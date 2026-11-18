package org.nooklin.files.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.nooklin.files.R
import java.io.File

/** Installs APK files with the Android package installer. Android asks the user to confirm. */
object ApkInstaller {
    /**
     * Writes the APK files into one install session, so the parts of a split app are installed together.
     * Throws when the files cannot be read or the session cannot be made. The result of the install itself
     * arrives later at InstallResultReceiver.
     */
    fun install(context: Context, apks: List<File>) {
        val installer = context.packageManager.packageInstaller
        val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        try {
            installer.openSession(id).use { session ->
                for (apk in apks) {
                    apk.inputStream().use { input ->
                        session.openWrite(apk.name, 0, apk.length()).use { out ->
                            input.copyTo(out, 64 * 1024)
                            session.fsync(out)
                        }
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
                // The installer fills in the result, so the intent must be mutable
                val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            try { installer.abandonSession(id) } catch (_: Exception) {}
            throw e
        }
    }
}

/** Receives what the package installer reports: ask the user, done, or failed. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirm)
                    } catch (e: Exception) {
                        toast(context, context.getString(R.string.restore_failed, e.message.orEmpty()))
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> toast(context, context.getString(R.string.restore_done))
            // The user said no in the Android screen: nothing to report
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> toast(context, context.getString(R.string.restore_failed, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()))
        }
    }

    private fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show() }
    }
}
