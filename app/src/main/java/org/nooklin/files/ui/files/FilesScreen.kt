package org.nooklin.files.ui.files

import org.nooklin.files.ui.theme.AppText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import org.nooklin.files.data.FolderView
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.nooklin.files.R
import org.nooklin.files.cloud.DavUrls
import org.nooklin.files.ui.nextcloud.RemotePane
import org.nooklin.files.fs.FileItem
import org.nooklin.files.fs.SortKey
import org.nooklin.files.fs.SortSpec
import org.nooklin.files.ui.FilesViewModel
import org.nooklin.files.ui.PaneUi
import org.nooklin.files.ui.SearchUi
import org.nooklin.files.ui.common.GlassPill
import org.nooklin.files.ui.common.NooklinMenu
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    vm: FilesViewModel,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onNameDialog: (NameDialogState) -> Unit,
    onNcNewFolder: () -> Unit
) {
    val context = LocalContext.current
    val dual by vm.dualPane.collectAsStateWithLifecycle()
    val active by vm.activePane.collectAsStateWithLifecycle()
    val pane0 by vm.panes[0].collectAsStateWithLifecycle()
    val pane1 by vm.panes[1].collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val secondRemote by vm.secondRemote.collectAsStateWithLifecycle()
    val ncAccount by vm.nc.account.collectAsStateWithLifecycle()
    val ncRemote by vm.nc.remote.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    // Read so the title and path bar follow a drive that is attached or removed
    vm.volumes.collectAsStateWithLifecycle().value
    val activeUi = if (active == 0 || !dual) pane0 else pane1
    val activeIndex = if (dual) active else 0
    val selectionCount = activeUi.selected.size
    val selectingNow = selectionCount > 0 || activeUi.selecting
    // The second pane shows the server while this is on, and the title bar and menu follow the pane that is active
    val secondRemoteShown = dual && secondRemote && ncAccount != null
    val remoteActive = secondRemoteShown && active == 1
    val remoteSelecting = ncRemote.selected.isNotEmpty() || ncRemote.selecting
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.nc.upload(context, uris)
    }
    val searchState = search
    var menuOpen by remember { mutableStateOf(false) }
    var viewMenuOpen by remember { mutableStateOf(false) }
    val folderView = vm.settings.collectAsStateWithLifecycle().value.folderView
    val rootLabel = stringResource(R.string.internal_storage)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            when {
                remoteActive && remoteSelecting -> TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, ncRemote.selected.size), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { vm.nc.clearSelection() }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.nc.selectAll() }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                        }
                    }
                )

                remoteActive -> TopAppBar(
                    title = {
                        val name = if (ncRemote.path.isEmpty()) stringResource(R.string.location_nextcloud) else DavUrls.nameOf(ncRemote.path)
                        Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        if (vm.nc.canGoUp()) {
                            IconButton(onClick = { vm.nc.goUp() }) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.action_up))
                            }
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.common_more))
                        }
                        RemoteFilesMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            onNewFolder = onNcNewFolder,
                            onUpload = { picker.launch(arrayOf("*/*")) },
                            onSelect = { vm.nc.startSelecting() },
                            onSelectAll = { vm.nc.selectAll() },
                            onRefresh = { vm.nc.refresh() },
                            onShowPhone = { vm.setSecondRemote(false) },
                            onToggleDual = { vm.toggleDualPane() }
                        )
                    }
                )

                selectingNow -> TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selectionCount), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection(activeIndex) }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.selectAll(activeIndex) }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                        }
                    }
                )

                else -> TopAppBar(
                    title = {
                        val drive = vm.volumeFor(activeUi.path)
                        val name = when {
                            activeUi.path == vm.root.path -> rootLabel
                            drive != null && drive.path == activeUi.path -> drive.label
                            else -> File(activeUi.path).name
                        }
                        Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        if (vm.canGoUp(activeIndex)) {
                            IconButton(onClick = { vm.goUp(activeIndex) }) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.action_up))
                            }
                        }
                        IconButton(onClick = { viewMenuOpen = true }) {
                            Icon(Icons.Rounded.GridView, contentDescription = stringResource(R.string.view_button))
                        }
                        NooklinMenu(viewMenuOpen, { viewMenuOpen = false }) {
                            listOf(
                                FolderView.LIST to R.string.view_list,
                                FolderView.DETAILS to R.string.view_details,
                                FolderView.GRID to R.string.view_grid,
                                FolderView.COMPACT_GRID to R.string.view_compact_grid
                            ).forEach { (option, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    trailingIcon = { if (folderView == option) Icon(Icons.Filled.Check, contentDescription = null) },
                                    onClick = { viewMenuOpen = false; vm.setFolderView(option) }
                                )
                            }
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.common_more))
                        }
                        FilesMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            sort = activeUi.sort,
                            dual = dual,
                            bookmarked = activeUi.path in bookmarks,
                            onNewFolder = { onNameDialog(NameDialogState(NameDialogKind.NEW_FOLDER)) },
                            onNewFile = { onNameDialog(NameDialogState(NameDialogKind.NEW_FILE)) },
                            onSort = { vm.setSort(activeIndex, it) },
                            onSelect = { vm.startSelecting(activeIndex) },
                            onSelectAll = { vm.selectAll(activeIndex) },
                            onToggleBookmark = { vm.toggleBookmark(activeUi.path) },
                            folderPath = activeUi.path,
                            onToggleDual = { vm.toggleDualPane() },
                            showNextcloudChoice = dual && ncAccount != null,
                            onShowNextcloud = { vm.setSecondRemote(true) }
                        )
                    }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!remoteActive) SearchField(
                query = searchState?.query.orEmpty(),
                onQueryChange = {
                    if (searchState == null) vm.startSearch()
                    vm.setQuery(it)
                },
                onClear = { vm.stopSearch() }
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (searchState != null && searchState.query.isNotBlank() && !remoteActive) {
                    SearchResults(
                        search = searchState,
                        scopeLabel = File(activeUi.path).name.ifEmpty { activeUi.path },
                        bottomPadding = bottomPadding,
                        vm = vm,
                        onOpenFolder = { vm.open(vm.activePane.value, it) }
                    )
                } else if (dual) {
                    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    if (landscape) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaneView(0, pane0, active == 0, true, vm, bottomPadding, rootLabel, folderView, Modifier.weight(1f))
                            SecondPane(vm, pane1, active, secondRemoteShown, bottomPadding, rootLabel, folderView, Modifier.weight(1f))
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaneView(0, pane0, active == 0, true, vm, 0.dp, rootLabel, folderView, Modifier.weight(1f))
                            SecondPane(vm, pane1, active, secondRemoteShown, bottomPadding, rootLabel, folderView, Modifier.weight(1f))
                        }
                    }
                } else {
                    PaneView(0, pane0, true, false, vm, bottomPadding, rootLabel, folderView, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** The search bar under the title, as on the Home screen. Searches the open folder and the folders in it. */
@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    val container = GlassPill.adaptiveGlassBackground
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_cancel))
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun FilesMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    sort: SortSpec,
    dual: Boolean,
    bookmarked: Boolean,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onSort: (SortSpec) -> Unit,
    onSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleBookmark: () -> Unit,
    folderPath: String,
    onToggleDual: () -> Unit,
    showNextcloudChoice: Boolean,
    onShowNextcloud: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    NooklinMenu(expanded, onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_new_folder)) },
            onClick = { onDismiss(); onNewFolder() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_new_file)) },
            onClick = { onDismiss(); onNewFile() }
        )
        HorizontalDivider()
        listOf(
            SortKey.NAME to R.string.sort_name,
            SortKey.TYPE to R.string.sort_type,
            SortKey.SIZE to R.string.sort_size,
            SortKey.DATE to R.string.sort_date
        ).forEach { (key, label) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                trailingIcon = { if (sort.key == key) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = { onSort(sort.copy(key = key)) }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(if (sort.ascending) R.string.sort_ascending else R.string.sort_descending)) },
            onClick = { onSort(sort.copy(ascending = !sort.ascending)) }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_select)) },
            onClick = { onDismiss(); onSelect() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_select_all)) },
            onClick = { onDismiss(); onSelectAll() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(if (bookmarked) R.string.action_remove_favorite else R.string.action_add_favorite)) },
            onClick = { onDismiss(); onToggleBookmark() }
        )
        if (androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_pin_shortcut)) },
                onClick = { onDismiss(); org.nooklin.files.ui.pinFolderShortcut(context, folderPath) }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(if (dual) R.string.action_hide_second_pane else R.string.action_show_second_pane)) },
            onClick = { onDismiss(); onToggleDual() }
        )
        if (showNextcloudChoice) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_second_pane_nextcloud)) },
                onClick = { onDismiss(); onShowNextcloud() }
            )
        }
    }
}

