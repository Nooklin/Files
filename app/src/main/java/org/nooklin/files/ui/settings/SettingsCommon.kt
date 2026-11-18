package org.nooklin.files.ui.settings

import org.nooklin.files.ui.theme.AppText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.nooklin.files.R
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import org.nooklin.files.ui.common.IconTile
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import org.nooklin.files.ui.common.GlassPill
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The small grey explanation under a group, lined up with the group title. */
@Composable
fun SettingsNote(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = AppText.Caption,
        modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

/** A section heading on the main settings screen, the same as in Nooklin Photos. */
@Composable
fun SettingsHeading(title: String) {
    Text(
        title,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 28.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
}

// RadioButton keeps a real onClick: with null the dot didn't visually update on a device. Touches go to the innermost node, so no double toggle.
@Composable
fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    fontFamily: FontFamily? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.RadioButton) { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The row is the touch target, the 48 dp the button asks for would make the row taller than the main menu rows
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            RadioButton(selected = selected, onClick = onClick)
        }
        Text(label, fontFamily = fontFamily, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Switch, enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, if (enabled) color else color.copy(alpha = 0.38f), Modifier.padding(end = 12.dp))
            Text(label, color = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f))
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

/** The frame of a settings screen with a back arrow, a large title and a scrolling column. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageScaffold(
    title: String,
    bottomPadding: Dp,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            content()
            Spacer(Modifier.height(bottomPadding))
        }
    }
}

/** A row with the current choice on the right; tapping it opens a dialog with the options (same as Nooklin Photos). */
@Composable
fun <T> SettingsChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    fontFor: (T) -> FontFamily? = { null },
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button) { open = true }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(current, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            shape = GlassPill.shape,
            containerColor = GlassPill.adaptivePopupBackground,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text(label) },
            text = {
                Column {
                    options.forEach { (value, text) ->
                        SettingsRadioRow(text, value == selected, fontFamily = fontFor(value)) {
                            onSelect(value)
                            open = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}
