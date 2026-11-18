package org.nooklin.files.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nooklin.files.R
import org.nooklin.files.ui.common.TileColors
import org.nooklin.files.ui.common.canUseAppLock

@Composable
fun SettingsPrivacyScreen(
    appLock: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    disableScreenshots: Boolean,
    onDisableScreenshotsChange: (Boolean) -> Unit,
    shareEnabled: Boolean,
    onShareEnabledChange: (Boolean) -> Unit,
    bottomPadding: Dp,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    SettingsPageScaffold(stringResource(R.string.settings_privacy), bottomPadding, onBack) {
        SettingsGroup(
            stringResource(R.string.settings_app_lock),
            listOf({
                SettingsToggleRow(stringResource(R.string.settings_app_lock), appLock, Icons.Filled.Lock, TileColors.Privacy) { on ->
                    // Without a screen lock the prompt could never be passed, so it cannot be switched on
                    if (on && !canUseAppLock(context)) {
                        Toast.makeText(context, R.string.settings_app_lock_unavailable, Toast.LENGTH_LONG).show()
                    } else {
                        onAppLockChange(on)
                    }
                }
            })
        )
        SettingsNote(stringResource(R.string.settings_app_lock_explanation))
        Spacer(Modifier.height(8.dp))
        SettingsGroup(
            stringResource(R.string.settings_screenshots),
            listOf({
                SettingsToggleRow(
                    stringResource(R.string.settings_disable_screenshots),
                    disableScreenshots,
                    Icons.Filled.Screenshot,
                    TileColors.Screenshots,
                    onCheckedChange = onDisableScreenshotsChange
                )
            })
        )
        SettingsNote(stringResource(R.string.settings_screenshots_explanation))
        Spacer(Modifier.height(8.dp))
        SettingsGroup(
            stringResource(R.string.settings_sharing),
            listOf({
                SettingsToggleRow(
                    stringResource(R.string.settings_share),
                    shareEnabled,
                    Icons.Filled.Share,
                    TileColors.Share,
                    onCheckedChange = onShareEnabledChange
                )
            })
        )
        SettingsNote(stringResource(R.string.settings_share_explanation))
    }
}
