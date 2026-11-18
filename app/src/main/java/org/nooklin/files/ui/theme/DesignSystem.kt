package org.nooklin.files.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import org.nooklin.files.data.AppFont
import org.nooklin.files.data.ContrastColor

// null color is DEFAULT: no tint, a real selectable option
data class ContrastColorState(val color: Color?, val isDarkTheme: Boolean, val isBlackMode: Boolean = false)

val LocalContrastColorState = staticCompositionLocalOf { ContrastColorState(null, false) }

/** Whether the icons on Home, in Settings and in Storage sit on a coloured tile (Appearance > Colorful display). */
val LocalColorful = staticCompositionLocalOf { true }

@Composable
fun NooklinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    contrastColor: ContrastColor = ContrastColor.DEFAULT,
    appFont: AppFont = AppFont.INTER,
    blackMode: Boolean = false,
    colorful: Boolean = true,
    content: @Composable () -> Unit
) {
    val baseColorScheme = neutralScheme(darkTheme)
    val accent = contrastColor.color ?: baseColorScheme.onSurface
    val colorScheme = baseColorScheme.withAccent(accent).let { scheme ->
        if (blackMode) {
            // Every surface role is black, so no grey panel shows. Only surfaceVariant (pills, fields) stays a very dark grey to read
            scheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceDim = Color.Black,
                surfaceBright = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color.Black,
                surfaceContainer = Color.Black,
                surfaceContainerHigh = Color.Black,
                surfaceContainerHighest = Color.Black,
                surfaceVariant = Color(0xFF1C1C1E)
            ).withAccent(accent)
        } else scheme
    }
    CompositionLocalProvider(
        LocalContrastColorState provides ContrastColorState(contrastColor.color, darkTheme, blackMode),
        LocalColorful provides colorful
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = typographyFor(appFont), content = content)
    }
}

// Material's own colours have a purple tint (surfaces, switch thumb, chips, outlines). Every role is set here,
// so the only colour in the app is the one the user chose (or none). Same as Nooklin Calendar.
private fun neutralScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        background = Color(0xFF121212), onBackground = Color(0xFFE3E3E3),
        surface = Color(0xFF121212), onSurface = Color(0xFFE3E3E3),
        surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFFC7C7CC),
        outline = Color(0xFF8E8E93), outlineVariant = Color(0xFF3A3A3C),
        surfaceDim = Color(0xFF121212), surfaceBright = Color(0xFF383838),
        surfaceContainerLowest = Color(0xFF0E0E0E), surfaceContainerLow = Color(0xFF1A1A1A),
        surfaceContainer = Color(0xFF1E1E1E), surfaceContainerHigh = Color(0xFF282828),
        surfaceContainerHighest = Color(0xFF333333),
        inverseSurface = Color(0xFFE3E3E3), inverseOnSurface = Color(0xFF1C1C1E)
    )
} else {
    lightColorScheme(
        background = Color(0xFFFFFFFF), onBackground = Color(0xFF1C1C1E),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFFE9E9EE), onSurfaceVariant = Color(0xFF48484A),
        outline = Color(0xFF8E8E93), outlineVariant = Color(0xFFC6C6C8),
        surfaceDim = Color(0xFFDADADA), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F7F7),
        surfaceContainer = Color(0xFFF2F2F2), surfaceContainerHigh = Color(0xFFECECEC),
        surfaceContainerHighest = Color(0xFFE6E6E6),
        inverseSurface = Color(0xFF1C1C1E), inverseOnSurface = Color(0xFFF2F2F2)
    )
}

/** All accent roles (primary, secondary, tertiary and their containers) follow accent; nothing is left on Material's purple. */
private fun ColorScheme.withAccent(accent: Color): ColorScheme {
    val on = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val container = lerp(surface, accent, 0.22f)
    return copy(
        primary = accent, onPrimary = on,
        primaryContainer = container, onPrimaryContainer = onSurface,
        secondary = accent, onSecondary = on,
        secondaryContainer = container, onSecondaryContainer = onSurface,
        tertiary = accent, onTertiary = on,
        tertiaryContainer = container, onTertiaryContainer = onSurface,
        inversePrimary = accent, surfaceTint = accent
    )
}
