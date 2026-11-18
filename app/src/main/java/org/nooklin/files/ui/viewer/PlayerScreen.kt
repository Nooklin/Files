package org.nooklin.files.ui.viewer

import org.nooklin.files.ui.common.GlassPill
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.Settings
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Checkbox
import org.nooklin.files.ui.common.NooklinMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.files.R
import org.nooklin.files.fs.MediaKind
import org.nooklin.files.fs.MediaKinds
import org.nooklin.files.player.PipState
import org.nooklin.files.player.enterPip
import org.nooklin.files.player.formatTime
import org.nooklin.files.player.rememberPlaybackController
import org.nooklin.files.player.setPipParams
import org.nooklin.files.ui.common.openFile
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode { NONE, SEEK, BRIGHTNESS, VOLUME }

private fun mediaItemFor(path: String): MediaItem {
    val file = File(path)
    val uri = Uri.fromFile(file)
    return MediaItem.Builder()
        .setMediaId(path)
        .setUri(uri)
        // The service gets the location from here
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(MediaMetadata.Builder().setTitle(file.nameWithoutExtension).build())
        .build()
}

/**
 * Video and music. The player itself lives in PlaybackService, this screen controls it.
 * Video: tap for the controls, double tap left or right for 10 seconds back or forward, slide up or down on the
 * left for brightness and on the right for volume, slide sideways to seek.
 */
@Composable
fun PlayerScreen(
    paths: List<String>,
    startIndex: Int,
    backgroundPlay: Boolean,
    onBackgroundPlay: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val isVideo = MediaKinds.of(File(paths[startIndex.coerceIn(0, paths.lastIndex)])) == MediaKind.VIDEO
    val controllerState = rememberPlaybackController()
    val controller = controllerState.value
    val background by rememberUpdatedState(backgroundPlay)
    val pip by PipState.active

    // Player state, kept up to date by a listener
    var isPlaying by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(startIndex) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var videoSize by remember { mutableStateOf<VideoSize?>(null) }
    var failed by remember { mutableStateOf(false) }

    // Start the queue, or pick up the queue that is already playing
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        val start = startIndex.coerceIn(0, paths.lastIndex)
        val same = c.mediaItemCount == paths.size && paths.indices.all { c.getMediaItemAt(it).mediaId == paths[it] }
        if (same) {
            if (c.currentMediaItemIndex != start) c.seekTo(start, 0L)
        } else {
            c.setMediaItems(paths.map { mediaItemFor(it) }, start, 0L)
            c.prepare()
        }
        c.play()
    }
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                index = c.currentMediaItemIndex
                failed = false
            }
            override fun onPlaybackStateChanged(state: Int) { duration = c.duration.coerceAtLeast(0L) }
            override fun onVideoSizeChanged(size: VideoSize) { videoSize = size }
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        c.addListener(listener)
        isPlaying = c.isPlaying
        index = c.currentMediaItemIndex.coerceAtLeast(0)
        videoSize = c.videoSize
        onDispose { c.removeListener(listener) }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            position = c.currentPosition
            duration = c.duration.coerceAtLeast(0L)
            delay(400)
        }
    }

    // Video stops when the app is left, unless background play is on. Music always continues.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && isVideo && !background) controller?.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Leaving the screen
    DisposableEffect(controller) {
        val c = controller
        onDispose {
            if (c != null && isVideo && !background) {
                c.stop()
                c.clearMediaItems()
            }
        }
    }
    // Picture in picture by home button or gesture, only while a video plays
    DisposableEffect(isVideo, isPlaying, videoSize) {
        if (activity != null && isVideo) setPipParams(activity, videoSize, autoEnter = isPlaying)
        onDispose {}
    }
    DisposableEffect(Unit) {
        onDispose {
            if (activity != null) setPipParams(activity, null, autoEnter = false)
        }
    }

    var closing by remember { mutableStateOf(false) }
    fun close() {
        closing = true
        onClose()
    }
    BackHandler { close() }

    val currentPath = paths[index.coerceIn(0, paths.lastIndex)]
    if (isVideo) {
        VideoLayout(
            controller = controller,
            title = File(currentPath).name,
            queueLabel = if (paths.size > 1) "${index + 1} / ${paths.size}" else null,
            isPlaying = isPlaying,
            position = position,
            duration = duration,
            videoSize = videoSize,
            failed = failed,
            pip = pip,
            hasPrevious = index > 0,
            hasNext = index < paths.lastIndex,
            backgroundPlay = backgroundPlay,
            onBackgroundPlay = onBackgroundPlay,
            onOpenOther = { openFile(context, currentPath) },
            onPip = { if (activity != null) enterPip(activity, videoSize) },
            onClose = ::close
        )
    } else {
        MusicLayout(
            controller = controller,
            path = currentPath,
            queueLabel = if (paths.size > 1) "${index + 1} / ${paths.size}" else null,
            isPlaying = isPlaying,
            position = position,
            duration = duration,
            failed = failed,
            hasPrevious = index > 0,
            hasNext = index < paths.lastIndex,
            onOpenOther = { openFile(context, currentPath) },
            onStop = {
                controller?.stop()
                controller?.clearMediaItems()
                close()
            },
            onClose = ::close
        )
    }
}

