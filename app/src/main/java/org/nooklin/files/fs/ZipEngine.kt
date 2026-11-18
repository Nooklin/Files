package org.nooklin.files.fs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import net.lingala.zip4j.ZipFile as ZipFile4j
import net.lingala.zip4j.io.outputstream.ZipOutputStream as ZipOutputStream4j

/**
 * Zip only. Zips without a password use java.util.zip. Zips with a password (AES 256 when created here, AES and the
 * older zip encryption when extracting) use zip4j, because java.util.zip cannot read or write those.
 */
object ZipEngine {
    private const val BUFFER_SIZE = 64 * 1024

    private fun openZip(file: File): ZipFile = try {
        ZipFile(file)
    } catch (e: IllegalArgumentException) {
        // Names that are not valid UTF-8: old zips use the DOS code page
        ZipFile(file, Charset.forName("IBM437"))
    }

    /** True when at least one entry of the zip needs a password. */
    fun isEncrypted(zip: File): Boolean = try {
        ZipFile4j(zip).use { zf -> zf.fileHeaders.any { it.isEncrypted } }
    } catch (e: Exception) {
        false
    }

    /** The size of everything in a zip with a password. java.util.zip cannot even open those. */
    suspend fun totalUncompressedEncrypted(zip: File): Long {
        var total = 0L
        ZipFile4j(zip).use { zf ->
            for (header in zf.fileHeaders) {
                currentCoroutineContext().ensureActive()
                if (header.uncompressedSize > 0) total += header.uncompressedSize
            }
        }
        return total
    }

