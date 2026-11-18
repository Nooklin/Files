package org.nooklin.files.ui.navigation

import org.nooklin.files.ui.theme.AppText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import org.nooklin.files.R
import org.nooklin.files.fs.MediaKind
import org.nooklin.files.fs.MediaKinds
import org.nooklin.files.player.rememberPlaybackController
import org.nooklin.files.ui.common.GlassPill
import org.nooklin.files.ui.common.GlassPillContainer
import java.io.File

/**
 * A small bar for music that keeps playing after the player screen was closed: song, play or pause, stop.
 * Tapping it opens the player screen with the same queue. Shows nothing when no song is loaded.
 */
@Composable
fun MiniPlayer(onOpen: (List<String>, Int) -> Unit, modifier: Modifier = Modifier) {
    val controller by rememberPlaybackController()
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { tick++ }
        }
        c.addListener(listener)
        tick++
        onDispose { c.removeListener(listener) }
    }
    val c = controller ?: return
    // Read so the bar updates with every player event
    if (tick < 0) return
    if (c.mediaItemCount == 0) return
    val path = c.currentMediaItem?.mediaId ?: return
    if (MediaKinds.of(File(path)) != MediaKind.AUDIO) return
    GlassPillContainer(
        modifier = modifier,
        backgroundColor = GlassPill.adaptiveFloatingBackground,
        border = GlassPill.adaptiveBorder,
        verticalContentPadding = 0.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable {
                    val paths = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
                    onOpen(paths, c.currentMediaItemIndex.coerceAtLeast(0))
                }
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                File(path).nameWithoutExtension,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = AppText.Body
            )
            IconButton(onClick = { if (c.isPlaying) c.pause() else c.play() }) {
                Icon(
                    if (c.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (c.isPlaying) R.string.player_pause else R.string.player_play)
                )
            }
            IconButton(onClick = {
                c.stop()
                c.clearMediaItems()
            }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.player_stop))
            }
        }
    }
}
