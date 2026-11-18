package org.nooklin.files.ui.servers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import org.nooklin.files.R
import org.nooklin.files.cloud.DavUrls
import org.nooklin.files.ui.FilesViewModel
import org.nooklin.files.ui.common.NooklinMenu
import org.nooklin.files.ui.files.Breadcrumbs
import org.nooklin.files.ui.files.FileRow
import org.nooklin.files.ui.files.GroupedItem
import org.nooklin.files.ui.files.SearchField
import org.nooklin.files.ui.files.formatDate
import org.nooklin.files.ui.files.formatSize
import org.nooklin.files.ui.files.rememberDateFormat

/** The files on an FTP or SMB server, in the same look as the folders on the phone and on Nextcloud. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(vm: FilesViewModel, bottomPadding: Dp, onBack: () -> Unit, onNewFolder: () -> Unit) {
    val servers = vm.servers
    val state by servers.remote.collectAsStateWithLifecycle()
    val server by servers.current.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dateFormat = rememberDateFormat()
    var menuOpen by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        servers.upload(context, uris)
    }
    val rootLabel = server?.name.orEmpty()
    val selectionMode = state.selected.isNotEmpty() || state.selecting
    var query by rememberSaveable { mutableStateOf("") }
    // Searching filters the open folder only, a new folder starts without a filter
    LaunchedEffect(state.path) { query = "" }
    val shown = if (query.isBlank()) state.items else state.items.filter { it.name.contains(query.trim(), ignoreCase = true) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, state.selected.size), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { servers.clearSelection() }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { servers.selectAll() }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        val name = if (state.path.isEmpty()) rootLabel else DavUrls.nameOf(state.path)
                        Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        if (servers.canGoUp()) {
                            IconButton(onClick = { servers.goUp() }) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = stringResource(R.string.action_up))
                            }
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.common_more))
                        }
                        NooklinMenu(menuOpen, { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_new_folder)) },
                                onClick = { menuOpen = false; onNewFolder() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nc_upload)) },
                                onClick = { menuOpen = false; picker.launch(arrayOf("*/*")) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_select)) },
                                onClick = { menuOpen = false; servers.startSelecting() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nc_refresh)) },
                                onClick = { menuOpen = false; servers.refresh() }
                            )
                        }
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
            SearchField(query = query, onQueryChange = { query = it }, onClear = { query = "" })
            Breadcrumbs(
                path = state.path,
                rootPath = "",
                rootLabel = rootLabel,
                onNavigate = { servers.open(it) },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading && state.items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null && state.items.isEmpty() -> Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { servers.refresh() }) { Text(stringResource(R.string.nc_retry)) }
                    }

                    state.items.isEmpty() || shown.isEmpty() -> Text(
                        stringResource(if (state.items.isEmpty()) R.string.pane_empty else R.string.search_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    else -> LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding)) {
                        itemsIndexed(shown, key = { _, item -> item.path }) { index, item ->
                            GroupedItem(index, shown.size) {
                                val date = if (item.modified > 0) formatDate(dateFormat, item.modified) else ""
                                val subtitle = when {
                                    item.isDir -> date
                                    date.isEmpty() -> formatSize(context, item.size)
                                    else -> formatSize(context, item.size) + "  ·  " + date
                                }
                                FileRow(
                                    item = item,
                                    subtitle = subtitle,
                                    selected = item.path in state.selected,
                                    onClick = {
                                        when {
                                            selectionMode -> servers.toggleSelect(item.path)
                                            item.isDir -> servers.open(item.path)
                                            else -> servers.openRemoteFile(context, item)
                                        }
                                    },
                                    onLongClick = { servers.toggleSelect(item.path) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
