package org.nooklin.files.fs

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.ArrayDeque
import java.util.PriorityQueue

object RecentFiles {
    const val LIMIT = 100

    /**
     * The files with the newest modified date below root, newest first. Folders are not listed, links are not
     * followed, and the folders named in skip (the root level Android folder, which other apps keep private) are not entered.
     */
    suspend fun scan(root: File, showHidden: Boolean, skip: Set<File> = emptySet(), limit: Int = LIMIT): List<FileItem> {
        // Min-heap: the oldest of the current best files sits on top and is replaced by anything newer
        val best = PriorityQueue<FileItem>(limit + 1, compareBy { it.modified })
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = queue.pollFirst() ?: break
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (!showHidden && child.name.startsWith(".")) continue
                if (FileEngine.isLink(child)) continue
                if (child.isDirectory) {
                    if (child !in skip) queue.add(child)
                } else {
                    val modified = child.lastModified()
                    if (best.size < limit || modified > best.peek()!!.modified) {
                        best.add(FileItem(child.name, child.path, false, child.length(), modified))
                        if (best.size > limit) best.poll()
                    }
                }
            }
        }
        return best.sortedByDescending { it.modified }
    }
}
