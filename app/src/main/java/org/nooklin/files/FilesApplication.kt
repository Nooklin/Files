package org.nooklin.files

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.nooklin.files.data.AppPreferences
import org.nooklin.files.fs.TrashStore
import org.nooklin.files.ops.OperationManager
import org.nooklin.files.ops.TransferService
import java.io.File

class FilesApplication : Application() {
    val prefs by lazy { AppPreferences(this) }

    // Next to the app's own files on the same storage, so moving into it is a rename. Items are lost on uninstall.
    val trashStore by lazy { TrashStore(File(getExternalFilesDir(null) ?: filesDir, ".trash")) }
    val operations by lazy { OperationManager(this, trashStore) }

    override fun onCreate() {
        super.onCreate()
        AppStrings.app = this
        val channel = NotificationChannel(
            TransferService.CHANNEL_ID,
            getString(R.string.notification_channel_transfers),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        // Retention and size limits of the trash are applied at every start
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { trashStore.cleanup() }
    }
}
