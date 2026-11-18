package org.nooklin.files.ui.apps

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.nooklin.files.fs.AppEntry

/** The total size of an app (APK, data and cache) comes from Android's storage statistics, which need Usage access. */
object AppStorageStats {
    fun usageAccessGranted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Asks for the total of every app and reports the numbers found so far every 25 apps. An app that Android
     * cannot report on is left out of the result, it keeps showing its APK size.
     */
    suspend fun totals(context: Context, apps: List<AppEntry>, onProgress: (Map<String, Long>) -> Unit) {
        val manager = context.getSystemService(StorageStatsManager::class.java) ?: return
        val user = Process.myUserHandle()
        val found = HashMap<String, Long>()
        for ((i, app) in apps.withIndex()) {
            currentCoroutineContext().ensureActive()
            try {
                val stats = manager.queryStatsForPackage(app.storageUuid, app.packageName, user)
                found[app.packageName] = stats.appBytes + stats.dataBytes
            } catch (e: Exception) {
                // No access to this one
            }
            if ((i + 1) % 25 == 0) onProgress(HashMap(found))
        }
        onProgress(found)
    }
}
