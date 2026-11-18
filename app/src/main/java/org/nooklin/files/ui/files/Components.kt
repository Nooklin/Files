package org.nooklin.files.ui.files

import org.nooklin.files.ui.theme.AppText
import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.files.fs.FileItem
import org.nooklin.files.ui.common.GlassPill
import java.io.File
import java.text.DateFormat
import java.util.Date

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp", "dng", "svg")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v")
private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "wav", "m4a", "ogg", "opus", "aac", "wma")
private val TEXT_EXTENSIONS = setOf("txt", "md", "json", "xml", "csv", "log", "kt", "java", "py", "js", "html", "css", "sh", "yml", "yaml", "ini", "doc", "docx", "odt")
private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "xz", "bz2")

fun iconFor(item: FileItem): ImageVector = when {
    item.isDir -> Icons.Rounded.Folder
    item.extension in IMAGE_EXTENSIONS -> Icons.Rounded.Image
    item.extension in VIDEO_EXTENSIONS -> Icons.Rounded.Movie
    item.extension in AUDIO_EXTENSIONS -> Icons.Rounded.MusicNote
    item.extension == "pdf" -> Icons.Rounded.PictureAsPdf
    item.extension == "apk" -> Icons.Rounded.Android
    item.extension in ARCHIVE_EXTENSIONS -> Icons.Rounded.FolderZip
    item.extension in TEXT_EXTENSIONS -> Icons.Rounded.Description
    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

/** The color of the icon of a file, by kind. Folders use the accent color of the app. */
fun iconColorFor(item: FileItem, accent: Color, neutral: Color): Color = when {
    item.isDir -> accent
    item.extension in IMAGE_EXTENSIONS -> Color(0xFF34C759)
    item.extension in VIDEO_EXTENSIONS -> Color(0xFFAF52DE)
    item.extension in AUDIO_EXTENSIONS -> Color(0xFFFF2D55)
    item.extension == "pdf" -> Color(0xFFFF3B30)
    item.extension == "apk" -> Color(0xFF3DDC84)
    item.extension in ARCHIVE_EXTENSIONS -> Color(0xFFFF9500)
    item.extension in TEXT_EXTENSIONS -> Color(0xFF0A84FF)
    else -> neutral
}

/** The thumbnail of a picture, video, song cover or pdf; null for folders, other files, or while it loads. */
@Composable
fun rememberItemThumbnail(item: FileItem, px: Int): androidx.compose.ui.graphics.ImageBitmap? {
    val group = if (item.isDir) null else org.nooklin.files.fs.MediaKinds.groupOf(item.extension)
    if (group == null) return null
    val media = remember(item.path, item.modified) {
        org.nooklin.files.fs.MediaFile(item.name, item.path, item.extension, item.size, item.modified, group)
    }
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        org.nooklin.files.ui.media.Thumbnails.cached(media), item.path, item.modified
    ) {
        if (value == null) value = org.nooklin.files.ui.media.Thumbnails.load(media, px)
    }
    return bitmap?.asImageBitmap()
}

/** The icon of an item, or its thumbnail on a rounded tile of the given size. */
@Composable
private fun ItemVisual(item: FileItem, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp?, tint: Color) {
    val thumb = rememberItemThumbnail(item, with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() })
    if (thumb != null) {
        androidx.compose.foundation.Image(
            thumb,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.2f))
        )
    } else {
        Icon(iconFor(item), contentDescription = null, tint = tint, modifier = if (iconSize != null) Modifier.size(iconSize) else Modifier)
    }
}

fun formatSize(context: Context, bytes: Long): String = Formatter.formatFileSize(context, bytes)

@Composable
fun rememberDateFormat(): DateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

fun formatDate(format: DateFormat, millis: Long): String = format.format(Date(millis))

/** One row of the file list: icon, name, a line of details and an optional button at the end. */
@Composable
fun FileRow(
    item: FileItem,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .background(if (selected) accent.copy(alpha = 0.15f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accent)
            } else {
                ItemVisual(item, 40.dp, null, iconColorFor(item, accent, MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                fontSize = AppText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) trailing()
    }
}

/** The path as tappable parts: Internal storage, then every folder below it. */
@Composable
fun Breadcrumbs(
    path: String,
    rootPath: String,
    rootLabel: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(path, rootPath, rootLabel) { breadcrumbSegments(path, rootPath, rootLabel) }
    val listState = rememberLazyListState()
    LaunchedEffect(path) {
        if (segments.isNotEmpty()) listState.scrollToItem(segments.lastIndex)
    }
    LazyRow(
        modifier.fillMaxWidth(),
        state = listState,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        itemsIndexed(segments) { index, segment ->
            val last = index == segments.lastIndex
            if (index > 0) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                segment.second,
                fontSize = AppText.Body,
                fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clickable(enabled = !last) { onNavigate(segment.first) }
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            )
        }
    }
}

private fun breadcrumbSegments(path: String, rootPath: String, rootLabel: String): List<Pair<String, String>> {
    val result = ArrayList<Pair<String, String>>()
    val base: String
    val rest: String
    if (path == rootPath || path.startsWith("$rootPath/")) {
        result.add(rootPath to rootLabel)
        base = rootPath
        rest = path.removePrefix(rootPath)
    } else {
        result.add("/" to "/")
        base = ""
        rest = path
    }
    var current = base
    for (part in rest.split('/').filter { it.isNotEmpty() }) {
        current = "$current/$part"
        result.add(current to part)
    }
    return result
}

