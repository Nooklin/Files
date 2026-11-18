package org.nooklin.files.ui.apps

import org.nooklin.files.ui.theme.AppText
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.files.R
import org.nooklin.files.fs.BackupEntry
import org.nooklin.files.fs.BackupList
import org.nooklin.files.install.ApkInstaller
import org.nooklin.files.ui.files.GroupedItem
import org.nooklin.files.ui.files.formatDate
import org.nooklin.files.ui.files.rememberDateFormat
import java.io.File

/** The back-ups in Download/App backups. Tap one to install it again. App data is not part of a back-up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = rememberDateFormat()
    var backups by remember { mutableStateOf<List<BackupEntry>?>(null) }
    var chosen by remember { mutableStateOf<BackupEntry?>(null) }
    var needsPermission by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    BackHandler { onBack() }
    LaunchedEffect(Unit) {
        backups = withContext(Dispatchers.IO) {
            BackupList.list(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "App backups"))
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.restore_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        val list = backups
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.restore_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Column {
                    Text(
                        stringResource(R.string.restore_note),
                        fontSize = AppText.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
                        itemsIndexed(list, key = { _, b -> b.path }) { index, backup ->
                            GroupedItem(index, list.size) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 60.dp)
                                        .clickable {
                                            if (context.packageManager.canRequestPackageInstalls()) chosen = backup else needsPermission = true
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(backup.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            Formatter.formatFileSize(context, backup.size) +
                                                (if (backup.apks.size > 1) "  ·  " + context.getString(R.string.restore_parts, backup.apks.size) else "") +
                                                "  ·  " + formatDate(dateFormat, backup.modified),
                                            fontSize = AppText.Caption,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
    chosen?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!busy) chosen = null },
            title = { Text(stringResource(R.string.restore_confirm_title, backup.name)) },
            text = { Text(stringResource(R.string.restore_confirm_text)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { ApkInstaller.install(context, backup.apks) } }
                        busy = false
                        chosen = null
                        // Android takes over from here, the result comes as a message
                        result.exceptionOrNull()?.let {
                            Toast.makeText(context, context.getString(R.string.restore_failed, it.message.orEmpty()), Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.restore_install)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { chosen = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
    if (needsPermission) {
        AlertDialog(
            onDismissRequest = { needsPermission = false },
            title = { Text(stringResource(R.string.restore_permission_title)) },
            text = { Text(stringResource(R.string.restore_permission_text)) },
            confirmButton = {
                TextButton(onClick = {
                    needsPermission = false
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)))
                }) { Text(stringResource(R.string.restore_permission_open)) }
            },
            dismissButton = { TextButton(onClick = { needsPermission = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}
