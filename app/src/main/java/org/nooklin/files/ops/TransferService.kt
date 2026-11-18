package org.nooklin.files.ops

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.nooklin.files.FilesApplication
import org.nooklin.files.MainActivity
import org.nooklin.files.R
import org.nooklin.files.appString

/**
 * Only keeps the process alive and shows the progress notification while the OperationManager works. The work
 * itself runs in the OperationManager, so a closed screen does not stop it.
 */
class TransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = (application as FilesApplication).operations
        if (intent?.action == ACTION_CANCEL) {
            manager.cancelCurrent()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(manager.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        if (collectJob == null) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            collectJob = scope.launch {
                manager.state.collect { state ->
                    if (state == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(state: OpState?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, TransferService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, appString(R.string.common_cancel), cancel)
        if (state != null) {
            builder.setContentTitle(opTitle(state.type, state.itemCount))
            builder.setContentText(if (state.conflict != null) appString(R.string.op_waiting_for_answer) else state.current)
            if (state.total > 0 && !state.preparing) {
                builder.setProgress(100, (state.fraction * 100).toInt(), false)
            } else {
                builder.setProgress(0, 0, true)
            }
        } else {
            builder.setContentTitle(appString(R.string.app_name))
        }
        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "transfers"
        const val ACTION_CANCEL = "org.nooklin.files.CANCEL"
        private const val NOTIFICATION_ID = 1
    }
}