/** The menu while the second pane shows the Nextcloud server and is the active pane. */
@Composable
private fun RemoteFilesMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onNewFolder: () -> Unit,
    onUpload: () -> Unit,
    onSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onRefresh: () -> Unit,
    onShowPhone: () -> Unit,
    onToggleDual: () -> Unit
) {
    NooklinMenu(expanded, onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_new_folder)) },
            onClick = { onDismiss(); onNewFolder() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.nc_upload)) },
            onClick = { onDismiss(); onUpload() }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_select)) },
            onClick = { onDismiss(); onSelect() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_select_all)) },
            onClick = { onDismiss(); onSelectAll() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.nc_refresh)) },
            onClick = { onDismiss(); onRefresh() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_second_pane_phone)) },
            onClick = { onDismiss(); onShowPhone() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_hide_second_pane)) },
            onClick = { onDismiss(); onToggleDual() }
        )
    }
}

@Composable
private fun SecondPane(
    vm: FilesViewModel,
    pane: PaneUi,
    active: Int,
    remote: Boolean,
    bottomPadding: Dp,
    rootLabel: String,
    view: FolderView,
    modifier: Modifier
) {
    if (remote) RemotePane(vm, active == 1, bottomPadding, modifier)
    else PaneView(1, pane, active == 1, true, vm, bottomPadding, rootLabel, view, modifier)
}

