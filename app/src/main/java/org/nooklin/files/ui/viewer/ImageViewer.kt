package org.nooklin.files.ui.viewer

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nooklin.files.R
import org.nooklin.files.ui.common.openFile
import java.io.File

/** Pictures one by one: swipe for the next, pinch or double tap to zoom, tap to hide the bars. */
@Composable
fun ImageViewer(paths: List<String>, startIndex: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, paths.lastIndex)) { paths.size }
    var zoomed by remember { mutableStateOf(false) }
    var bars by remember { mutableStateOf(true) }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed, key = { paths[it] }) { page ->
            ZoomableImage(paths[page], onZoomChanged = { if (pager.currentPage == page) zoomed = it }, onTap = { bars = !bars })
        }
        if (bars) {
            val path = paths[pager.currentPage]
            ViewerBar(
                title = File(path).name,
                subtitle = if (paths.size > 1) "${pager.currentPage + 1} / ${paths.size}" else null,
                onBack = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.55f)),
                actions = {
                    IconButton(onClick = { openFile(context, path) }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.viewer_open_other), tint = Color.White)
                    }
                }
            )
        }
    }
}

private sealed interface Loaded {
    data object Loading : Loaded
    data object Failed : Loaded
    class Ok(val bitmap: Bitmap) : Loaded
}

@Composable
private fun ZoomableImage(path: String, onZoomChanged: (Boolean) -> Unit, onTap: () -> Unit) {
    val loaded by produceState<Loaded>(Loaded.Loading, path) {
        value = withContext(Dispatchers.IO) {
            try {
                // ImageDecoder turns the picture the right way up and reads large pictures at a lower resolution
                val source = ImageDecoder.createSource(File(path))
                Loaded.Ok(ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    var sample = 1
                    while (maxOf(info.size.width, info.size.height) / sample > 2560) sample *= 2
                    decoder.setTargetSampleSize(sample)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                })
            } catch (e: Exception) {
                Loaded.Failed
            } catch (e: OutOfMemoryError) {
                Loaded.Failed
            }
        }
    }
    when (val state = loaded) {
        Loaded.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        Loaded.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.viewer_error), color = Color.White)
        }
        is Loaded.Ok -> {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            var size by remember { mutableStateOf(IntSize.Zero) }
            fun clamp(o: Offset, s: Float): Offset {
                val maxX = size.width * (s - 1f) / 2f
                val maxY = size.height * (s - 1f) / 2f
                return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { size = this.size }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = { tap ->
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                    // The tapped spot moves towards the middle
                                    offset = clamp(Offset((size.width / 2f - tap.x) * 1.5f, (size.height / 2f - tap.y) * 1.5f), scale)
                                }
                                onZoomChanged(scale > 1f)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val pressed = event.changes.count { it.pressed }
                                // One finger on a zoomed in picture moves it, two fingers zoom. One finger on a full picture belongs to the pager.
                                if (pressed > 1 || scale > 1f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    offset = if (scale <= 1f) Offset.Zero else clamp(offset + pan, scale)
                                    onZoomChanged(scale > 1f)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                )
            }
        }
    }
}
