package org.nooklin.files.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nooklin.files.BuildConfig
import org.nooklin.files.R
import org.nooklin.files.ui.common.GlassPill
import org.nooklin.files.ui.common.TileColors
import org.nooklin.files.ui.common.openUrl

@Composable
fun SettingsAboutScreen(bottomPadding: Dp, onOpenChangelog: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var showPrivacy by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }

    SettingsPageScaffold(stringResource(R.string.settings_about), bottomPadding, onBack) {
        SettingsGroup(
            null,
            listOf(
                { AboutValueRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME) },
                { AboutValueRow(stringResource(R.string.about_license), "GPL-3.0") },
                { SettingsNavRow(stringResource(R.string.about_changelog), Icons.Filled.History, TileColors.About) { onOpenChangelog() } }
            )
        )
        Spacer(Modifier.height(8.dp))
        SettingsGroup(
            null,
            listOf(
                { SettingsNavRow(stringResource(R.string.about_github), Icons.Filled.Code, TileColors.About) { openUrl(context, GITHUB_URL) } },
                { SettingsNavRow(stringResource(R.string.about_privacy), Icons.Filled.PrivacyTip, TileColors.Privacy) { showPrivacy = true } },
                { SettingsNavRow(stringResource(R.string.about_terms), Icons.Filled.Description, TileColors.About) { showTerms = true } }
            )
        )
    }

    if (showPrivacy) {
        AboutDialog(
            title = stringResource(R.string.about_privacy),
            texts = listOf(
                R.string.about_privacy_1, R.string.about_privacy_2, R.string.about_privacy_3,
                R.string.about_privacy_4, R.string.about_privacy_5, R.string.about_privacy_6
            ),
            onDismiss = { showPrivacy = false }
        )
    }
    if (showTerms) {
        AboutDialog(
            title = stringResource(R.string.about_terms),
            texts = listOf(
                R.string.about_terms_1, R.string.about_terms_2, R.string.about_terms_3,
                R.string.about_terms_4, R.string.about_terms_5, R.string.about_terms_6
            ),
            onDismiss = { showTerms = false }
        )
    }
}

@Composable
private fun AboutDialog(title: String, texts: List<Int>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                texts.forEachIndexed { index, id ->
                    Text(stringResource(id), modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } }
    )
}

@Composable
private fun AboutValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
