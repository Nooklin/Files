package org.nooklin.files.ui.media

import org.nooklin.files.ui.theme.AppText
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.produceState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Toc
import androidx.compose.material3.CircularProgressIndicator
import org.nooklin.files.ui.common.NooklinMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.nooklin.files.R
import org.nooklin.files.fs.MediaFile
import org.nooklin.files.fs.MediaGroup
import org.nooklin.files.fs.MediaGroupBy
import org.nooklin.files.fs.MediaGrouping
import org.nooklin.files.fs.MediaKind
import org.nooklin.files.fs.MediaKinds
import org.nooklin.files.fs.MediaSort
import org.nooklin.files.ui.FilesViewModel
import org.nooklin.files.ui.files.formatDate
import org.nooklin.files.ui.files.formatSize
import org.nooklin.files.ui.files.rememberDateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the media screen shows: kept by the caller so it survives opening a file and coming back. */
class MediaViewState(val listState: LazyListState) {
    var group by mutableStateOf(MediaGroup.VIDEOS)
    var groupBy by mutableStateOf(MediaGroupBy.DATE)
    var sort by mutableStateOf(MediaSort.DATE)
    var ascending by mutableStateOf(false)
}

/**
 * Every video, picture, song and document on the phone in one list. Group them by month, folder, type or first
 * letter, sort them, and jump to any header from the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(vm: FilesViewModel, view: MediaViewState, bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val media by vm.media.collectAsStateWithLifecycle()
    val dateFormat = rememberDateFormat()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.refreshMedia() }

    // The choices live outside this screen: it leaves the screen while a viewer is open and must come back as it was
    val group = view.group
    val groupBy = view.groupBy
    val sort = view.sort
    val ascending = view.ascending
    var groupMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var jumpMenu by remember { mutableStateOf(false) }

    val monthFormat = remember { SimpleDateFormat("LLLL yyyy", Locale.getDefault()) }
    val inGroup = remember(media.files, group) { media.files.filter { it.group == group } }
    val sections = remember(inGroup, groupBy, sort, ascending) {
        MediaGrouping.organize(inGroup, groupBy, sort, ascending) { monthFormat.format(Date(it)).replaceFirstChar { c -> c.uppercase() } }
    }
    val listState = view.listState
    // Index of every header in the list, for the jump menu
    val headerIndexes = remember(sections, groupBy) {
        var next = 0
        sections.map { section ->
            val at = next
            next += 1 + section.files.size
            at
        }
    }
    // The queue the viewer pages through: the files of the list in the order shown
    val queue = remember(sections) { sections.flatMap { s -> s.files.map { it.path } } }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.media_title), fontSize = 28.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    // Group by
                    Box {
                        IconButton(onClick = { groupMenu = true }) {
                            Icon(Icons.Rounded.GridView, contentDescription = stringResource(R.string.media_group))
                        }
                        NooklinMenu(groupMenu, onDismiss = { groupMenu = false }) {
                            MediaGroupBy.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(groupByName(option))) },
                                    trailingIcon = { if (option == groupBy) Icon(Icons.Rounded.Check, contentDescription = null) },
                                    onClick = {
                                        view.groupBy = option
                                        groupMenu = false
                                    }
                                )
                            }
                        }
                    }
                    // Sort: one tap picks both the order and the direction
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.media_sort))
                        }
                        NooklinMenu(sortMenu, onDismiss = { sortMenu = false }) {
                            sortChoices.forEach { (option, up, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    trailingIcon = { if (option == sort && up == ascending) Icon(Icons.Rounded.Check, contentDescription = null) },
                                    onClick = {
                                        view.sort = option
                                        view.ascending = up
                                        sortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    // Jump to a header
                    if (groupBy != MediaGroupBy.NONE && sections.size > 1) {
                        Box {
                            IconButton(onClick = { jumpMenu = true }) {
                                Icon(Icons.Rounded.Toc, contentDescription = stringResource(R.string.media_jump))
                            }
                            NooklinMenu(jumpMenu, onDismiss = { jumpMenu = false }) {
                                sections.forEachIndexed { i, section ->
                                    DropdownMenuItem(
                                        text = { Text(headerTitle(section.title, groupBy) + "  (" + section.files.size + ")", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        onClick = {
                                            jumpMenu = false
                                            scope.launch { listState.scrollToItem(headerIndexes[i]) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // The four lists
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MediaGroup.entries.forEach { option ->
                    val count = media.files.count { it.group == option }
                    FilterChip(
                        selected = option == group,
                        onClick = {
                            view.group = option
                            scope.launch { listState.scrollToItem(0) }
                        },
                        label = { Text(stringResource(groupName(option)) + if (media.loading && count == 0) "" else "  $count") }
                    )
                }
            }
            if (media.loading && media.files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (inGroup.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.media_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                MediaList(
                    sections = sections,
                    groupBy = groupBy,
                    state = listState,
                    bottomPadding = bottomPadding,
                    onOpen = { file -> vm.openFileOrEditor(context, file.path, queue.takeIf { MediaKinds.of(file.extension) in setOf(MediaKind.IMAGE, MediaKind.VIDEO, MediaKind.AUDIO) }) },
                    subtitle = { file -> formatSize(context, file.size) + "  ·  " + formatDate(dateFormat, file.modified) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaList(
    sections: List<org.nooklin.files.fs.MediaSection>,
    groupBy: MediaGroupBy,
    state: LazyListState,
    bottomPadding: Dp,
    onOpen: (MediaFile) -> Unit,
    subtitle: (MediaFile) -> String
) {
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        sections.forEach { section ->
            if (groupBy != MediaGroupBy.NONE) {
                stickyHeader {
                    Text(
                        headerTitle(section.title, groupBy) + "  ·  " + section.files.size,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = AppText.Caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            items(section.files, key = { it.path }) { file ->
                MediaRow(file, subtitle(file), onClick = { onOpen(file) })
            }
        }
        item { Box(Modifier.size(bottomPadding)) }
    }
}

@Composable
private fun MediaRow(file: MediaFile, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val density = LocalDensity.current
        val px = with(density) { 96.dp.roundToPx() }
        val thumb by produceState(Thumbnails.cached(file), file.path, file.modified) {
            if (value == null) value = Thumbnails.load(file, px)
        }
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val shown = thumb
            if (shown != null) {
                Image(shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    when (file.group) {
                        MediaGroup.VIDEOS -> Icons.Rounded.Movie
                        MediaGroup.IMAGES -> Icons.Rounded.Image
                        MediaGroup.MUSIC -> Icons.Rounded.MusicNote
                        MediaGroup.DOCUMENTS -> Icons.Rounded.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = AppText.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A folder header shows the folder name, with the full path unnecessary: the last two parts are enough. */
