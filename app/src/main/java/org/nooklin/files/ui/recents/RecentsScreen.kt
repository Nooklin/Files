package org.nooklin.files.ui.recents

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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import org.nooklin.files.ui.FilesViewModel
import org.nooklin.files.data.FolderView
import org.nooklin.files.ui.files.ItemCollection
import org.nooklin.files.ui.files.GroupedItem
import org.nooklin.files.ui.files.formatDate
import org.nooklin.files.ui.files.formatSize
import org.nooklin.files.ui.files.rememberDateFormat
import java.io.File

/** The files with the newest modified date on the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(vm: FilesViewModel, bottomPadding: Dp, onOpenFolder: (String) -> Unit) {
    val context = LocalContext.current
    val recents by vm.recents.collectAsStateWithLifecycle()
    val dateFormat = rememberDateFormat()
    val view = vm.settings.collectAsStateWithLifecycle().value.folderView
    // Read from disk each time the tab opens
    LaunchedEffect(Unit) { vm.refreshRecents() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tab_recents), fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (recents.loading) {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
                }
            }
            if (!recents.loading && recents.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.recents_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ItemCollection(
                items = recents.items,
                view = view,
                bottomPadding = bottomPadding,
                subtitle = { item ->
                    File(item.path).parentFile?.name.orEmpty() + "  ·  " +
                        formatSize(context, item.size) + "  ·  " + formatDate(dateFormat, item.modified)
                },
                isSelected = { false },
                onClick = { vm.openFileOrEditor(context, it.path) },
                // Outside the List view there is no button at the end of a row: a long press goes to the folder
                onLongClick = { if (view != FolderView.LIST) File(it.path).parent?.let(onOpenFolder) },
                modifier = Modifier.weight(1f),
                trailing = { item ->
                    {
                        IconButton(onClick = { File(item.path).parent?.let(onOpenFolder) }) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = stringResource(R.string.action_go_to_folder))
                        }
                    }
                }
            )
        }
    }
}