@Composable
internal fun SearchResults(
    search: SearchUi,
    scopeLabel: String,
    bottomPadding: Dp,
    vm: FilesViewModel,
    onOpenFolder: (String) -> Unit
) {
    val context = LocalContext.current
    val folderView = vm.settings.collectAsStateWithLifecycle().value.folderView
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.search_scope, scopeLabel),
            fontSize = AppText.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (search.running) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
            }
        }
        if (!search.running && search.query.isNotBlank() && search.results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val goToFolder: (FileItem) -> Unit = { item ->
            val parent = File(item.path).parent
            vm.stopSearch()
            if (parent != null) onOpenFolder(parent)
        }
        ItemCollection(
            items = search.results,
            view = folderView,
            bottomPadding = bottomPadding,
            subtitle = { File(it.path).parent.orEmpty() },
            isSelected = { false },
            onClick = { item ->
                if (item.isDir) {
                    vm.stopSearch()
                    onOpenFolder(item.path)
                } else {
                    vm.openFileOrEditor(context, item.path)
                }
            },
            // Outside the List view there is no button at the end of a row: a long press goes to the folder
            onLongClick = { if (folderView != FolderView.LIST) goToFolder(it) },
            modifier = Modifier.weight(1f),
            trailing = { item ->
                {
                    IconButton(onClick = { goToFolder(item) }) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = stringResource(R.string.action_go_to_folder))
                    }
                }
            },
            footer = if (search.capped) ({
                Text(
                    stringResource(R.string.search_capped, org.nooklin.files.fs.FileSearch.MAX_RESULTS),
                    fontSize = AppText.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }) else null
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun PaneView(
    index: Int,
    ui: PaneUi,
    isActive: Boolean,
    dual: Boolean,
    vm: FilesViewModel,
    bottomPadding: Dp,
    rootLabel: String,
    view: FolderView,
    modifier: Modifier
) {
    val context = LocalContext.current
    val dateFormat = rememberDateFormat()
    val selectionMode = ui.selected.isNotEmpty() || ui.selecting
    val border = if (dual && isActive) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    } else {
        Modifier
    }
    Column(modifier.then(border)) {
        val drive = vm.volumeFor(ui.path)
        Breadcrumbs(
            path = ui.path,
            rootPath = drive?.path ?: vm.root.path,
            rootLabel = drive?.label ?: rootLabel,
            onNavigate = {
                vm.setActivePane(index)
                vm.open(index, it)
            },
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(index) { detectTapGestures { vm.setActivePane(index) } }
        ) {
            key(ui.navId, view) {
                when {
                    ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    ui.unreadable -> Text(
                        stringResource(R.string.pane_unreadable),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )

                    ui.items.isEmpty() -> Text(
                        stringResource(R.string.pane_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    else -> {
                        val onClickItem: (FileItem) -> Unit = { item ->
                            vm.setActivePane(index)
                            when {
                                selectionMode -> vm.toggleSelect(index, item.path)
                                item.isDir -> vm.open(index, item.path)
                                else -> vm.openFileOrEditor(context, item.path)
                            }
                        }
                        val onLongClickItem: (FileItem) -> Unit = { item ->
                            vm.setActivePane(index)
                            vm.toggleSelect(index, item.path)
                        }
                        val start = ui.restoreScroll.coerceIn(0, ui.items.lastIndex)
                        if (view == FolderView.GRID || view == FolderView.COMPACT_GRID) {
                            val compact = view == FolderView.COMPACT_GRID
                            val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = start)
                            LaunchedEffect(gridState) {
                                snapshotFlow { gridState.firstVisibleItemIndex }
                                    .debounce(400)
                                    .collect { vm.reportScroll(index, it) }
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(if (compact) 80.dp else 112.dp),
                                state = gridState,
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomPadding)
                            ) {
                                items(ui.items, key = { it.path }) { item ->
                                    FileGridCell(
                                        item = item,
                                        selected = item.path in ui.selected,
                                        compact = compact,
                                        onClick = { onClickItem(item) },
                                        onLongClick = { onLongClickItem(item) }
                                    )
                                }
                            }
                        } else {
                            val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
                            LaunchedEffect(listState) {
                                snapshotFlow { listState.firstVisibleItemIndex }
                                    .debounce(400)
                                    .collect { vm.reportScroll(index, it) }
                            }
                            LazyColumn(state = listState, contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding)) {
                                itemsIndexed(ui.items, key = { _, item -> item.path }) { position, item ->
                                    GroupedItem(position, ui.items.size) {
                                        if (view == FolderView.DETAILS) {
                                            FileDetailRow(
                                                item = item,
                                                sizeText = if (item.isDir) null else formatSize(context, item.size),
                                                dateText = formatDate(dateFormat, item.modified),
                                                selected = item.path in ui.selected,
                                                onClick = { onClickItem(item) },
                                                onLongClick = { onLongClickItem(item) }
                                            )
                                        } else {
                                            FileRow(
                                                item = item,
                                                subtitle = rowSubtitle(context, item, dateFormat),
                                                selected = item.path in ui.selected,
                                                onClick = { onClickItem(item) },
                                                onLongClick = { onLongClickItem(item) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun rowSubtitle(context: android.content.Context, item: FileItem, dateFormat: java.text.DateFormat): String {
    val date = formatDate(dateFormat, item.modified)
    return if (item.isDir) date else formatSize(context, item.size) + "  ·  " + date
}
