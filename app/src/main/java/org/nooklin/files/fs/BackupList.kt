package org.nooklin.files.fs

import java.io.File

/** A back-up made by the App Manager: one APK file, or a folder with the parts of a split app. */
data class BackupEntry(val name: String, val path: String, val apks: List<File>, val size: Long, val modified: Long)

object BackupList {
    /** What is in the back-up folder, newest first. Half-written files and folders without an APK are left out. */
    fun list(dir: File): List<BackupEntry> {
        val children = dir.listFiles() ?: return emptyList()
        val entries = ArrayList<BackupEntry>()
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isFile && child.extension.equals("apk", ignoreCase = true)) {
                entries.add(BackupEntry(child.name, child.path, listOf(child), child.length(), child.lastModified()))
            } else if (child.isDirectory) {
                val apks = child.listFiles { f -> f.isFile && f.extension.equals("apk", ignoreCase = true) }?.sortedBy { it.name }.orEmpty()
                if (apks.isNotEmpty()) entries.add(BackupEntry(child.name, child.path, apks, apks.sumOf { it.length() }, child.lastModified()))
            }
        }
        return entries.sortedByDescending { it.modified }
    }
}
