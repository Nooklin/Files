package org.nooklin.files.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.util.Rational
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/** True while the app is shown in the small picture in picture window. Set by MainActivity. */
object PipState {
    val active = mutableStateOf(false)
}

/**
 * Tells Android the shape of the video and whether the app may go to picture in picture by itself
 * (home button or home gesture) while a video plays.
 */
fun setPipParams(activity: Activity, size: VideoSize?, autoEnter: Boolean) {
    try {
        activity.setPictureInPictureParams(pipParams(size, autoEnter))
    } catch (_: IllegalStateException) {
    } catch (_: IllegalArgumentException) {
    }
}

/** Opens the picture in picture window now. */
fun enterPip(activity: Activity, size: VideoSize?) {
    try {
        activity.enterPictureInPictureMode(pipParams(size, true))
    } catch (_: IllegalStateException) {
    } catch (_: IllegalArgumentException) {
    }
}

private fun pipParams(size: VideoSize?, autoEnter: Boolean): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder().setAutoEnterEnabled(autoEnter)
    if (size != null && size.width > 0 && size.height > 0) {
        // Android only accepts shapes between 1:2.39 and 2.39:1
        val ratio = (size.width * size.pixelWidthHeightRatio) / size.height
        val clamped = ratio.coerceIn(0.42f, 2.38f)
        builder.setAspectRatio(Rational((clamped * 1000).toInt(), 1000))
    }
    return builder.build()
}

/** A connection to the playback service, null until it is ready. Released when the screen leaves. */
@Composable
fun rememberPlaybackController(): State<MediaController?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        var gone = false
        future.addListener({
            if (!gone) state.value = try { future.get() } catch (_: Exception) { null }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            gone = true
            state.value = null
            MediaController.releaseFuture(future)
        }
    }
    return state
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

