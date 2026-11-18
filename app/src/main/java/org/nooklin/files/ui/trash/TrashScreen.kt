package org.nooklin.files.ui.trash

import org.nooklin.files.ui.theme.AppText
import org.nooklin.files.ui.common.GlassPill
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.files.R
import org.nooklin.files.cloud.DavUrls
import org.nooklin.files.fs.FileItem
import org.nooklin.files.ui.FilesViewModel
import org.nooklin.files.ui.common.NooklinMenu
import org.nooklin.files.ui.files.ItemCollection
import org.nooklin.files.ui.files.GroupedItem
import org.nooklin.files.ui.files.SearchField
import org.nooklin.files.ui.files.formatDate
import org.nooklin.files.ui.files.formatSize
import org.nooklin.files.ui.files.rememberDateFormat
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    vm: FilesViewModel,
    bottomPadding: Dp,
    remote: Boolean,
    onRemoteChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onEmptyTrash: () -> Unit
) {
    val context = LocalContext.current
    val view = vm.settings.collectAsStateWithLifecycle().value.folderView
    val items by vm.trashItems.collectAsStateWithLifecycle()
    val localSelected by vm.trashSelected.collectAsStateWithLifecycle()
    val account by vm.nc.account.collectAsStateWithLifecycle()
    val remoteTrash by vm.nc.trash.collectAsStateWithLifecycle()
    // Remote mode only makes sense while an account exists
    val showRemote = remote && account != null
    val selected = if (showRemote) remoteTrash.selected else localSelected
    val dateFormat = rememberDateFormat()
    var menuOpen by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // The list is read from disk when the tab opens
    LaunchedEffect(showRemote) {
        query = ""
        if (showRemote) vm.nc.refreshTrash() else vm.refreshTrash()
    }
    val limits = vm.trashLimits
    val shownLocal = if (query.isBlank()) items else items.filter { it.name.contains(query.trim(), ignoreCase = true) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selected.size), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { if (showRemote) vm.nc.clearTrashSelection() else vm.clearTrashSelection() }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { if (showRemote) vm.nc.selectAllTrash() else vm.selectAllTrash() }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.location_recently_deleted), fontWeight = FontWeight.Bold, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.common_more))
                        }
                        NooklinMenu(menuOpen, { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_select_all)) },
                                enabled = if (showRemote) remoteTrash.items.isNotEmpty() else items.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    if (showRemote) vm.nc.selectAllTrash() else vm.selectAllTrash()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_empty_trash)) },
                                enabled = if (showRemote) remoteTrash.items.isNotEmpty() else items.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    onEmptyTrash()
                                }
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
            if (account != null) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !showRemote,
                        onClick = { onRemoteChange(false) },
                        label = { Text(stringResource(R.string.location_phone)) }
                    )
                    FilterChip(
                        selected = showRemote,
                        onClick = { onRemoteChange(true) },
                        label = { Text(stringResource(R.string.location_nextcloud)) }
                    )
                }
            }
            Text(
                if (showRemote) stringResource(R.string.nc_trash_info)
                else stringResource(R.string.trash_info, limits.retentionDays, formatSize(context, limits.maxBytes), limits.maxItems),
                fontSize = AppText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (showRemote) {
                val list = if (query.isBlank()) remoteTrash.items else remoteTrash.items.filter { it.name.contains(query.trim(), ignoreCase = true) }
                when {
                    remoteTrash.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(remoteTrash.error.orEmpty(), color = GlassPill.destructiveText, modifier = Modifier.padding(16.dp))
                            TextButton(onClick = { vm.nc.refreshTrash() }) { Text(stringResource(R.string.nc_retry)) }
                        }
                    }
                    remoteTrash.loading && list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (remoteTrash.items.isEmpty()) R.string.trash_empty else R.string.search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        val rows = list.map { FileItem(it.name, it.trashPath, it.isDir, it.size, it.deletedAt) }
                        val byPath = list.associateBy { it.trashPath }
                        ItemCollection(
                            items = rows,
                            view = view,
                            bottomPadding = bottomPadding,
                            subtitle = { row ->
                                val item = byPath.getValue(row.path)
                                context.getString(
                                    R.string.trash_row_subtitle,
                                    DavUrls.parentOf(item.originalLocation),
                                    formatSize(context, item.size),
                                    formatDate(dateFormat, item.deletedAt)
                                )
                            },
                            isSelected = { it.path in selected },
                            onClick = { vm.nc.toggleTrashSelect(it.path) },
                            onLongClick = { vm.nc.toggleTrashSelect(it.path) }
                        )
                    }
                }
            } else if (shownLocal.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (items.isEmpty()) R.string.trash_empty else R.string.search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val rows = shownLocal.map { FileItem(it.name, it.id, it.isDir, it.size, it.deletedAt) }
                val byId = shownLocal.associateBy { it.id }
                ItemCollection(
                    items = rows,
                    view = view,
                    bottomPadding = bottomPadding,
                    subtitle = { row ->
                        val item = byId.getValue(row.path)
                        context.getString(
                            R.string.trash_row_subtitle,
                            File(item.originalPath).parent.orEmpty(),
                            formatSize(context, item.size),
                            formatDate(dateFormat, item.deletedAt)
                        )
                    },
                    isSelected = { it.path in selected },
                    onClick = { vm.toggleTrashSelect(it.path) },
                    onLongClick = { vm.toggleTrashSelect(it.path) }
                )
            }
        }
    }
}
