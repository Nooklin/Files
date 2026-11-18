package org.nooklin.files.fs

import java.io.File
import kotlinx.coroutines.ensureActive

/** What a file is for the built-in viewers. OTHER opens in the app Android picks (or the editor for text). */
enum class MediaKind { IMAGE, VIDEO, AUDIO, PDF, DOCX, EPUB, OTHER }

/** The four lists of the media screen. */
enum class MediaGroup { VIDEOS, IMAGES, MUSIC, DOCUMENTS }

object MediaKinds {
    // svg is not here: Android cannot decode it, it opens in another app
    private val images = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp")
    private val videos = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v")
    private val audio = setOf("mp3", "flac", "wav", "m4a", "ogg", "opus", "aac")
    // Documents that are listed in the media screen. Only pdf, docx and epub open in the app.
    private val otherDocuments = setOf("doc", "odt", "rtf", "txt", "md", "xls", "xlsx", "ods", "ppt", "pptx", "odp", "csv")

    fun of(extension: String): MediaKind = when (extension.lowercase()) {
        in images -> MediaKind.IMAGE
        in videos -> MediaKind.VIDEO
        in audio -> MediaKind.AUDIO
        "pdf" -> MediaKind.PDF
        "docx" -> MediaKind.DOCX
        "epub" -> MediaKind.EPUB
        else -> MediaKind.OTHER
    }

    fun of(file: File): MediaKind = of(file.extension)

    /** Which list a file belongs to, or null when it is in none. */
    fun groupOf(extension: String): MediaGroup? {
        val ext = extension.lowercase()
        return when {
            ext in videos -> MediaGroup.VIDEOS
            ext in images -> MediaGroup.IMAGES
            ext in audio -> MediaGroup.MUSIC
            ext == "pdf" || ext == "docx" || ext == "epub" || ext in otherDocuments -> MediaGroup.DOCUMENTS
            else -> null
        }
    }

    /** True for what the built-in viewers open. */
    fun isBuiltIn(file: File): Boolean = of(file) != MediaKind.OTHER
}

/** A file found by the media scan. */
data class MediaFile(val name: String, val path: String, val extension: String, val size: Long, val modified: Long, val group: MediaGroup)

object MediaScan {
    /**
     * All files below the roots that belong to one of the four lists. Folders named in skip are not entered,
     * links are not followed. The result is in no particular order.
     */
    suspend fun scan(roots: List<File>, showHidden: Boolean, skip: Set<File> = emptySet()): List<MediaFile> {
        val found = ArrayList<MediaFile>()
        val queue = java.util.ArrayDeque<File>()
        queue.addAll(roots)
        while (queue.isNotEmpty()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val dir = queue.pollFirst() ?: break
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (!showHidden && child.name.startsWith(".")) continue
                if (FileEngine.isLink(child)) continue
                if (child.isDirectory) {
                    if (child !in skip) queue.add(child)
                } else {
                    val group = MediaKinds.groupOf(child.extension) ?: continue
                    found.add(MediaFile(child.name, child.path, child.extension.lowercase(), child.length(), child.lastModified(), group))
                }
            }
        }
        return found
    }
}
