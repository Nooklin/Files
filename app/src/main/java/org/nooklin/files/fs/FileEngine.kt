package org.nooklin.files.fs

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.nio.file.Files

enum class Resolution { OVERWRITE, RENAME, SKIP }

data class Conflict(
    val sourcePath: String,
    val targetPath: String,
    val sourceIsDir: Boolean,
    val targetIsDir: Boolean
)

enum class ErrorKind { INTO_ITSELF, SYMLINK, NOT_FOUND, TOO_LARGE_FOR_TRASH, UNSAFE_ENTRY, WRONG_PASSWORD, IO }

data class OpError(val path: String, val kind: ErrorKind, val detail: String = "")

/** What the engine needs from its caller: progress and an answer when a target already exists. */
interface OpContext {
    fun addDone(units: Long)
    fun setCurrent(name: String)
    suspend fun resolve(conflict: Conflict): Resolution
}

/**
 * Copy, move and delete on plain java.io files. Cancelling the coroutine stops the work between two buffers.
 * A copied file is written next to its target under a temporary name and renamed when complete, so a cancel
 * or a crash never leaves a half file under the real name.
 */
object FileEngine {
    private const val BUFFER_SIZE = 256 * 1024

    fun isLink(file: File): Boolean = try {
        Files.isSymbolicLink(file.toPath())
    } catch (e: Exception) {
        false
    }