@Composable
private fun SeekRow(controller: MediaController?, position: Long, duration: Long, tint: Color) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime((fraction * duration).toLong()), color = tint, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { controller?.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint, inactiveTrackColor = tint.copy(alpha = 0.3f)),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text(formatTime(duration), color = tint, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TransportRow(controller: MediaController?, isPlaying: Boolean, hasPrevious: Boolean, hasNext: Boolean, tint: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = hasPrevious, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.player_previous), tint = tint.copy(alpha = if (hasPrevious) 1f else 0.35f), modifier = Modifier.size(34.dp))
        }
        IconButton(
            onClick = { if (isPlaying) controller?.pause() else controller?.play() },
            modifier = Modifier.size(72.dp)
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                tint = tint,
                modifier = Modifier.size(48.dp)
            )
        }
        IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = hasNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next), tint = tint.copy(alpha = if (hasNext) 1f else 0.35f), modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun VideoLayout(
    controller: MediaController?,
    title: String,
    queueLabel: String?,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    videoSize: VideoSize?,
    failed: Boolean,
    pip: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    backgroundPlay: Boolean,
    onBackgroundPlay: (Boolean) -> Unit,
    onOpenOther: () -> Unit,
    onPip: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    var controls by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var hintJob by remember { mutableStateOf<Job?>(null) }
    fun showHint(text: String, keep: Boolean = false) {
        hint = text
        hintJob?.cancel()
        if (!keep) hintJob = scope.launch {
            delay(700)
            hint = null
        }
    }
    // The controls disappear after a few seconds while the video plays
    LaunchedEffect(controls, isPlaying, touches) {
        if (controls && isPlaying) {
            delay(3500)
            controls = false
        }
    }
    // Brightness goes back to the system setting when the video is left
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }
    }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The picture
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val ratio = videoSize?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width * it.pixelWidthHeightRatio / it.height }
            // The box around it has room for the whole screen, aspectRatio takes the biggest size of the video's shape that fits
            val surfaceModifier = if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize()
            AndroidView(
                factory = { SurfaceView(it).apply { keepScreenOn = true } },
                modifier = surfaceModifier,
                onRelease = { controller?.clearVideoSurfaceView(it) },
                update = { controller?.setVideoSurfaceView(it) }
            )
        }
        if (failed) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_error), color = Color.White, textAlign = TextAlign.Center)
                IconButton(onClick = onOpenOther) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.viewer_open_other), tint = Color.White)
                }
            }
        }
        // Gestures. Nothing is drawn here, in picture in picture only the picture is shown.
        if (!pip) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(controller) {
                        detectTapGestures(
                            onTap = {
                                controls = !controls
                                touches++
                            },
                            onDoubleTap = { at ->
                                val c = controller ?: return@detectTapGestures
                                if (at.x < size.width / 2f) {
                                    c.seekTo((c.currentPosition - 10_000L).coerceAtLeast(0L))
                                    showHint("-10 s")
                                } else {
                                    val end = if (c.duration > 0) c.duration else Long.MAX_VALUE
                                    c.seekTo((c.currentPosition + 10_000L).coerceAtMost(end))
                                    showHint("+10 s")
                                }
                            }
                        )
                    }
                    .pointerInput(controller) {
                        var mode = DragMode.NONE
                        var total = androidx.compose.ui.geometry.Offset.Zero
                        var startX = 0f
                        var basePosition = 0L
                        var target = 0L
                        var baseBrightness = 0.5f
                        var baseVolume = 0f
                        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        detectDragGestures(
                            onDragStart = { start ->
                                mode = DragMode.NONE
                                total = androidx.compose.ui.geometry.Offset.Zero
                                startX = start.x
                                basePosition = controller?.currentPosition ?: 0L
                                target = basePosition
                                val set = activity?.window?.attributes?.screenBrightness ?: -1f
                                baseBrightness = if (set >= 0f) set else
                                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
                                baseVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                total += amount
                                if (mode == DragMode.NONE && total.getDistance() > viewConfiguration.touchSlop * 2) {
                                    mode = if (abs(total.x) > abs(total.y)) DragMode.SEEK
                                    else if (startX < size.width / 2f) DragMode.BRIGHTNESS else DragMode.VOLUME
                                }
                                when (mode) {
                                    DragMode.SEEK -> {
                                        val end = controller?.duration?.takeIf { it > 0 } ?: Long.MAX_VALUE
                                        // A swipe over the whole width is 2 minutes
                                        target = (basePosition + (total.x / size.width * 120_000f).toLong()).coerceIn(0L, end)
                                        val diff = (target - basePosition) / 1000
                                        showHint("${if (diff >= 0) "+" else "-"}${formatTime(abs(diff) * 1000)}  (${formatTime(target)})", keep = true)
                                    }
                                    DragMode.BRIGHTNESS -> {
                                        val value = (baseBrightness - total.y / size.height * 1.3f).coerceIn(0.02f, 1f)
                                        activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = value } }
                                        showHint(context.getString(R.string.player_brightness, (value * 100).roundToInt()), keep = true)
                                    }
                                    DragMode.VOLUME -> {
                                        val value = (baseVolume - total.y / size.height * 1.3f).coerceIn(0f, 1f)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * maxVolume).roundToInt(), 0)
                                        showHint(context.getString(R.string.player_volume, (value * 100).roundToInt()), keep = true)
                                    }
                                    DragMode.NONE -> Unit
                                }
                            },
                            onDragEnd = {
                                if (mode == DragMode.SEEK) controller?.seekTo(target)
                                mode = DragMode.NONE
                                showHint(hint ?: "")
                            },
                            onDragCancel = { mode = DragMode.NONE; hint = null }
                        )
                    }
            )
            hint?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            if (controls) {
                ViewerBar(
                    title = title,
                    subtitle = queueLabel,
                    onBack = onClose,
                    modifier = Modifier.align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.55f)),
                    actions = {
                        IconButton(onClick = onPip) {
                            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = stringResource(R.string.player_pip), tint = Color.White)
                        }
                        Box {
                            IconButton(onClick = { menu = true; touches++ }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = Color.White)
                            }
                            NooklinMenu(menu, onDismiss = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_background)) },
                                    leadingIcon = { Checkbox(checked = backgroundPlay, onCheckedChange = null) },
                                    onClick = { onBackgroundPlay(!backgroundPlay) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.viewer_open_other)) },
                                    onClick = {
                                        menu = false
                                        onOpenOther()
                                    }
                                )
                            }
                        }
                    }
                )
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .navigationBarsPadding()
                ) {
                    SeekRow(controller, position, duration, Color.White)
                    TransportRow(controller, isPlaying, hasPrevious, hasNext, Color.White)
                }
            }
        }
    }
}

