package org.nooklin.files

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import org.nooklin.files.player.PipState
import org.nooklin.files.ui.FilesApp
import org.nooklin.files.ui.ShortcutTarget
import java.lang.ref.WeakReference

// FragmentActivity: BiometricPrompt (App Lock) needs it
class MainActivity : FragmentActivity() {
    // Checked again at every resume: the user grants All files access in the system settings and comes back
    private val storageGranted = mutableStateOf(false)
    // The launcher shortcut that started or returned to the app, until the app has gone there
    private val shortcut = mutableStateOf<ShortcutTarget?>(null)
    // A folder that another app asked to show, until the app has gone there
    private val folder = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppStrings.activity = WeakReference(this)
        enableEdgeToEdge()
        // Only a fresh start: after a recreation the old intent must not send the user there again
        if (savedInstanceState == null) {
            shortcut.value = ShortcutTarget.from(intent.getStringExtra(ShortcutTarget.EXTRA))
            folder.value = FolderIntents.folderOf(intent)
        }
        storageGranted.value = Environment.isExternalStorageManager()
        setContent {
            FilesApp(
                storageGranted.value,
                shortcut.value,
                onShortcutHandled = { shortcut.value = null },
                openFolder = folder.value,
                onFolderHandled = { folder.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ShortcutTarget.from(intent.getStringExtra(ShortcutTarget.EXTRA))?.let { shortcut.value = it }
        FolderIntents.folderOf(intent)?.let { folder.value = it }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipState.active.value = isInPictureInPictureMode
    }

    override fun onResume() {
        super.onResume()
        storageGranted.value = Environment.isExternalStorageManager()
    }
}
