package org.nooklin.files.fs

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest

/** Details of a file or folder for the properties dialog. */
data class FolderTotals(val files: Long, val folders: Long, val bytes: Long)

object FileInfo {
    /** Counts everything inside a folder. Symbolic links are not followed. Cancellable. */
    suspend fun folderTotals(dir: File): FolderTotals {
        var files = 0L
        var folders = 0L
        var bytes = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (FileEngine.isLink(child)) {
                    files++
                } else if (child.isDirectory) {
                    folders++
                    stack.add(child)
                } else {
                    files++
                    bytes += child.length()
                }
            }
        }
        return FolderTotals(files, folders, bytes)
    }

    /** Hex checksum of a file. algorithm is "SHA-256" or "MD5". Cancellable. */
    suspend fun checksum(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(256 * 1024)
        file.inputStream().use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
