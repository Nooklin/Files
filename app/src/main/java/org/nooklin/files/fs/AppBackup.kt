package org.nooklin.files.fs

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File

/** One installed app. apkPaths is the base APK first, then the split APKs if there are any. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val versionName: String,
    val isSystem: Boolean,
    val apkPaths: List<String>,
    val storageUuid: java.util.UUID = android.os.storage.StorageManager.UUID_DEFAULT
) {
    val size: Long get() = apkPaths.sumOf { File(it).length() }
}

/** Reading the installed apps and copying their APK files. The APK of an installed app can be read by every app. */
object AppBackup {
    fun load(pm: PackageManager): List<AppEntry> =
        pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0)).mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            val base = app.sourceDir ?: return@mapNotNull null
            AppEntry(
                packageName = info.packageName,
                label = pm.getApplicationLabel(app).toString(),
                versionName = info.versionName.orEmpty(),
                isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                apkPaths = listOf(base) + app.splitSourceDirs.orEmpty().toList(),
                storageUuid = app.storageUuid
            )
        }.sortedBy { it.label.lowercase() }

    /** A name that can be used for a file or folder on the phone. */
    fun safeName(text: String): String =
        text.replace(Regex("[\\\\/:*?\"<>|\\u0000]"), "_").trim().trim('.').ifEmpty { "app" }

    /**
     * Copies the APK files into destDir. An app with one APK becomes one file (Name (version).apk), an app with split
     * APKs becomes a folder with all its parts. Nothing is overwritten, a taken name gets a number. Returns the
     * file or folder that was made.
     */
    fun backup(app: AppEntry, destDir: File): File {
        destDir.mkdirs()
        val base = safeName(app.label) + if (app.versionName.isNotEmpty()) " (" + safeName(app.versionName) + ")" else ""
        if (app.apkPaths.size == 1) {
            val target = unique(destDir, "$base.apk")
            copy(File(app.apkPaths[0]), target)
            return target
        }
        val folder = unique(destDir, base)
        folder.mkdirs()
        try {
            for (path in app.apkPaths) copy(File(path), File(folder, File(path).name))
        } catch (e: Exception) {
            folder.deleteRecursively()
            throw e
        }
        return folder
    }

    private fun copy(from: File, to: File) {
        val temp = File(to.parentFile, ".nooklin-" + to.name + ".part")
        try {
            from.inputStream().use { input -> temp.outputStream().use { output -> input.copyTo(output, 64 * 1024) } }
            if (!temp.renameTo(to)) throw java.io.IOException("Could not save " + to.name)
        } finally {
            temp.delete()
        }
    }

    private fun unique(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$stem ($n)$ext")
            n++
        }
        return candidate
    }
}
