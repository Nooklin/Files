package org.nooklin.files.ui.home

import org.nooklin.files.ui.theme.AppText
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.files.R
import org.nooklin.files.ui.FilesViewModel
import org.nooklin.files.ui.common.IconTile
import org.nooklin.files.ui.common.NextcloudTile
import org.nooklin.files.ui.common.PermissionNotice
import org.nooklin.files.ui.common.TileColors
import org.nooklin.files.ui.files.SearchField
import org.nooklin.files.ui.files.SearchResults
import org.nooklin.files.ui.settings.SettingsGroup
import org.nooklin.files.ui.settings.serverIcon
import org.nooklin.files.ui.settings.serverTile
import java.io.File

/** The start screen: the folders the user added as Favorites, and the places to browse. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: FilesViewModel,
    granted: Boolean,
    bottomPadding: Dp,
    onOpen: (String) -> Unit,
    onOpenNextcloud: () -> Unit,
    onOpenServer: (String) -> Unit,
    onOpenApps: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenTrash: () -> Unit
) {
    val favorites by vm.bookmarks.collectAsStateWithLifecycle()
    val volumes by vm.volumes.collectAsStateWithLifecycle()
    val nextcloud by vm.nc.account.collectAsStateWithLifecycle()
    val servers by vm.servers.servers.collectAsStateWithLifecycle()
    val searchFlow by vm.search.collectAsStateWithLifecycle()
    val searchState = searchFlow
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshVolumes() }
    // A drive that is attached or removed while Home is open
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                vm.refreshVolumes()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tab_home), fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Searching the phone needs All files access: without it the search bar gives way to the message
            if (granted) {
                SearchField(
                    query = searchState?.query.orEmpty(),
                    onQueryChange = {
                        if (searchState == null) vm.startSearch()
                        vm.setQuery(it, vm.root.path)
                    },
                    onClear = { vm.stopSearch() }
                )
            }
            if (granted && searchState != null && searchState.query.isNotBlank()) {
                SearchResults(
                    search = searchState,
                    scopeLabel = stringResource(R.string.location_phone),
                    bottomPadding = bottomPadding,
                    vm = vm,
                    onOpenFolder = onOpen
                )
            } else Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (!granted) PermissionNotice()
                if (favorites.isEmpty()) {
                    SettingsGroup(stringResource(R.string.home_favorites), listOf({
                        Text(
                            stringResource(R.string.home_no_favorites),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }))
                } else {
                    SettingsGroup(
                        stringResource(R.string.home_favorites),
                        favorites.map { path ->
                            {
                                HomeRow(
                                    title = File(path).name.ifEmpty { path },
                                    subtitle = File(path).parent.orEmpty(),
                                    onClick = { onOpen(path) },
                                    trailing = {
                                        IconButton(onClick = { vm.removeBookmark(path) }) {
                                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove_favorite))
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
                val locationRows = buildList<@Composable () -> Unit> {
                    add {
                        HomeRow(
                            title = stringResource(R.string.location_phone),
                            subtitle = freeSpaceText(vm.root.path),
                            onClick = { onOpen(vm.root.path) },
                            icon = Icons.Rounded.PhoneAndroid,
                        tile = TileColors.Phone
                        )
                    }
                    volumes.forEach { volume ->
                        add { HomeRow(title = volume.label, subtitle = freeSpaceText(volume.path), onClick = { onOpen(volume.path) }, icon = Icons.Rounded.Usb, tile = TileColors.Drive) }
                    }
                    if (nextcloud != null) {
                        add { HomeRow(title = stringResource(R.string.location_nextcloud), onClick = onOpenNextcloud, icon = Icons.Rounded.Cloud, logo = true) }
                    }
                    servers.forEach { server ->
                        add {
                            HomeRow(
                                title = server.name,
                                subtitle = server.type.name + "  ·  " + server.host,
                                onClick = { onOpenServer(server.id) },
                                icon = serverIcon(server.type),
                                tile = serverTile(server.type)
                            )
                        }
                    }
                    add {
                        HomeRow(
                            title = stringResource(R.string.location_recently_deleted),
                            onClick = onOpenTrash,
                            icon = Icons.Rounded.Delete,
                        tile = TileColors.Trash
                        )
                    }
                }
                SettingsGroup(stringResource(R.string.home_locations), locationRows)
                SettingsGroup(
                    stringResource(R.string.home_tools),
                    listOf(
                        { HomeRow(title = stringResource(R.string.media_title), subtitle = stringResource(R.string.media_subtitle), onClick = onOpenMedia, icon = Icons.Rounded.PermMedia, tile = TileColors.Media) },
                        { HomeRow(title = stringResource(R.string.apps_title), onClick = onOpenApps, icon = Icons.Rounded.Android, tile = TileColors.Apps) },
                        { HomeRow(title = stringResource(R.string.storage_title), onClick = onOpenStorage, icon = Icons.Rounded.SdStorage, tile = TileColors.Storage) }
                    )
                )
                Spacer(Modifier.height(bottomPadding))
            }
        }
    }
}

@Composable
private fun HomeRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    icon: ImageVector = Icons.Rounded.Folder,
    tile: Color = MaterialTheme.colorScheme.primary,
    logo: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onClick() }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = if (trailing == null) 16.dp else 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (logo) NextcloudTile(Modifier.padding(end = 16.dp)) else IconTile(icon, tile, Modifier.padding(end = 16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        subtitle,
                        fontSize = AppText.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (trailing != null) trailing()
    }
}

/** "12 GB free of 128 GB" for a location on the phone, nothing when Android gives no size. */
@Composable
private fun freeSpaceText(path: String): String? {
    val context = LocalContext.current
    val file = File(path)
    val total = file.totalSpace
    if (total <= 0L) return null
    return stringResource(
        R.string.home_free_of,
        android.text.format.Formatter.formatFileSize(context, file.usableSpace),
        android.text.format.Formatter.formatFileSize(context, total)
    )
}
