package org.nooklin.files.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.nooklin.files.fs.MediaFile
import org.nooklin.files.fs.MediaGroup
import java.io.File

/** Small pictures for the media list: pictures, a frame of a video, the cover of a song, the first page of a pdf. */
object Thumbnails {
    // About 24 MB of thumbnails are kept, the oldest go first
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    // A few at a time, so scrolling fast does not start hundreds of decoders at once
    private val gate = Semaphore(4)

    fun cached(file: MediaFile): Bitmap? = cache.get(key(file))

    private fun key(file: MediaFile) = file.path + "|" + file.modified

    /** The thumbnail, or null when the file has none or cannot be read. */
    suspend fun load(file: MediaFile, px: Int): Bitmap? {
        cached(file)?.let { return it }
        val bitmap = gate.withPermit { withContext(Dispatchers.IO) { make(file, px) } }
        if (bitmap != null) cache.put(key(file), bitmap)
        return bitmap
    }

    private fun make(file: MediaFile, px: Int): Bitmap? = try {
        val f = File(file.path)
        when {
            file.group == MediaGroup.IMAGES -> ThumbnailUtils.createImageThumbnail(f, Size(px, px), null)
            file.group == MediaGroup.VIDEOS -> ThumbnailUtils.createVideoThumbnail(f, Size(px, px), null)
            file.group == MediaGroup.MUSIC -> cover(f, px)
            file.extension == "pdf" -> firstPage(f, px)
            else -> null
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun cover(file: File, px: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= px) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun firstPage(file: File, px: Int): Bitmap? {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val height = (px.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(px, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }
}
