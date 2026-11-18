package org.nooklin.files.ui.navigation

import org.nooklin.files.ui.theme.AppText
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.files.R
import org.nooklin.files.ops.OpState
import org.nooklin.files.ops.opTitle
import org.nooklin.files.ui.common.GlassPill
import org.nooklin.files.ui.common.GlassPillContainer
import org.nooklin.files.ui.files.formatSize

enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Rounded.Home),
    RECENTS(R.string.tab_recents, Icons.Rounded.Schedule),
    SETTINGS(R.string.tab_settings, Icons.Rounded.Settings)
}

/** The main places of the app: a floating pill at the bottom of the screen. */
@Composable
fun AppBottomBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    GlassPillContainer(
        modifier = modifier,
        backgroundColor = GlassPill.adaptiveFloatingBackground,
        border = GlassPill.adaptiveBorder,
        verticalContentPadding = 0.dp
    ) {
        // 56 dp, the same height as the round search button next to it
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (active) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            else Modifier
                        )
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint)
                    Text(stringResource(tab.label), color = tint, fontSize = AppText.Caption)
                }
            }
        }
    }
}

/** A floating pill with a row of icon buttons, used while items are selected. */
@Composable
fun ActionPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    GlassPillContainer(
        modifier = modifier,
        backgroundColor = GlassPill.adaptiveFloatingBackground,
        border = GlassPill.adaptiveBorder,
        verticalContentPadding = 0.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun RowScope.PillButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.weight(1f)) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** What is running now: title, file, progress bar and a Cancel button. */
@Composable
fun OperationPill(state: OpState, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    GlassPillContainer(
        modifier = modifier,
        backgroundColor = GlassPill.adaptiveFloatingBackground,
        border = GlassPill.adaptiveBorder,
        verticalContentPadding = 4.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(opTitle(state.type, state.itemCount), fontWeight = FontWeight.Bold, fontSize = AppText.Body)
                    val line = when {
                        state.conflict != null -> stringResource(R.string.op_waiting_for_answer)
                        state.preparing -> stringResource(R.string.op_preparing)
                        else -> state.current
                    }
                    Text(
                        line,
                        fontSize = AppText.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
            }
            if (state.total > 0 && !state.preparing) {
                LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val detail = buildString {
                if (state.total > 0 && !state.preparing) {
                    if (state.bytesBased) {
                        append(stringResource(R.string.op_progress_bytes, formatSize(context, state.done), formatSize(context, state.total)))
                        if (state.bytesPerSecond > 0) {
                            append("  ·  ")
                            append(stringResource(R.string.op_speed, formatSize(context, state.bytesPerSecond)))
                        }
                        if (state.etaSeconds > 0) {
                            append("  ·  ")
                            append(stringResource(R.string.op_eta, formatDuration(state.etaSeconds)))
                        }
                    } else {
                        append(stringResource(R.string.op_progress_items, state.done, state.total))
                    }
                }
                if (state.queued > 0) {
                    if (isNotEmpty()) append("  ·  ")
                    append(stringResource(R.string.op_queued, state.queued))
                }
            }
            if (detail.isNotEmpty()) {
                Text(detail, fontSize = AppText.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

/** Something on the Nextcloud server is running: what it is and a Cancel button. */
@Composable
fun BusyPill(text: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    GlassPillContainer(
        modifier = modifier,
        backgroundColor = GlassPill.adaptiveFloatingBackground,
        border = GlassPill.adaptiveBorder,
        verticalContentPadding = 4.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, fontSize = AppText.Body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** Shown after Copy or Cut: Paste here, or forget the clipboard. */
@Composable
fun ClipboardPill(text: String, onPaste: (() -> Unit)?, onClear: () -> Unit, modifier: Modifier = Modifier) {
    GlassPillContainer(
        modifier = modifier,
        backgroundColor = GlassPill.adaptiveFloatingBackground,
        border = GlassPill.adaptiveBorder,
        verticalContentPadding = 0.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = AppText.Body)
            if (onPaste != null) TextButton(onClick = onPaste) { Text(stringResource(R.string.action_paste)) }
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_cancel))
            }
        }
    }
}
