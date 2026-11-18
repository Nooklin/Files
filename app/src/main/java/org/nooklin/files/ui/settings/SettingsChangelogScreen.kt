package org.nooklin.files.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nooklin.files.R

// Newest first. One text per version, one line per change.
private val versions = listOf(
    R.string.changelog_100_title to R.string.changelog_100,
    R.string.changelog_0019_title to R.string.changelog_0019,
    R.string.changelog_0018_title to R.string.changelog_0018,
    R.string.changelog_0017_title to R.string.changelog_0017,
    R.string.changelog_0016_title to R.string.changelog_0016,
    R.string.changelog_0015_title to R.string.changelog_0015,
    R.string.changelog_0014_title to R.string.changelog_0014,
    R.string.changelog_0013_title to R.string.changelog_0013,
    R.string.changelog_0012_title to R.string.changelog_0012,
    R.string.changelog_0011_title to R.string.changelog_0011,
    R.string.changelog_0010_title to R.string.changelog_0010,
    R.string.changelog_009_title to R.string.changelog_009,
    R.string.changelog_008_title to R.string.changelog_008,
    R.string.changelog_007_title to R.string.changelog_007,
    R.string.changelog_006_title to R.string.changelog_006,
    R.string.changelog_005_title to R.string.changelog_005,
    R.string.changelog_004_title to R.string.changelog_004,
    R.string.changelog_003_title to R.string.changelog_003,
    R.string.changelog_002_title to R.string.changelog_002,
    R.string.changelog_001_title to R.string.changelog_001
)

@Composable
fun SettingsChangelogScreen(bottomPadding: Dp, onBack: () -> Unit) {
    SettingsPageScaffold(stringResource(R.string.about_changelog), bottomPadding, onBack) {
        versions.forEach { (title, text) ->
            SettingsGroup(
                stringResource(title),
                listOf({
                    Text(stringResource(text), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                })
            )
        }
    }
}