    /** A name that is free in dir: "name (1).ext", "name (2).ext" and so on. */
    fun uniqueFile(dir: File, name: String, isDir: Boolean): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val base = if (!isDir && dot > 0) name.substring(0, dot) else name
        val ext = if (!isDir && dot > 0) name.substring(dot) else ""
        var counter = 1
        while (true) {
            val candidate = File(dir, "$base ($counter)$ext")
            if (!candidate.exists()) return candidate
            counter++
        }
    }

    fun tempFileFor(dir: File): File = File(dir, ".nooklin-" + System.nanoTime().toString(36) + ".part")

    /** Size in bytes of a file or of everything inside a folder. Not cancellable, for short checks. */
    fun sizeOfPlain(file: File): Long {
        if (!file.isDirectory || isLink(file)) return file.length()
        var total = 0L
        file.listFiles()?.forEach { total += sizeOfPlain(it) }
        return total
    }

    suspend fun sizeOf(file: File): Long {
        currentCoroutineContext().ensureActive()
        if (!file.isDirectory || isLink(file)) return file.length()
        var total = 0L
        file.listFiles()?.forEach { total += sizeOf(it) }
        return total
    }

    suspend fun totalSize(files: List<File>): Long {
        var total = 0L
        for (file in files) total += sizeOf(file)
        return total
    }

    suspend fun countEntries(file: File): Long {
        currentCoroutineContext().ensureActive()
        if (!file.isDirectory || isLink(file)) return 1L
        var total = 1L
        file.listFiles()?.forEach { total += countEntries(it) }
        return total
    }

    /** Deletes a file or a whole folder without following links. True when nothing is left. */
    fun deleteQuietly(file: File): Boolean {
        if (file.isDirectory && !isLink(file)) {
            file.listFiles()?.forEach { deleteQuietly(it) }
        }
        return file.delete() || !file.exists()
    }

    /**
     * Copies or moves the sources into destDir. Folders that exist on both sides are merged, a file that exists
     * on both sides goes through ctx.resolve. Returns the problems, the rest of the work carries on.
     */
    suspend fun transfer(sources: List<File>, destDir: File, move: Boolean, ctx: OpContext): List<OpError> {
        val errors = ArrayList<OpError>()
        val destCanonical = destDir.canonicalFile
        for (source in sources) {
            currentCoroutineContext().ensureActive()
            try {
                if (!source.exists()) {
                    errors.add(OpError(source.path, ErrorKind.NOT_FOUND))
                    continue
                }
                val sourceCanonical = source.canonicalFile
                if (source.isDirectory &&
                    (destCanonical == sourceCanonical || destCanonical.path.startsWith(sourceCanonical.path + File.separator))
                ) {
                    errors.add(OpError(source.path, ErrorKind.INTO_ITSELF))
                    continue
                }
                if (move && sourceCanonical.parentFile == destCanonical) {
                    // Already there, nothing to move
                    ctx.addDone(sizeOf(source))
                    continue
                }
                transferOne(source, destDir, move, ctx, errors)
            } catch (e: IOException) {
                errors.add(OpError(source.path, ErrorKind.IO, e.message.orEmpty()))
            }
        }
        return errors
    }

    private suspend fun transferOne(src: File, destDir: File, move: Boolean, ctx: OpContext, errors: MutableList<OpError>) {
        currentCoroutineContext().ensureActive()
        ctx.setCurrent(src.name)
        if (src.isDirectory && isLink(src)) {
            errors.add(OpError(src.path, ErrorKind.SYMLINK))
            return
        }
        var target = File(destDir, src.name)
        var replacing = false
        if (target.exists()) {
            if (src.isDirectory && target.isDirectory) {
                mergeDir(src, target, move, ctx, errors)
                return
            }
            when (ctx.resolve(Conflict(src.path, target.path, src.isDirectory, target.isDirectory))) {
                Resolution.SKIP -> {
                    ctx.addDone(sizeOf(src))
                    return
                }
                Resolution.RENAME -> target = uniqueFile(destDir, src.name, src.isDirectory)
                Resolution.OVERWRITE -> replacing = true
            }
        }
        if (src.isDirectory) {
            // A file is in the way of a folder: only reached when the user chose overwrite
            if (replacing) target.delete()
            if (move) {
                val size = sizeOf(src)
                if (!target.exists() && src.renameTo(target)) {
                    ctx.addDone(size)
                    return
                }
            }
            if (!target.mkdirs() && !target.isDirectory) throw IOException("Cannot create " + target.name)
            copyChildren(src, target, move, ctx, errors)
            if (move) src.delete()
        } else {
            // A folder is in the way of a file: only reached when the user chose overwrite
            if (replacing && target.isDirectory) deleteQuietly(target)
            if (move) {
                val length = src.length()
                if (src.renameTo(target)) {
                    ctx.addDone(length)
                    return
                }
            }
            copyFileData(src, target, ctx)
            if (move && !src.delete()) {
                throw IOException("Copied, but the original could not be removed")
            }
        }
    }

    private suspend fun mergeDir(src: File, target: File, move: Boolean, ctx: OpContext, errors: MutableList<OpError>) {
        copyChildren(src, target, move, ctx, errors)
        // Only an empty folder is removed, so anything that was skipped stays where it was
        if (move) src.delete()
    }

    private suspend fun copyChildren(src: File, target: File, move: Boolean, ctx: OpContext, errors: MutableList<OpError>) {
        val children = src.listFiles() ?: return
        for (child in children) {
            currentCoroutineContext().ensureActive()
            try {
                transferOne(child, target, move, ctx, errors)
            } catch (e: IOException) {
                errors.add(OpError(child.path, ErrorKind.IO, e.message.orEmpty()))
            }
        }
    }

    private suspend fun copyFileData(src: File, target: File, ctx: OpContext) {
        val part = tempFileFor(target.parentFile ?: throw IOException("No parent folder"))
        var finished = false
        try {
            src.inputStream().use { input ->
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
            part.setLastModified(src.lastModified())
            if (target.exists() && !target.delete()) throw IOException("Cannot replace " + target.name)
            if (!part.renameTo(target)) throw IOException("Cannot finish " + target.name)
            finished = true
        } finally {
            if (!finished) part.delete()
        }
    }

    /** Permanent delete, one unit of progress per file or folder. */
    suspend fun deleteTree(file: File, ctx: OpContext, errors: MutableList<OpError>) {
        currentCoroutineContext().ensureActive()
        if (file.isDirectory && !isLink(file)) {
            file.listFiles()?.forEach { deleteTree(it, ctx, errors) }
        }
        if (!file.delete() && file.exists()) {
            errors.add(OpError(file.path, ErrorKind.IO, "Delete failed"))
        }
        ctx.addDone(1)
    }
}
