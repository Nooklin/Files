package org.nooklin.files.ui.settings

import org.nooklin.files.ui.theme.AppText
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.files.R
import org.nooklin.files.ui.FilesViewModel
import org.nooklin.files.ui.common.GlassPill
import org.nooklin.files.ui.common.IconTile
import org.nooklin.files.ui.common.NextcloudTile
import org.nooklin.files.ui.common.TileColors

enum class SettingsPage { APPEARANCE, NEXTCLOUD, SERVERS, SERVER_EDIT, PRIVACY, PERMISSIONS, HELP, HOWTO, ABOUT, CHANGELOG }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: FilesViewModel, bottomPadding: Dp, onNavigate: (SettingsPage) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_settings), fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsHeading(stringResource(R.string.settings_head_general))
            SettingsGroup(
                null,
                listOf(
                    { SettingsNavRow(stringResource(R.string.settings_appearance), Icons.Filled.Palette, TileColors.Appearance) { onNavigate(SettingsPage.APPEARANCE) } },
                    { SettingsNavRow(stringResource(R.string.settings_permissions), Icons.Filled.Security, TileColors.Warning) { onNavigate(SettingsPage.PERMISSIONS) } }
                )
            )
            SettingsHeading(stringResource(R.string.settings_head_connections))
            SettingsGroup(
                null,
                listOf(
                    { SettingsNavRow(stringResource(R.string.settings_nextcloud), Icons.Filled.Cloud, logo = true) { onNavigate(SettingsPage.NEXTCLOUD) } },
                    { SettingsNavRow(stringResource(R.string.settings_servers), Icons.Filled.Dns, TileColors.Smb) { onNavigate(SettingsPage.SERVERS) } }
                )
            )
            SettingsHeading(stringResource(R.string.settings_head_privacy))
            SettingsGroup(
                null,
                listOf(
                    { SettingsNavRow(stringResource(R.string.settings_privacy), Icons.Filled.Lock, TileColors.Privacy) { onNavigate(SettingsPage.PRIVACY) } }
                )
            )
            SettingsHeading(stringResource(R.string.settings_head_about))
            SettingsGroup(
                null,
                listOf(
                    { SettingsNavRow(stringResource(R.string.settings_help), Icons.AutoMirrored.Filled.Help, TileColors.Help) { onNavigate(SettingsPage.HELP) } },
                    { SettingsNavRow(stringResource(R.string.settings_about), Icons.Filled.Info, TileColors.About) { onNavigate(SettingsPage.ABOUT) } }
                )
            )
            Spacer(Modifier.height(bottomPadding))
        }
    }
}

// Items instead of a content lambda: a divider goes between items, never after the last
@Composable
fun SettingsGroup(title: String?, items: List<@Composable () -> Unit>) {
    if (title != null) {
        Text(
            title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = AppText.Caption,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp)
        )
    }
    Surface(
        color = GlassPill.adaptiveGlassBackground,
        shape = RoundedCornerShape(20.dp),
        border = GlassPill.adaptiveBorder,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        val edgePadding = if (items.size == 1) 4.dp else 8.dp
        Column(Modifier.padding(vertical = edgePadding)) {
            items.forEachIndexed { index, item ->
                item()
                if (index != items.lastIndex) {
                    // start = 56dp aligns past the leading icon
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsNavRow(
    label: String,
    icon: ImageVector = Icons.Filled.MoreHoriz,
    color: Color = MaterialTheme.colorScheme.primary,
    logo: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (logo) NextcloudTile(Modifier.padding(end = 12.dp)) else IconTile(icon, color, Modifier.padding(end = 12.dp))
            Text(label)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}
