package org.nooklin.files.fs

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.ArrayDeque

object FileSearch {
    const val MAX_RESULTS = 2000

    /**
     * Searches names, breadth first so shallow results come first. onMatch returns false to stop.
     * Folders reached through a link are not entered.
     */
    suspend fun search(root: File, query: String, showHidden: Boolean, onMatch: (FileItem) -> Boolean) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = queue.pollFirst() ?: break
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (!showHidden && child.name.startsWith(".")) continue
                val isDir = child.isDirectory
                if (child.name.lowercase().contains(needle)) {
                    val item = FileItem(child.name, child.path, isDir, if (isDir) 0L else child.length(), child.lastModified())
                    if (!onMatch(item)) return
                }
                if (isDir && !FileEngine.isLink(child)) queue.add(child)
            }
        }
    }
}