private fun headerTitle(title: String, groupBy: MediaGroupBy): String =
    if (groupBy == MediaGroupBy.FOLDER) title.trimEnd('/').split('/').takeLast(2).joinToString("/") else title

private fun groupByName(option: MediaGroupBy): Int = when (option) {
    MediaGroupBy.NONE -> R.string.media_group_none
    MediaGroupBy.DATE -> R.string.media_group_date
    MediaGroupBy.FOLDER -> R.string.media_group_folder
    MediaGroupBy.TYPE -> R.string.media_group_type
    MediaGroupBy.LETTER -> R.string.media_group_letter
}

private val sortChoices = listOf(
    Triple(MediaSort.DATE, false, R.string.media_sort_date_new),
    Triple(MediaSort.DATE, true, R.string.media_sort_date_old),
    Triple(MediaSort.NAME, true, R.string.media_sort_name_az),
    Triple(MediaSort.NAME, false, R.string.media_sort_name_za),
    Triple(MediaSort.SIZE, false, R.string.media_sort_size_big),
    Triple(MediaSort.SIZE, true, R.string.media_sort_size_small)
)

private fun groupName(option: MediaGroup): Int = when (option) {
    MediaGroup.VIDEOS -> R.string.media_videos
    MediaGroup.IMAGES -> R.string.media_images
    MediaGroup.MUSIC -> R.string.media_music
    MediaGroup.DOCUMENTS -> R.string.media_documents
}
