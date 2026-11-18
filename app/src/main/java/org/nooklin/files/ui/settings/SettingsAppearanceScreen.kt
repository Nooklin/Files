package org.nooklin.files.ui.settings

import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import org.nooklin.files.ui.common.TileColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.files.R
import org.nooklin.files.data.AppFont
import org.nooklin.files.data.AppThemeMode
import org.nooklin.files.data.ContrastColor
import org.nooklin.files.ui.theme.fontFamilyFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearanceScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    contrastColor: ContrastColor,
    onContrastColorChange: (ContrastColor) -> Unit,
    appFont: AppFont,
    onAppFontChange: (AppFont) -> Unit,
    colorful: Boolean,
    onColorfulChange: (Boolean) -> Unit,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    bottomPadding: Dp,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val localeManager = remember { context.getSystemService(LocaleManager::class.java) }
    var languageTags by remember { mutableStateOf(localeManager.applicationLocales.toLanguageTags()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_appearance), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroup(
                stringResource(R.string.settings_theme),
                listOf({
                    SettingsChoiceRow(
                        stringResource(R.string.settings_theme),
                        listOf(
                            AppThemeMode.SYSTEM to stringResource(R.string.settings_follow_system),
                            AppThemeMode.LIGHT to stringResource(R.string.settings_light),
                            AppThemeMode.DARK to stringResource(R.string.settings_dark),
                            AppThemeMode.BLACK to stringResource(R.string.settings_black)
                        ),
                        themeMode,
                        onSelect = onThemeModeChange
                    )
                })
            )
            SettingsNote(stringResource(R.string.settings_black_explanation))

            SettingsGroup(
                stringResource(R.string.settings_colors),
                listOf({
                    SettingsToggleRow(
                        stringResource(R.string.settings_colorful),
                        colorful,
                        Icons.Filled.Palette,
                        TileColors.Appearance,
                        onCheckedChange = onColorfulChange
                    )
                })
            )
            SettingsNote(stringResource(R.string.settings_colorful_explanation))

            SettingsGroup(
                stringResource(R.string.settings_language),
                listOf({
                    val currentTag = when {
                        languageTags.isEmpty() -> ""
                        languageTags.startsWith("nl") -> "nl"
                        else -> "en"
                    }
                    SettingsChoiceRow(
                        stringResource(R.string.settings_language),
                        listOf(
                            "" to stringResource(R.string.settings_follow_system),
                            "en" to "English",
                            "nl" to "Nederlands"
                        ),
                        currentTag,
                        onSelect = { tag ->
                            languageTags = tag
                            localeManager.applicationLocales = LocaleList.forLanguageTags(tag)
                        }
                    )
                })
            )
            SettingsNote(stringResource(R.string.settings_language_explanation))

            SettingsGroup(
                stringResource(R.string.settings_font),
                listOf({
                    SettingsChoiceRow(
                        stringResource(R.string.settings_font),
                        listOf(
                            AppFont.INTER to AppFont.INTER.label,
                            AppFont.SYSTEM_DEFAULT to AppFont.SYSTEM_DEFAULT.label,
                            AppFont.WORK_SANS to AppFont.WORK_SANS.label
                        ),
                        appFont,
                        fontFor = { fontFamilyFor(it) },
                        onSelect = onAppFontChange
                    )
                })
            )

            SettingsGroup(
                stringResource(R.string.settings_files),
                listOf({
                    SettingsToggleRow(
                        stringResource(R.string.settings_show_hidden),
                        showHidden,
                        Icons.Filled.Visibility,
                        TileColors.Hidden,
                        onCheckedChange = onShowHiddenChange
                    )
                })
            )
            SettingsNote(stringResource(R.string.settings_hidden_explanation))

            SettingsGroup(
                stringResource(R.string.settings_contrast_color),
                listOf({
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(ContrastColor.entries, key = { it.storageKey }) { option ->
                            ContrastColorSwatch(
                                option = option,
                                selected = option == contrastColor,
                                onClick = { onContrastColorChange(option) }
                            )
                        }
                    }
                })
            )
            SettingsNote(stringResource(R.string.settings_contrast_explanation))
            Spacer(Modifier.height(bottomPadding))
        }
    }
}

// DEFAULT is an outlined circle, not a filled swatch: it means "no tint"
@Composable
private fun ContrastColorSwatch(option: ContrastColor, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (option.color != null) {
                    Modifier.background(option.color)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                }
            )
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (option.color != null) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
