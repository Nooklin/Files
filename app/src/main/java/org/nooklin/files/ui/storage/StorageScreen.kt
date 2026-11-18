package org.nooklin.files.ui.storage

import org.nooklin.files.ui.theme.AppText
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import org.nooklin.files.fs.CategoryTotal
import org.nooklin.files.fs.MediaGroup
import org.nooklin.files.fs.StorageCategory
import org.nooklin.files.fs.StorageUsage
import org.nooklin.files.ui.common.IconTile
import org.nooklin.files.ui.common.PermissionNotice
import org.nooklin.files.ui.settings.SettingsNote
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nooklin.files.R
import org.nooklin.files.ui.settings.SettingsGroup
import org.nooklin.files.ui.settings.SettingsPageScaffold
import java.io.File

/** One storage: total, free and what the system tells about it. fileSystem is empty when it could not be found. */
private data class StorageInfo(
    val title: String,
    val path: String,
    val removable: Boolean,
    val mounted: Boolean,
    val total: Long,
    val free: Long,
    val fileSystem: String,
    val uuid: String
) {
    val used: Long get() = total - free
}

private val CategoryColors = mapOf(
    StorageCategory.IMAGES to Color(0xFF34C759),
    StorageCategory.VIDEOS to Color(0xFFFF453A),
    StorageCategory.AUDIO to Color(0xFFAF52DE),
    StorageCategory.DOCUMENTS to Color(0xFF0A84FF),
    StorageCategory.APPS to Color(0xFF3DDC84),
    StorageCategory.ARCHIVES to Color(0xFFFF9F0A),
    StorageCategory.OTHER_FILES to Color(0xFF8E8E93)
)
private val SystemColor = Color(0xFF5E5CE6)

private fun categoryIcon(category: StorageCategory): ImageVector = when (category) {
    StorageCategory.IMAGES -> Icons.Rounded.Image
    StorageCategory.VIDEOS -> Icons.Rounded.Videocam
    StorageCategory.AUDIO -> Icons.Rounded.MusicNote
    StorageCategory.DOCUMENTS -> Icons.Rounded.Description
    StorageCategory.APPS -> Icons.Rounded.Android
    StorageCategory.ARCHIVES -> Icons.Rounded.FolderZip
    StorageCategory.OTHER_FILES -> Icons.Rounded.InsertDriveFile
}

private fun categoryName(category: StorageCategory): Int = when (category) {
    StorageCategory.IMAGES -> R.string.storage_cat_images
    StorageCategory.VIDEOS -> R.string.storage_cat_videos
    StorageCategory.AUDIO -> R.string.storage_cat_audio
    StorageCategory.DOCUMENTS -> R.string.storage_cat_documents
    StorageCategory.APPS -> R.string.storage_cat_apks
    StorageCategory.ARCHIVES -> R.string.storage_cat_archives
    StorageCategory.OTHER_FILES -> R.string.storage_cat_other
}