fun parentName(path: String): String = File(path).parent ?: path

/**
 * One row of a rounded group, as on the Home screen. index and count decide which corners are rounded and
 * whether a divider is drawn, so the rows of a lazy list together look like one card.
 */
@Composable
fun GroupedItem(index: Int, count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val radius = 20.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) radius else 0.dp,
        topEnd = if (index == 0) radius else 0.dp,
        bottomStart = if (index == count - 1) radius else 0.dp,
        bottomEnd = if (index == count - 1) radius else 0.dp
    )
    val border = GlassPill.adaptiveBorder
    val first = index == 0
    val last = index == count - 1
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .clip(shape)
            .background(GlassPill.adaptiveGlassBackground)
            .drawWithContent {
                drawContent()
                // The outline of the whole card, drawn per row: the part of it that is outside this row is clipped away
                val stroke = border.width.toPx()
                val extra = radius.toPx() * 2
                drawRoundRect(
                    brush = border.brush,
                    topLeft = Offset(stroke / 2, if (first) stroke / 2 else -extra),
                    size = Size(
                        size.width - stroke,
                        size.height - (if (first) stroke / 2 else -extra) + (if (last) -stroke / 2 else extra)
                    ),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(width = stroke)
                )
            }
    ) {
        content()
        if (index != count - 1) {
            // 68 dp: past the 16 dp padding, the 40 dp icon and the 12 dp gap
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        }
    }
}

/** A dense row for the Details view: icon, name, and the size and date at the end. */
@Composable
fun FileDetailRow(
    item: FileItem,
    sizeText: String?,
    dateText: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (selected) accent.copy(alpha = 0.15f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accent)
            } else {
                ItemVisual(item, 28.dp, null, iconColorFor(item, accent, MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(item.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (sizeText != null) {
                Text(sizeText, fontSize = AppText.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(dateText, fontSize = AppText.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** One item of the Grid and Compact grid views: a colored icon on a tile with the name under it. */
@Composable
fun FileGridCell(
    item: FileItem,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val tint = iconColorFor(item, accent, MaterialTheme.colorScheme.onSurfaceVariant)
    val tile = if (compact) 44.dp else 64.dp
    val icon = if (compact) 26.dp else 38.dp
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.15f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(tile)
                .clip(RoundedCornerShape(tile * 0.28f))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(icon))
            } else {
                val thumb = rememberItemThumbnail(item, with(androidx.compose.ui.platform.LocalDensity.current) { tile.roundToPx() })
                if (thumb != null) {
                    androidx.compose.foundation.Image(
                        thumb,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(iconFor(item), contentDescription = null, tint = tint, modifier = Modifier.size(icon))
                }
            }
        }
        Text(
            item.name,
            fontSize = if (compact) AppText.Caption else AppText.Caption,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/**
 * A list of items in the folder view the user picked (List, Details, Grid, Compact grid). Used by the lists that are not a folder:
 * Recents, search results, Recently Deleted and Nextcloud. trailing is only shown in the List view; in the other views
 * onLongClick is the way to the extra action.
 */
@Composable
fun ItemCollection(
    items: List<FileItem>,
    view: org.nooklin.files.data.FolderView,
    bottomPadding: androidx.compose.ui.unit.Dp,
    subtitle: (FileItem) -> String,
    isSelected: (FileItem) -> Boolean,
    onClick: (FileItem) -> Unit,
    onLongClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
    trailing: ((FileItem) -> (@Composable () -> Unit))? = null,
    footer: (@Composable () -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateFormat = rememberDateFormat()
    if (view == org.nooklin.files.data.FolderView.GRID || view == org.nooklin.files.data.FolderView.COMPACT_GRID) {
        val compact = view == org.nooklin.files.data.FolderView.COMPACT_GRID
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(if (compact) 80.dp else 112.dp),
            modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomPadding)
        ) {
            items(items.size, key = { items[it].path }) { position ->
                val item = items[position]
                FileGridCell(item, isSelected(item), compact, { onClick(item) }, { onLongClick(item) })
            }
            if (footer != null) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { footer() }
            }
        }
    } else {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = bottomPadding)
        ) {
            items(items.size, key = { items[it].path }) { position ->
                val item = items[position]
                GroupedItem(position, items.size) {
                    if (view == org.nooklin.files.data.FolderView.DETAILS) {
                        FileDetailRow(
                            item = item,
                            sizeText = if (item.isDir) null else formatSize(context, item.size),
                            dateText = if (item.modified > 0) formatDate(dateFormat, item.modified) else "",
                            selected = isSelected(item),
                            onClick = { onClick(item) },
                            onLongClick = { onLongClick(item) }
                        )
                    } else {
                        FileRow(
                            item = item,
                            subtitle = subtitle(item),
                            selected = isSelected(item),
                            onClick = { onClick(item) },
                            onLongClick = { onLongClick(item) },
                            trailing = trailing?.invoke(item)
                        )
                    }
                }
            }
            if (footer != null) item { footer() }
        }
    }
}
