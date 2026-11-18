package org.nooklin.files.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nooklin.files.R
import org.nooklin.files.ui.theme.LocalColorful

/** The blue of the Nextcloud logo. */
val NextcloudBlue = Color(0xFF0082C9)

/** The colors of the icon tiles. Fixed colors, so each place keeps its own color in every theme. */
object TileColors {
    // The six tile colors of Nooklin Photos and Calendar, one color per kind of place
    private val Blue = Color(0xFF0A84FF)
    private val Green = Color(0xFF30D158)
    private val Orange = Color(0xFFFF9F0A)
    private val Red = Color(0xFFFF453A)
    private val Purple = Color(0xFFBF5AF2)
    private val Gray = Color(0xFF8E8E93)

    val Phone = Blue
    val Drive = Orange
    val Trash = Red
    val Appearance = Orange
    val Hidden = Purple
    val Share = Green
    val About = Gray
    val Warning = Orange
    val Privacy = Red
    val Screenshots = Red
    val Help = Green
    val Ftp = Orange
    val Smb = Blue
    val Apps = Green
    val Storage = Blue
    val Media = Purple
}

/** A rounded square in a color with a white icon on it, like the icons in the iOS settings. */
@Composable
fun IconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    if (!LocalColorful.current) {
        // Colorful display is off: the plain icon, same space
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.75f))
        }
        return
    }
    Box(
        modifier
            .size(size)
            .background(color, RoundedCornerShape(size * 0.27f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

/** The three white rings of Nextcloud on the Nextcloud blue. The rings come from the logo file the owner supplied. */
@Composable
fun NextcloudTile(modifier: Modifier = Modifier, size: Dp = 30.dp) {
    if (!LocalColorful.current) {
        IconTile(Icons.Filled.Cloud, NextcloudBlue, modifier, size)
        return
    }
    Box(
        modifier
            .size(size)
            .background(NextcloudBlue, RoundedCornerShape(size * 0.27f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.nextcloud_rings),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = size * 0.17f)
        )
    }
}
