package org.nooklin.files.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.nooklin.files.R
import org.nooklin.files.ui.common.openFile
import java.io.File

/** Opens the pdf and hands out pages. PdfRenderer shows one page at a time, so rendering is one at a time too. */
private class PdfDoc(val renderer: PdfRenderer, private val descriptor: ParcelFileDescriptor) {
    private val lock = Mutex()
    val pageCount = renderer.pageCount

    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                // A page has no background of its own
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    suspend fun pageRatio(index: Int): Float = lock.withLock {
        withContext(Dispatchers.IO) { renderer.openPage(index).use { it.height.toFloat() / it.width } }
    }

    fun close() {
        try {
            renderer.close()
            descriptor.close()
        } catch (_: Exception) {
        }
    }
}

private sealed interface PdfState {
    data object Loading : PdfState
    data object Password : PdfState
    data object Failed : PdfState
    class Ready(val doc: PdfDoc) : PdfState
}

/** A pdf as a column of pages. Double tap switches between fitting the width and a larger view that scrolls sideways. */
@Composable
fun PdfViewer(path: String, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onClose() }
    val state by produceState<PdfState>(PdfState.Loading, path) {
        value = withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    PdfState.Ready(PdfDoc(PdfRenderer(pfd), pfd))
                } catch (e: SecurityException) {
                    pfd.close()
                    PdfState.Password
                } catch (e: Exception) {
                    pfd.close()
                    PdfState.Failed
                }
            } catch (e: Exception) {
                PdfState.Failed
            }
        }
    }
    (state as? PdfState.Ready)?.let { ready -> DisposableEffect(ready) { onDispose { ready.doc.close() } } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ViewerBar(
            title = File(path).name,
            onBack = onClose,
            tint = MaterialTheme.colorScheme.onBackground,
            actions = {
                IconButton(onClick = { openFile(context, path) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.viewer_open_other), tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        )
        when (val s = state) {
            PdfState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            PdfState.Password -> Message(stringResource(R.string.viewer_pdf_password))
            PdfState.Failed -> Message(stringResource(R.string.viewer_error))
            is PdfState.Ready -> Pages(s.doc)
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Pages(doc: PdfDoc) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    var zoomed by remember { mutableStateOf(false) }
    val pageWidthDp = if (zoomed) screenWidthDp * 2.5f else screenWidthDp.toFloat()
    val widthPx = with(density) { pageWidthDp.dp.roundToPx() }
    val list = rememberLazyListState()
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoomed = !zoomed }) }
    ) {
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(Modifier.width(pageWidthDp.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(List(doc.pageCount) { it }) { _, index ->
                    val bitmap by produceState<Bitmap?>(null, index, widthPx) { value = doc.render(index, widthPx) }
                    val shown = bitmap
                    if (shown != null) {
                        Image(shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                    } else {
                        // Keeps the room of the page while it is drawn so the scroll position does not jump
                        val ratio by produceState(1.4f, index) { value = doc.pageRatio(index) }
                        Box(Modifier.fillMaxWidth().height((pageWidthDp * ratio).dp).background(Color.White.copy(alpha = 0.6f)))
                    }
                }
            }
        }
        Text(
            "${list.firstVisibleItemIndex + 1} / ${doc.pageCount}",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
