package org.nooklin.files.ui.apps

import org.nooklin.files.ui.theme.AppText
import org.nooklin.files.ui.common.GlassPill
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.files.R
import org.nooklin.files.fs.AppBackup
import org.nooklin.files.fs.AppEntry
import org.nooklin.files.ui.files.GroupedItem
import org.nooklin.files.ui.files.SearchField
import java.io.File

private enum class AppFilter { USER, SYSTEM, ALL }

/** All installed apps, with a back-up of the APK and uninstalling for the apps you installed yourself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(AppFilter.USER) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    var busy by remember { mutableStateOf(false) }

    var showBackups by remember { mutableStateOf(false) }
    // The total size of each app (with data and cache), known once Usage access is on
    var totals by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var usageAccess by remember { mutableStateOf(AppStorageStats.usageAccessGranted(context)) }
    // Coming back from the Android settings screen: check the switch again
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) usageAccess = AppStorageStats.usageAccessGranted(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(reload) {
        apps = withContext(Dispatchers.IO) { AppBackup.load(context.packageManager) }
    }
    LaunchedEffect(apps, usageAccess) {
        val list = apps
        if (list != null && usageAccess) {
            withContext(Dispatchers.IO) { AppStorageStats.totals(context, list) { totals = it } }
        }
    }
    // Back from the uninstall screen of Android: read the list again, the app may be gone
    val uninstall = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { reload++ }

    val shown = apps?.filter { app ->
        (filter == AppFilter.ALL || (filter == AppFilter.SYSTEM) == app.isSystem) &&
            (query.isBlank() || app.label.contains(query.trim(), ignoreCase = true) || app.packageName.contains(query.trim(), ignoreCase = true))
    }

    if (showBackups) {
        BackupsScreen(bottomPadding) { showBackups = false }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.apps_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showBackups = true }) {
                        Icon(Icons.Rounded.Restore, contentDescription = stringResource(R.string.restore_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchField(query = query, onQueryChange = { query = it }, onClear = { query = "" })
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                listOf(
                    AppFilter.USER to R.string.apps_filter_user,
                    AppFilter.SYSTEM to R.string.apps_filter_system,
                    AppFilter.ALL to R.string.apps_filter_all
                ).forEach { (option, label) ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(stringResource(label)) })
                }
            }
            if (!usageAccess) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.apps_usage_note),
                        fontSize = AppText.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName)))
                        } catch (e: android.content.ActivityNotFoundException) {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    }) { Text(stringResource(R.string.apps_usage_allow)) }
                }
            }
            when {
                shown == null -> Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }

                shown.isEmpty() -> Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.search_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
                    itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
                        GroupedItem(index, shown.size) {
                            AppRow(app, totals[app.packageName]) { selected = app }
                        }
                    }
                }
            }
        }
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { if (!busy) selected = null },
            title = { Text(app.label) },
            text = {
                Column {
                    Text(app.packageName, fontSize = AppText.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.apps_detail, app.versionName.ifEmpty { "-" }, android.text.format.Formatter.formatFileSize(context, app.size)),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        stringResource(if (app.isSystem) R.string.apps_system_note else R.string.apps_user_note),
                        fontSize = AppText.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (busy) CircularProgressIndicator(Modifier.padding(top = 12.dp))
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    AppBackup.backup(app, File(root, "App backups"))
                                }
                            }
                            busy = false
                            selected = null
                            val text = result.fold(
                                { context.getString(R.string.apps_backup_done, it.name) },
                                { context.getString(R.string.apps_backup_failed, it.message.orEmpty()) }
                            )
                            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                        }
                    }) { Text(stringResource(R.string.apps_backup)) }
                    if (!app.isSystem) {
                        TextButton(enabled = !busy, onClick = {
                            selected = null
                            uninstall.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName)))
                        }) { Text(stringResource(R.string.apps_uninstall), color = GlassPill.destructiveText) }
                    }
                    TextButton(enabled = !busy, onClick = {
                        selected = null
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName))
                        )
                    }) { Text(stringResource(R.string.apps_info)) }
                }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun AppRow(app: AppEntry, total: Long?, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.Default) {
            runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) } ?: androidx.compose.foundation.layout.Spacer(Modifier.size(40.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The size comes right under the name: the total when Android reports it, the APK otherwise
            val apk = android.text.format.Formatter.formatFileSize(context, app.size)
            Text(
                if (total != null) stringResource(R.string.apps_size_total, android.text.format.Formatter.formatFileSize(context, total), apk)
                else stringResource(R.string.apps_size_apk, apk),
                fontSize = AppText.Caption,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                app.packageName,
                fontSize = AppText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
