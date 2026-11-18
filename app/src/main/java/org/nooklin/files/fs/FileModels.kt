package org.nooklin.files.fs

import java.io.File

data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long
) {
    val extension: String get() = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
}

enum class SortKey { NAME, TYPE, SIZE, DATE }

data class SortSpec(val key: SortKey = SortKey.NAME, val ascending: Boolean = true)

// Plain java.io only, so the whole fs package can be tested on a normal JVM
object FileLister {
    /** Null when the folder cannot be read (for example another app's folder under Android/data). */
    fun list(dir: File, showHidden: Boolean, sort: SortSpec): List<FileItem>? {
        val children = dir.listFiles() ?: return null
        val items = ArrayList<FileItem>(children.size)
        for (child in children) {
            if (!showHidden && child.name.startsWith(".")) continue
            val isDir = child.isDirectory
            items.add(FileItem(child.name, child.path, isDir, if (isDir) 0L else child.length(), child.lastModified()))
        }
        return items.sortedWith(comparatorFor(sort))
    }

    fun comparatorFor(sort: SortSpec): Comparator<FileItem> {
        val byName = compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        val key: Comparator<FileItem> = when (sort.key) {
            SortKey.NAME -> byName
            SortKey.TYPE -> compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.extension }.then(byName)
            SortKey.SIZE -> compareBy<FileItem> { it.size }.then(byName)
            SortKey.DATE -> compareBy<FileItem> { it.modified }.then(byName)
        }
        val directed = if (sort.ascending) key else key.reversed()
        // Folders always come first, whatever the sort
        return compareBy<FileItem> { if (it.isDir) 0 else 1 }.then(directed)
    }
}
