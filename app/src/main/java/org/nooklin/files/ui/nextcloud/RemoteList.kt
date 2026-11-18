package org.nooklin.files.ui.nextcloud

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.files.R
import org.nooklin.files.fs.FileItem
import org.nooklin.files.ui.FilesViewModel
import org.nooklin.files.ui.RemoteUi
import org.nooklin.files.ui.files.Breadcrumbs
import org.nooklin.files.ui.files.ItemCollection
import org.nooklin.files.ui.files.formatDate
import org.nooklin.files.ui.files.formatSize
import org.nooklin.files.ui.files.rememberDateFormat

/** The list of the open Nextcloud folder: loading, error, empty and the rows. Shared by the Nextcloud screen and the second pane. */
@Composable
fun RemoteListBody(
    vm: FilesViewModel,
    state: RemoteUi,
    shown: List<FileItem>,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    onActivate: () -> Unit = {}
) {
    val nc = vm.nc
    val context = LocalContext.current
    val dateFormat = rememberDateFormat()
    val selectionMode = state.selected.isNotEmpty() || state.selecting
    Box(modifier) {
        when {
            state.loading && state.items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null && state.items.isEmpty() -> Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { nc.refresh() }) { Text(stringResource(R.string.nc_retry)) }
            }

            state.items.isEmpty() || shown.isEmpty() -> Text(
                stringResource(if (state.items.isEmpty()) R.string.pane_empty else R.string.search_no_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )

            else -> ItemCollection(
                items = shown,
                view = vm.settings.collectAsStateWithLifecycle().value.folderView,
                bottomPadding = bottomPadding,
                subtitle = { subtitle(it, context, dateFormat) },
                isSelected = { it.path in state.selected },
                onClick = { item ->
                    onActivate()
                    when {
                        selectionMode -> nc.toggleSelect(item.path)
                        item.isDir -> nc.open(item.path)
                        else -> nc.openRemoteFile(context, item)
                    }
                },
                onLongClick = { item ->
                    onActivate()
                    nc.toggleSelect(item.path)
                }
            )
        }
    }
}

private fun subtitle(item: FileItem, context: android.content.Context, dateFormat: java.text.DateFormat): String {
    val date = if (item.modified > 0) formatDate(dateFormat, item.modified) else ""
    return when {
        item.isDir -> date
        date.isEmpty() -> formatSize(context, item.size)
        else -> formatSize(context, item.size) + "  ·  " + date
    }
}

/** The second pane while it shows the Nextcloud server. Same look as a pane with a folder of the phone. */
@Composable
fun RemotePane(vm: FilesViewModel, isActive: Boolean, bottomPadding: Dp, modifier: Modifier = Modifier) {
    val state by vm.nc.remote.collectAsStateWithLifecycle()
    val border = if (isActive) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    } else {
        Modifier
    }
    Column(modifier.then(border)) {
        Breadcrumbs(
            path = state.path,
            rootPath = "",
            rootLabel = stringResource(R.string.location_nextcloud),
            onNavigate = {
                vm.setActivePane(1)
                vm.nc.open(it)
            },
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        RemoteListBody(
            vm = vm,
            state = state,
            shown = state.items,
            bottomPadding = bottomPadding,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { vm.setActivePane(1) } },
            onActivate = { vm.setActivePane(1) }
        )
    }
}
