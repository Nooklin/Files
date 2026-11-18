package org.nooklin.files.ui.common

import org.nooklin.files.ui.theme.AppText
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import org.nooklin.files.R

/** Opens the Android screen where All files access is switched on for this app. */
fun openAllFilesSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName)))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}

/** Opens the Android notification settings of this app. */
fun openNotificationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

fun notificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

/** The message of a part of the app that cannot work without All files access, with the button to give it. */
@Composable
fun PermissionNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text(
            stringResource(R.string.permission_unavailable),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Button(onClick = { openAllFilesSettings(context) }) { Text(stringResource(R.string.permission_give)) }
    }
}

/** A whole screen for a section that cannot be used without All files access: only the message and the button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionNeededScreen(title: String, onBack: (() -> Unit)?) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PermissionNotice()
        }
    }
}

/**
 * Asked once, the first time the app starts: what each permission is for and what happens without it.
 * Only the permissions that are still missing are listed.
 */
@Composable
fun PermissionsIntroDialog(needFiles: Boolean, needNotifications: Boolean, onContinue: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.permission_intro_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.permission_intro_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (needFiles) {
                    Column {
                        Text(stringResource(R.string.permission_files_title), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.permission_files_why), fontSize = AppText.Body)
                        Text(stringResource(R.string.permission_files_without), fontSize = AppText.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (needNotifications) {
                    Column {
                        Text(stringResource(R.string.permission_notifications_title), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.permission_notifications_why), fontSize = AppText.Body)
                        Text(stringResource(R.string.permission_notifications_without), fontSize = AppText.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.permission_intro_later), fontSize = AppText.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.permission_continue)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.permission_not_now)) } }
    )
}
