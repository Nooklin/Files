package org.nooklin.files.fs

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** What a file counts as in the storage overview. */
enum class StorageCategory { IMAGES, VIDEOS, AUDIO, DOCUMENTS, APPS, ARCHIVES, OTHER_FILES }

class CategoryTotal(var count: Int = 0, var bytes: Long = 0L)

object StorageUsage {
    private val apps = setOf("apk", "xapk", "apks", "apkm")
    private val archives = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst")

    fun categoryOf(extension: String): StorageCategory {
        val ext = extension.lowercase()
        return when (MediaKinds.groupOf(ext)) {
            MediaGroup.IMAGES -> StorageCategory.IMAGES
            MediaGroup.VIDEOS -> StorageCategory.VIDEOS
            MediaGroup.MUSIC -> StorageCategory.AUDIO
            MediaGroup.DOCUMENTS -> StorageCategory.DOCUMENTS
            null -> when (ext) {
                in apps -> StorageCategory.APPS
                in archives -> StorageCategory.ARCHIVES
                else -> StorageCategory.OTHER_FILES
            }
        }
    }

    /** What a scan finds: totals per category, the largest files and the largest folders directly below the root. */
    class ScanResult(
        val totals: Map<StorageCategory, CategoryTotal>,
        val largestFiles: List<Pair<File, Long>>,
        val largestFolders: List<Pair<File, Long>>
    )

    /** Adds up the size and number of the files below root per category. Links are not followed. */
    suspend fun scan(root: File): Map<StorageCategory, CategoryTotal> = scanFull(root, 0).totals

    /** Like scan, and keeps the biggest top files and the biggest top folders of the first level. */
    suspend fun scanFull(root: File, top: Int): ScanResult {
        val totals = StorageCategory.entries.associateWith { CategoryTotal() }
        val biggest = ArrayList<Pair<File, Long>>()
        val folders = ArrayList<Pair<File, Long>>()
        val topLevel = root.listFiles().orEmpty()
        for (entry in topLevel) {
            currentCoroutineContext().ensureActive()
            if (FileEngine.isLink(entry)) continue
            var folderBytes = 0L
            val queue = java.util.ArrayDeque<File>()
            queue.add(entry)
            while (queue.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val item = queue.pollFirst() ?: break
                if (item.isDirectory) {
                    val children = item.listFiles() ?: continue
                    for (child in children) if (!FileEngine.isLink(child)) queue.add(child)
                } else {
                    val size = item.length()
                    val total = totals.getValue(categoryOf(item.extension))
                    total.count++
                    total.bytes += size
                    folderBytes += size
                    if (top > 0) {
                        if (biggest.size < top) {
                            biggest.add(item to size)
                        } else {
                            val smallest = biggest.minByOrNull { it.second }!!
                            if (size > smallest.second) {
                                biggest.remove(smallest)
                                biggest.add(item to size)
                            }
                        }
                    }
                }
            }
            if (top > 0 && entry.isDirectory && folderBytes > 0) folders.add(entry to folderBytes)
        }
        return ScanResult(
            totals,
            biggest.sortedByDescending { it.second },
            folders.sortedByDescending { it.second }.take(top)
        )
    }
}
