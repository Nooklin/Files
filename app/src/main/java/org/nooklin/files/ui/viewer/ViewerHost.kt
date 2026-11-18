package org.nooklin.files.ui.viewer

import androidx.compose.runtime.Composable
import org.nooklin.files.fs.MediaKind
import org.nooklin.files.fs.MediaKinds
import org.nooklin.files.ui.ViewerRequest
import java.io.File

/** Picks the viewer for the file the user opened. */
@Composable
fun ViewerHost(
    request: ViewerRequest,
    backgroundPlay: Boolean,
    onBackgroundPlay: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val path = request.paths[request.index.coerceIn(0, request.paths.lastIndex)]
    when (MediaKinds.of(File(path))) {
        MediaKind.IMAGE -> ImageViewer(request.paths, request.index, onClose)
        MediaKind.VIDEO, MediaKind.AUDIO -> PlayerScreen(request.paths, request.index, backgroundPlay, onBackgroundPlay, onClose)
        MediaKind.PDF -> PdfViewer(path, onClose)
        MediaKind.DOCX, MediaKind.EPUB -> DocViewer(path, onClose)
        MediaKind.OTHER -> onClose()
    }
}