    suspend fun totalUncompressed(zip: File): Long {
        var total = 0L
        openZip(zip).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val size = entries.nextElement().size
                if (size > 0) total += size
            }
        }
        return total
    }

    /** Extracts into a new folder named after the zip, next to it. Returns that folder. */
    suspend fun extract(zip: File, destParent: File, ctx: OpContext, errors: MutableList<OpError>): File {
        val folder = FileEngine.uniqueFile(destParent, zip.nameWithoutExtension, true)
        if (!folder.mkdirs()) throw IOException("Cannot create " + folder.name)
        val folderCanonical = folder.canonicalPath
        try {
            openZip(zip).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    currentCoroutineContext().ensureActive()
                    val entry = entries.nextElement()
                    val entryName = entry.name.replace('\\', '/')
                    val out = File(folder, entryName)
                    val outCanonical = out.canonicalPath
                    if (outCanonical != folderCanonical && !outCanonical.startsWith(folderCanonical + File.separator)) {
                        // A name like ../../x would write outside the new folder
                        errors.add(OpError(entry.name, ErrorKind.UNSAFE_ENTRY))
                        continue
                    }
                    ctx.setCurrent(entryName.trimEnd('/').substringAfterLast('/'))
                    try {
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            extractEntry(zf, entry, out, ctx)
                        }
                    } catch (e: IOException) {
                        errors.add(OpError(entry.name, ErrorKind.IO, e.message.orEmpty()))
                    }
                }
            }
        } catch (e: CancellationException) {
            FileEngine.deleteQuietly(folder)
            throw e
        }
        return folder
    }

    /** Extracts a zip with a password into a new folder next to it. A wrong password removes the folder again. */
    suspend fun extractEncrypted(zip: File, destParent: File, password: CharArray, ctx: OpContext, errors: MutableList<OpError>): File {
        val folder = FileEngine.uniqueFile(destParent, zip.nameWithoutExtension, true)
        if (!folder.mkdirs()) throw IOException("Cannot create " + folder.name)
        val folderCanonical = folder.canonicalPath
        try {
            ZipFile4j(zip, password).use { zf ->
                for (header in zf.fileHeaders) {
                    currentCoroutineContext().ensureActive()
                    val entryName = header.fileName.replace('\\', '/')
                    val out = File(folder, entryName)
                    val outCanonical = out.canonicalPath
                    if (outCanonical != folderCanonical && !outCanonical.startsWith(folderCanonical + File.separator)) {
                        errors.add(OpError(header.fileName, ErrorKind.UNSAFE_ENTRY))
                        continue
                    }
                    ctx.setCurrent(entryName.trimEnd('/').substringAfterLast('/'))
                    try {
                        if (header.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            extractEncryptedEntry(zf, header, out, ctx)
                        }
                    } catch (e: ZipException) {
                        if (e.type == ZipException.Type.WRONG_PASSWORD) {
                            FileEngine.deleteQuietly(folder)
                            errors.add(OpError(zip.path, ErrorKind.WRONG_PASSWORD))
                            return folder
                        }
                        errors.add(OpError(header.fileName, ErrorKind.IO, e.message.orEmpty()))
                    } catch (e: IOException) {
                        errors.add(OpError(header.fileName, ErrorKind.IO, e.message.orEmpty()))
                    }
                }
            }
        } catch (e: CancellationException) {
            FileEngine.deleteQuietly(folder)
            throw e
        }
        return folder
    }

    private suspend fun extractEncryptedEntry(zf: ZipFile4j, header: FileHeader, out: File, ctx: OpContext) {
        val part = FileEngine.tempFileFor(out.parentFile ?: throw IOException("No parent folder"))
        var finished = false
        try {
            zf.getInputStream(header).use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        ctx.addDone(read.toLong())
                    }
                }
            }
            if (header.lastModifiedTimeEpoch > 0) part.setLastModified(header.lastModifiedTimeEpoch)
            if (out.exists() && !out.delete()) throw IOException("Cannot replace " + out.name)
            if (!part.renameTo(out)) throw IOException("Cannot finish " + out.name)
            finished = true
        } finally {
            if (!finished) part.delete()
        }
    }

    private suspend fun extractEntry(zf: ZipFile, entry: ZipEntry, out: File, ctx: OpContext) {
        val part = FileEngine.tempFileFor(out.parentFile ?: throw IOException("No parent folder"))
        var finished = false
        try {
            zf.getInputStream(entry).use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        ctx.addDone(read.toLong())
                    }
                }
            }
            if (entry.time > 0) part.setLastModified(entry.time)
            if (out.exists() && !out.delete()) throw IOException("Cannot replace " + out.name)
            if (!part.renameTo(out)) throw IOException("Cannot finish " + out.name)
            finished = true
        } finally {
            if (!finished) part.delete()
        }
    }

    /** Packs the sources into one zip in destDir. Returns the zip, or null when the work was cancelled. */
    suspend fun compress(sources: List<File>, destDir: File, ctx: OpContext, errors: MutableList<OpError>): File? {
        val baseName = when {
            sources.size > 1 -> "Archive"
            sources[0].isDirectory -> sources[0].name
            else -> sources[0].nameWithoutExtension.ifEmpty { sources[0].name }
        }
        val target = FileEngine.uniqueFile(destDir, "$baseName.zip", false)
        val part = FileEngine.tempFileFor(destDir)
        val partCanonical = part.canonicalPath
        var finished = false
        try {
            ZipOutputStream(BufferedOutputStream(part.outputStream())).use { zos ->
                for (source in sources) {
                    currentCoroutineContext().ensureActive()
                    addEntry(zos, source, source.name, partCanonical, ctx, errors)
                }
            }
            if (!part.renameTo(target)) throw IOException("Cannot finish " + target.name)
            finished = true
        } finally {
            if (!finished) part.delete()
        }
        return target
    }

    /** Packs the sources into one zip with AES 256 encryption. Returns the zip. */
    suspend fun compressEncrypted(sources: List<File>, destDir: File, password: CharArray, ctx: OpContext, errors: MutableList<OpError>): File {
        val baseName = when {
            sources.size > 1 -> "Archive"
            sources[0].isDirectory -> sources[0].name
            else -> sources[0].nameWithoutExtension.ifEmpty { sources[0].name }
        }
        val target = FileEngine.uniqueFile(destDir, "$baseName.zip", false)
        val part = FileEngine.tempFileFor(destDir)
        val partCanonical = part.canonicalPath
        var finished = false
        try {
            ZipOutputStream4j(BufferedOutputStream(part.outputStream()), password).use { zos ->
                for (source in sources) {
                    currentCoroutineContext().ensureActive()
                    addEncryptedEntry(zos, source, source.name, partCanonical, ctx, errors)
                }
            }
            if (!part.renameTo(target)) throw IOException("Cannot finish " + target.name)
            finished = true
        } finally {
            if (!finished) part.delete()
        }
        return target
    }

    private suspend fun addEncryptedEntry(
        zos: ZipOutputStream4j,
        file: File,
        entryName: String,
        partCanonical: String,
        ctx: OpContext,
        errors: MutableList<OpError>
    ) {
        currentCoroutineContext().ensureActive()
        if (file.canonicalPath == partCanonical) return
        if (FileEngine.isLink(file) && file.isDirectory) {
            errors.add(OpError(file.path, ErrorKind.SYMLINK))
            return
        }
        ctx.setCurrent(file.name)
        val parameters = ZipParameters()
        parameters.lastModifiedFileTime = file.lastModified()
        if (file.isDirectory) {
            // A folder entry has no data, so there is nothing to encrypt
            parameters.fileNameInZip = "$entryName/"
            parameters.isEncryptFiles = false
            parameters.compressionMethod = CompressionMethod.STORE
            zos.putNextEntry(parameters)
            zos.closeEntry()
            file.listFiles()?.forEach { addEncryptedEntry(zos, it, "$entryName/${it.name}", partCanonical, ctx, errors) }
        } else {
            parameters.fileNameInZip = entryName
            parameters.isEncryptFiles = true
            parameters.encryptionMethod = EncryptionMethod.AES
            parameters.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            zos.putNextEntry(parameters)
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    zos.write(buffer, 0, read)
                    ctx.addDone(read.toLong())
                }
            }
            zos.closeEntry()
        }
    }

    private suspend fun addEntry(
        zos: ZipOutputStream,
        file: File,
        entryName: String,
        partCanonical: String,
        ctx: OpContext,
        errors: MutableList<OpError>
    ) {
        currentCoroutineContext().ensureActive()
        if (file.canonicalPath == partCanonical) return
        if (FileEngine.isLink(file) && file.isDirectory) {
            errors.add(OpError(file.path, ErrorKind.SYMLINK))
            return
        }
        ctx.setCurrent(file.name)
        // A file that cannot be read stops the whole zip (the entry would be half written), the caller reports it
        if (file.isDirectory) {
            val dirEntry = ZipEntry("$entryName/")
            dirEntry.time = file.lastModified()
            zos.putNextEntry(dirEntry)
            zos.closeEntry()
            file.listFiles()?.forEach { addEntry(zos, it, "$entryName/${it.name}", partCanonical, ctx, errors) }
        } else {
            val entry = ZipEntry(entryName)
            entry.time = file.lastModified()
            zos.putNextEntry(entry)
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    zos.write(buffer, 0, read)
                    ctx.addDone(read.toLong())
                }
            }
            zos.closeEntry()
        }
    }
}
