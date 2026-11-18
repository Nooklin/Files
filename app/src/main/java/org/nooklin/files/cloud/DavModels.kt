package org.nooklin.files.cloud

/** A file or folder on the server. path starts with a slash and is relative to the Nextcloud files root, "" is the root. */
data class RemoteItem(val name: String, val path: String, val isDir: Boolean, val size: Long, val modified: Long)

/** An item in the Nextcloud trash. trashPath is relative to the user's trashbin, for example trash/photo.jpg.d1698000000 */
data class RemoteTrashItem(
    val trashPath: String,
    val name: String,
    val originalLocation: String,
    val deletedAt: Long,
    val size: Long,
    val isDir: Boolean
)

/** What went wrong in a request to the server, code is the HTTP status. */
class DavException(val code: Int, message: String) : Exception(message)

class DavAccount(val server: String, val user: String, val password: String)
