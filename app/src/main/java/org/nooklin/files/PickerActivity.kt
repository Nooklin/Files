package org.nooklin.files

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import org.nooklin.files.ui.FilesApp
import org.nooklin.files.ui.PickMode
import java.io.File
import java.lang.ref.WeakReference

/**
 * Opened by another app that asks for a file (GET_CONTENT, OPEN_DOCUMENT). It shows the normal app; tapping a
 * file hands that file back to the other app for reading. One file at a time, whatever type the other app asked for.
 */
class PickerActivity : FragmentActivity() {
    private val storageGranted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppStrings.activity = WeakReference(this)
        enableEdgeToEdge()
        storageGranted.value = Environment.isExternalStorageManager()
        setContent {
            FilesApp(
                storageGranted.value,
                shortcut = null,
                onShortcutHandled = {},
                pick = PickMode(onPick = ::choose, onCancel = { setResult(RESULT_CANCELED); finish() })
            )
        }
    }

    override fun onResume() {
        super.onResume()
        storageGranted.value = Environment.isExternalStorageManager()
    }

    private fun choose(path: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", File(path))
        val result = Intent().setData(uri)
        result.clipData = ClipData.newRawUri(null, uri)
        result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setResult(RESULT_OK, result)
        finish()
    }
}
