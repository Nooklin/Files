package org.nooklin.files

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/** Works out which folder of the phone another app means with a "show this folder" intent. */
object FolderIntents {
    /** The folder to show, or null when the intent is not about an existing folder of the phone. */
    fun folderOf(intent: Intent): String? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val path = pathOf(uri) ?: return null
        val dir = File(path)
        return if (dir.isDirectory) dir.path else null
    }

    private fun pathOf(uri: Uri): String? = when (uri.scheme) {
        "file" -> uri.path
        "content" -> if (uri.authority == "com.android.externalstorage.documents") documentPath(uri) else null
        else -> null
    }

    // The storage provider names a place "volume:relative/path", "primary" is the internal storage
    private fun documentPath(uri: Uri): String? {
        val id = try {
            if (DocumentsContract.isTreeUri(uri)) DocumentsContract.getTreeDocumentId(uri) else DocumentsContract.getDocumentId(uri)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val volume = id.substringBefore(':')
        val relative = id.substringAfter(':', "")
        if (relative.split('/').any { it == ".." }) return null
        val base = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        return if (relative.isEmpty()) base else "$base/$relative"
    }
}
