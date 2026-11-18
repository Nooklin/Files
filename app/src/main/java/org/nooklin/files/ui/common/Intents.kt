package org.nooklin.files.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import org.nooklin.files.R
import java.io.File

private fun uriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, context.packageName + ".files", file)

private fun mimeFor(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

/** Hands the file to the app Android picks for its type. */
fun openFile(context: Context, path: String) {
    val file = File(path)
    try {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, file), mimeFor(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.msg_no_app_to_open, Toast.LENGTH_SHORT).show()
    } catch (e: IllegalArgumentException) {
        Toast.makeText(context, R.string.msg_no_app_to_open, Toast.LENGTH_SHORT).show()
    }
}

/** Shares files (not folders) with another app. The file is shared as it is, nothing is changed in it. */
fun shareFiles(context: Context, paths: List<String>) {
    if (paths.isEmpty()) return
    try {
        val files = paths.map { File(it) }
        val uris = ArrayList(files.map { uriFor(context, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(mimeFor(files[0]))
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
        }
        val clip = ClipData.newRawUri(null, uris[0])
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.msg_no_app_to_share, Toast.LENGTH_SHORT).show()
    } catch (e: IllegalArgumentException) {
        Toast.makeText(context, R.string.msg_no_app_to_share, Toast.LENGTH_SHORT).show()
    }
}

/** Opens a web address in the browser. Nothing happens when no browser is installed. */
fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.msg_no_app_to_open, Toast.LENGTH_SHORT).show()
    }
}