@Composable
private fun MusicLayout(
    controller: MediaController?,
    path: String,
    queueLabel: String?,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    failed: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onOpenOther: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    val onBg = MaterialTheme.colorScheme.onBackground
    // Title, artist and cover come from the tags in the file
    val tags by produceState(Triple<String?, String?, Bitmap?>(null, null, null), path) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                val art = retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                Triple(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    art
                )
            } catch (e: Exception) {
                Triple<String?, String?, Bitmap?>(null, null, null)
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ViewerBar(
            title = stringResource(R.string.player_music),
            subtitle = queueLabel,
            onBack = onClose,
            tint = onBg,
            actions = {
                IconButton(onClick = onStop) {
                    Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.player_stop), tint = onBg)
                }
                IconButton(onClick = onOpenOther) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.viewer_open_other), tint = onBg)
                }
            }
        )
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                val art = tags.third
                if (art != null) {
                    Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)))
                } else {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(96.dp))
                }
            }
            Text(
                tags.first?.takeIf { it.isNotBlank() } ?: File(path).nameWithoutExtension,
                color = onBg,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp)
            )
            tags.second?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (failed) Text(stringResource(R.string.player_error), color = GlassPill.destructiveText, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            SeekRow(controller, position, duration, onBg)
            TransportRow(controller, isPlaying, hasPrevious, hasNext, onBg)
        }
    }
}
