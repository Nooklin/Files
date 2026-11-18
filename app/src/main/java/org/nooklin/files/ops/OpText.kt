package org.nooklin.files.ops

import org.nooklin.files.R
import org.nooklin.files.appString
import org.nooklin.files.cloud.DavException
import org.nooklin.files.cloud.ServerError
import org.nooklin.files.cloud.ServerException
import org.nooklin.files.fs.ErrorKind
import org.nooklin.files.fs.OpError
import java.io.File

fun opTitle(type: OpType, count: Int): String = when (type) {
    OpType.COPY -> appString(R.string.op_copying, count)
    OpType.MOVE -> appString(R.string.op_moving, count)
    OpType.TRASH -> appString(R.string.op_trashing, count)
    OpType.DELETE -> appString(R.string.op_deleting, count)
    OpType.RESTORE -> appString(R.string.op_restoring, count)
    OpType.EMPTY_TRASH -> appString(R.string.op_emptying_trash)
    OpType.DELETE_TRASH_ITEMS -> appString(R.string.op_deleting, count)
    OpType.EXTRACT -> appString(R.string.op_extracting)
    OpType.COMPRESS -> appString(R.string.op_compressing, count)
    OpType.UPLOAD -> appString(R.string.op_uploading, countLabel(count))
    OpType.DOWNLOAD -> appString(R.string.op_downloading, countLabel(count))
}

private fun countLabel(count: Int): String =
    if (count == 1) appString(R.string.items_one) else appString(R.string.items_many, count)

/** The text for something that went wrong while talking to the Nextcloud server. */
fun remoteErrorText(e: Exception): String =
    when (e) {
        is DavException -> when (e.code) {
            401 -> appString(R.string.nc_error_auth)
            404 -> appString(R.string.nc_error_not_found)
            507 -> appString(R.string.nc_error_full)
            else -> appString(R.string.nc_error_http, e.code)
        }

        is ServerException -> when (e.kind) {
            ServerError.LOGIN -> appString(R.string.server_error_login)
            ServerError.NOT_FOUND -> appString(R.string.nc_error_not_found)
            ServerError.SHARE -> appString(R.string.server_error_share)
            ServerError.DENIED -> appString(R.string.server_error_denied, e.message.orEmpty())
            ServerError.OTHER -> appString(R.string.nc_error_network, e.message.orEmpty())
        }

        else -> appString(R.string.nc_error_network, e.message.orEmpty())
    }

fun errorText(error: OpError): String {
    val name = if (error.path.isEmpty()) "" else File(error.path).name
    val reason = when (error.kind) {
        ErrorKind.INTO_ITSELF -> appString(R.string.error_into_itself)
        ErrorKind.SYMLINK -> appString(R.string.error_symlink)
        ErrorKind.NOT_FOUND -> appString(R.string.error_not_found)
        ErrorKind.TOO_LARGE_FOR_TRASH -> appString(R.string.error_too_large_for_trash)
        ErrorKind.UNSAFE_ENTRY -> appString(R.string.error_unsafe_entry)
        ErrorKind.WRONG_PASSWORD -> appString(R.string.error_wrong_password)
        ErrorKind.IO -> if (error.detail.isEmpty()) appString(R.string.error_io) else error.detail
    }
    return if (name.isEmpty()) reason else "$name: $reason"
}
