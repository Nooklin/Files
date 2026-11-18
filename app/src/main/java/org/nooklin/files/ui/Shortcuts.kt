package org.nooklin.files.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.nooklin.files.MainActivity
import org.nooklin.files.R
import org.nooklin.files.ui.common.NextcloudBlue
import org.nooklin.files.ui.common.TileColors

/** Which place a launcher shortcut opens. The name travels as an extra in the intent. */
enum class ShortcutTarget(val id: String) {
    RECENTS("recents"), APPS("apps"), NEXTCLOUD("nextcloud"), FTP("ftp"), SMB("smb");

    companion object {
        const val EXTRA = "org.nooklin.files.SHORTCUT"
        fun from(id: String?): ShortcutTarget? = entries.find { it.id == id }
    }
}

private const val BITMAP_SIZE = 216

/**
 * Puts the options on the app icon (long press on the launcher): Recent Files and App Manager always, Nextcloud,
 * FTP and SMB only when one is set up. Called again whenever that changes.
 */
@Composable
fun ShortcutsEffect(hasNextcloud: Boolean, hasFtp: Boolean, hasSmb: Boolean) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val recents = rememberVectorPainter(Icons.Rounded.History)
    val apps = rememberVectorPainter(Icons.Rounded.Android)
    val ftp = rememberVectorPainter(Icons.Rounded.Dns)
    val smb = rememberVectorPainter(Icons.Rounded.Lan)
    LaunchedEffect(hasNextcloud, hasFtp, hasSmb) {
        val list = ArrayList<ShortcutInfoCompat>()
        fun add(target: ShortcutTarget, label: String, icon: IconCompat) {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(ShortcutTarget.EXTRA, target.id)
            list.add(
                ShortcutInfoCompat.Builder(context, target.id)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(icon)
                    .setIntent(intent)
                    .setRank(list.size)
                    .build()
            )
        }
        add(ShortcutTarget.RECENTS, context.getString(R.string.shortcut_recents), vectorIcon(density, recents, TileColors.Phone))
        add(ShortcutTarget.APPS, context.getString(R.string.apps_title), vectorIcon(density, apps, TileColors.Apps))
        if (hasNextcloud) add(ShortcutTarget.NEXTCLOUD, context.getString(R.string.location_nextcloud), nextcloudIcon(context))
        if (hasFtp) add(ShortcutTarget.FTP, context.getString(R.string.shortcut_ftp), vectorIcon(density, ftp, TileColors.Ftp))
        if (hasSmb) add(ShortcutTarget.SMB, context.getString(R.string.shortcut_smb), vectorIcon(density, smb, TileColors.Smb))
        try {
            // Replaces the earlier list, so a server that was removed loses its shortcut
            ShortcutManagerCompat.setDynamicShortcuts(context, list)
        } catch (_: Exception) {
            // The launcher does not take shortcuts: the app works without them
        }
    }
}

/** A white icon in the middle of a colored square. The square is full size, the launcher cuts the shape. */
private fun vectorIcon(density: Density, painter: Painter, background: Color): IconCompat {
    val image = ImageBitmap(BITMAP_SIZE, BITMAP_SIZE)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    val scope = CanvasDrawScope()
    scope.draw(density, LayoutDirection.Ltr, canvas, Size(BITMAP_SIZE.toFloat(), BITMAP_SIZE.toFloat())) {
        drawRect(background)
        val iconSize = BITMAP_SIZE * 0.42f
        val offset = (BITMAP_SIZE - iconSize) / 2f
        translate(offset, offset) {
            with(painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(Color.White)) }
        }
    }
    return IconCompat.createWithAdaptiveBitmap(image.asAndroidBitmap())
}

/** The Nextcloud rings on the Nextcloud blue. */
private fun nextcloudIcon(context: Context): IconCompat {
    val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(NextcloudBlue.toArgb())
    val rings = BitmapFactory.decodeResource(context.resources, R.drawable.nextcloud_rings)
    if (rings != null) {
        val width = BITMAP_SIZE * 0.5f
        val height = width * rings.height / rings.width
        val box = RectF((BITMAP_SIZE - width) / 2f, (BITMAP_SIZE - height) / 2f, (BITMAP_SIZE + width) / 2f, (BITMAP_SIZE + height) / 2f)
        canvas.drawBitmap(rings, null, box, Paint(Paint.FILTER_BITMAP_FLAG))
    }
    return IconCompat.createWithAdaptiveBitmap(bitmap)
}

/** Asks the launcher to put a shortcut to this folder on the home screen. The launcher decides whether it is accepted. */
fun pinFolderShortcut(context: Context, path: String) {
    try {
        val dir = java.io.File(path)
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(android.net.Uri.fromFile(dir))
        val label = dir.name.ifEmpty { context.getString(R.string.location_phone) }
        val shortcut = ShortcutInfoCompat.Builder(context, "folder:$path")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, context.applicationInfo.icon))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    } catch (_: Exception) {
        // The launcher does not take pinned shortcuts
    }
}
