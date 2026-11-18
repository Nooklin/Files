package org.nooklin.files.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nooklin.files.R

// Each topic is a title and a text. Only things the app does today are described here.
private val topics = listOf(
    R.string.howto_browse_title to R.string.howto_browse_text,
    R.string.howto_views_title to R.string.howto_views_text,
    R.string.howto_select_title to R.string.howto_select_text,
    R.string.howto_copy_title to R.string.howto_copy_text,
    R.string.howto_pane_title to R.string.howto_pane_text,
    R.string.howto_zip_title to R.string.howto_zip_text,
    R.string.howto_editor_title to R.string.howto_editor_text,
    R.string.howto_search_title to R.string.howto_search_text,
    R.string.howto_favorites_title to R.string.howto_favorites_text,
    R.string.howto_nextcloud_title to R.string.howto_nextcloud_text,
    R.string.howto_media_title to R.string.howto_media_text,
    R.string.howto_apps_title to R.string.howto_apps_text
)

@Composable
fun SettingsHowToScreen(bottomPadding: Dp, onBack: () -> Unit) {
    SettingsPageScaffold(stringResource(R.string.help_how_to), bottomPadding, onBack) {
        topics.forEach { (title, text) ->
            SettingsGroup(
                stringResource(title),
                listOf({
                    Text(stringResource(text), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                })
            )
        }
    }
}
