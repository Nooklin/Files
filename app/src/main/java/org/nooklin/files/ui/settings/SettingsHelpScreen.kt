package org.nooklin.files.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.nooklin.files.R
import org.nooklin.files.ui.common.TileColors
import org.nooklin.files.ui.common.openUrl

const val GITHUB_URL = "https://github.com/Nooklin/Files"

@Composable
fun SettingsHelpScreen(bottomPadding: Dp, onOpenHowTo: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPageScaffold(stringResource(R.string.settings_help), bottomPadding, onBack) {
        SettingsGroup(
            null,
            listOf(
                { SettingsNavRow(stringResource(R.string.help_how_to), Icons.AutoMirrored.Filled.Help, TileColors.Help) { onOpenHowTo() } },
                { SettingsNavRow(stringResource(R.string.help_request_feature), Icons.Filled.Lightbulb, TileColors.Warning) { openUrl(context, GITHUB_URL) } },
                { SettingsNavRow(stringResource(R.string.help_report_issue), Icons.Filled.BugReport, TileColors.Trash) { openUrl(context, GITHUB_URL) } }
            )
        )
    }
}