/** The internal storage and every SD card or USB drive that is attached, with the space they have and what fills it. */
@Composable
fun StorageScreen(bottomPadding: Dp, canCount: Boolean, onBack: () -> Unit, onOpenMedia: (MediaGroup) -> Unit) {
    val context = LocalContext.current
    var storages by remember { mutableStateOf<List<StorageInfo>?>(null) }
    // Per storage path: what the files add up to, filled in when the count is done
    var usage by remember { mutableStateOf<Map<String, Map<StorageCategory, CategoryTotal>>>(emptyMap()) }
    var biggest by remember { mutableStateOf<Map<String, StorageUsage.ScanResult>>(emptyMap()) }
    // Counting the files needs All files access, the sizes of the storages themselves do not
    LaunchedEffect(canCount) {
        val list = withContext(Dispatchers.IO) { read(context) }
        storages = list
        if (!canCount) return@LaunchedEffect
        list.filter { it.mounted && it.total > 0 && it.path.isNotEmpty() }.forEach { storage ->
            launch {
                val result = withContext(Dispatchers.IO) { StorageUsage.scanFull(File(storage.path), 10) }
                usage = usage + (storage.path to result.totals)
                biggest = biggest + (storage.path to result)
            }
        }
    }
    SettingsPageScaffold(stringResource(R.string.storage_title), bottomPadding, onBack) {
        storages?.forEach { storage ->
            val totals = usage[storage.path]
            if (storage.mounted && storage.total > 0) {
                Overview(storage, totals, canCount, onOpenMedia)
            }
            val scan = biggest[storage.path]
            if (scan != null && scan.largestFiles.isNotEmpty()) {
                SettingsGroup(
                    stringResource(R.string.storage_biggest_folders),
                    scan.largestFolders.map { (folder, bytes) ->
                        { BigRow(folder.name, folder.parent.orEmpty(), Formatter.formatFileSize(context, bytes)) }
                    }
                )
                SettingsGroup(
                    stringResource(R.string.storage_biggest_files),
                    scan.largestFiles.map { (file, bytes) ->
                        { BigRow(file.name, file.parent.orEmpty(), Formatter.formatFileSize(context, bytes)) }
                    }
                )
            }
            SettingsGroup(
                if (storage.mounted && storage.total > 0) stringResource(R.string.storage_details) else storage.title,
                buildList<@Composable () -> Unit> {
                    if (storage.mounted && storage.total > 0) {
                        add { Detail(stringResource(R.string.storage_free), Formatter.formatFileSize(context, storage.free)) }
                    } else {
                        add { Detail(stringResource(R.string.storage_state), stringResource(R.string.storage_not_mounted)) }
                    }
                    add {
                        Detail(
                            stringResource(R.string.storage_kind),
                            stringResource(if (storage.removable) R.string.storage_removable else R.string.storage_internal)
                        )
                    }
                    if (storage.fileSystem.isNotEmpty()) add { Detail(stringResource(R.string.storage_filesystem), storage.fileSystem) }
                    if (storage.uuid.isNotEmpty()) add { Detail(stringResource(R.string.storage_id), storage.uuid) }
                    if (storage.path.isNotEmpty()) add { Detail(stringResource(R.string.storage_path), storage.path) }
                }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The big picture of one storage: used of total, a bar split by category and a line per category. */
@Composable
private fun Overview(storage: StorageInfo, totals: Map<StorageCategory, CategoryTotal>?, canCount: Boolean, onOpenMedia: (MediaGroup) -> Unit) {
    val context = LocalContext.current
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    // The rest of the used space is what the file scan cannot see: apps, their data and the system
    val counted = totals?.values?.sumOf { it.bytes } ?: 0L
    val system = (storage.used - counted).coerceAtLeast(0L)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(surface, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(storage.title, fontSize = AppText.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(Formatter.formatFileSize(context, storage.used), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(
                "  " + stringResource(R.string.storage_of, Formatter.formatFileSize(context, storage.total)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        // One bar: a piece per category, the free space at the end
        Row(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (totals != null) {
                StorageCategory.entries.forEach { category ->
                    val bytes = totals.getValue(category).bytes
                    if (bytes > 0) Box(Modifier.weight(bytes.toFloat() / storage.total).fillMaxHeight().background(CategoryColors.getValue(category)))
                }
                if (system > 0) Box(Modifier.weight(system.toFloat() / storage.total).fillMaxHeight().background(SystemColor))
            } else if (storage.used > 0) {
                Box(Modifier.weight(storage.used.toFloat() / storage.total).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
            }
            if (storage.free > 0) Box(Modifier.weight(storage.free.toFloat() / storage.total).fillMaxHeight())
        }
        Text(
            stringResource(R.string.storage_free_of, Formatter.formatFileSize(context, storage.free)),
            fontSize = AppText.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    if (totals == null && !canCount) {
        PermissionNotice()
    } else if (totals == null) {
        Row(Modifier.padding(horizontal = 32.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.storage_counting), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
        }
    } else {
        val rows = buildList<@Composable () -> Unit> {
            StorageCategory.entries.filter { totals.getValue(it).count > 0 }.sortedByDescending { totals.getValue(it).bytes }.forEach { category ->
                val total = totals.getValue(category)
                val group = when (category) {
                    StorageCategory.IMAGES -> MediaGroup.IMAGES
                    StorageCategory.VIDEOS -> MediaGroup.VIDEOS
                    StorageCategory.AUDIO -> MediaGroup.MUSIC
                    StorageCategory.DOCUMENTS -> MediaGroup.DOCUMENTS
                    else -> null
                }
                // The media categories open the media screen, but only on the internal storage: that screen reads the phone
                val opens = group != null && !storage.removable
                add {
                    CategoryRow(
                        icon = categoryIcon(category),
                        color = CategoryColors.getValue(category),
                        name = stringResource(categoryName(category)),
                        detail = pluralStringResource(R.plurals.storage_files, total.count, total.count) + "  ·  " + percent(total.bytes, storage.total),
                        size = Formatter.formatFileSize(context, total.bytes),
                        onClick = if (opens) ({ onOpenMedia(group!!) }) else null
                    )
                }
            }
            if (system > 0) add {
                CategoryRow(
                    icon = Icons.Rounded.Settings,
                    color = SystemColor,
                    name = stringResource(R.string.storage_cat_system),
                    detail = percent(system, storage.total),
                    size = Formatter.formatFileSize(context, system),
                    onClick = null
                )
            }
        }
        SettingsGroup(stringResource(R.string.storage_categories), rows)
        SettingsNote(stringResource(R.string.storage_note))
    }
}

private fun percent(part: Long, total: Long): String =
    if (total <= 0) "" else {
        val value = part * 100.0 / total
        if (value < 1.0 && part > 0) "<1%" else "${value.roundToInt()}%"
    }

@Composable
private fun CategoryRow(icon: ImageVector, color: Color, name: String, detail: String, size: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, color, Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = AppText.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(size, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 16.dp)
        )
    }
}

private fun read(context: android.content.Context): List<StorageInfo> {
    val manager = context.getSystemService(StorageManager::class.java)
    val mounts = readMounts()
    return manager.storageVolumes.map { volume ->
        val dir = volume.directory
        val mounted = volume.state == Environment.MEDIA_MOUNTED && dir != null
        var total = 0L
        var free = 0L
        if (mounted) {
            try {
                val stat = StatFs(dir!!.path)
                total = stat.totalBytes
                free = stat.availableBytes
            } catch (e: Exception) {
                // The numbers stay 0 and the screen says nothing about the size
            }
        }
        // The internal storage is a view on the data partition, so the file system of /data is the real one
        val lookup = if (volume.isPrimary || volume.isEmulated) "/data" else dir?.path.orEmpty()
        StorageInfo(
            title = volume.getDescription(context) ?: dir?.name.orEmpty(),
            path = dir?.path.orEmpty(),
            removable = volume.isRemovable,
            mounted = mounted,
            total = total,
            free = free,
            fileSystem = fileSystemOf(lookup, mounts),
            uuid = volume.uuid.orEmpty()
        )
    }
}

/** Mount point and file system type of every line in /proc/mounts. Empty when the file cannot be read. */
private fun readMounts(): List<Pair<String, String>> =
    try {
        File("/proc/mounts").readLines().mapNotNull { line ->
            val parts = line.split(' ')
            if (parts.size >= 3) parts[1] to parts[2] else null
        }
    } catch (e: Exception) {
        emptyList()
    }

/** The file system of the mount that holds the path: the longest mount point that is a prefix of it. */
private fun fileSystemOf(path: String, mounts: List<Pair<String, String>>): String {
    if (path.isEmpty()) return ""
    return mounts
        .filter { (mount, _) -> path == mount || path.startsWith(mount.trimEnd('/') + "/") }
        .maxByOrNull { it.first.length }
        ?.second
        .orEmpty()
}

@Composable
private fun BigRow(name: String, parent: String, size: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(parent, fontSize = AppText.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(size, modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
