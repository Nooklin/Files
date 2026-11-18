package org.nooklin.files.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.files.R
import org.nooklin.files.ui.common.openAllFilesSettings
import org.nooklin.files.ui.common.openNotificationSettings

/** The two permissions of the app: whether they are given, what they are for, and a button to give them. */
@Composable
fun SettingsPermissionsScreen(filesAllowed: Boolean, notificationsAllowed: Boolean, bottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPageScaffold(stringResource(R.string.settings_permissions), bottomPadding, onBack) {
        SettingsGroup(
            stringResource(R.string.permission_files_title),
            listOf({ StatusRow(filesAllowed) { openAllFilesSettings(context) } })
        )
        SettingsNote(stringResource(R.string.permission_files_why) + " " + stringResource(R.string.permission_files_without))
        SettingsGroup(
            stringResource(R.string.permission_notifications_title),
            listOf({ StatusRow(notificationsAllowed) { openNotificationSettings(context) } })
        )
        SettingsNote(stringResource(R.string.permission_notifications_why) + " " + stringResource(R.string.permission_notifications_without))
    }
}

@Composable
private fun StatusRow(allowed: Boolean, onGive: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(if (allowed) R.string.permission_status_allowed else R.string.permission_status_not_allowed))
        }
        if (!allowed) TextButton(onClick = onGive) { Text(stringResource(R.string.permission_give)) }
    }
}
