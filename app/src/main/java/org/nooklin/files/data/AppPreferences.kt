package org.nooklin.files.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.nooklin.files.fs.SortKey
import org.nooklin.files.fs.SortSpec

private val Context.settingsDataStore by preferencesDataStore(name = "nooklin_files_settings")

enum class AppThemeMode { LIGHT, DARK, BLACK, SYSTEM }

// DEFAULT has no tint (null)
enum class ContrastColor(val storageKey: String, val label: String, val color: Color?) {
    DEFAULT("default", "Default", null),
    BLUE("blue", "Blue", Color(0xFF0A84FF)),
    RED("red", "Red", Color(0xFFFF3B30)),
    ORANGE("orange", "Orange", Color(0xFFFF9500)),
    YELLOW("yellow", "Yellow", Color(0xFFFFCC00)),
    GREEN("green", "Green", Color(0xFF34C759)),
    TEAL("teal", "Teal", Color(0xFF30B0C7)),
    PURPLE("purple", "Purple", Color(0xFFAF52DE)),
    PINK("pink", "Pink", Color(0xFFFF2D55));

    companion object {
        fun fromKey(key: String?): ContrastColor? = entries.find { it.storageKey == key }
    }
}

// Inter is the default. Helvetica has no free license to bundle
enum class AppFont(val storageKey: String, val label: String) {
    INTER("inter", "Inter"),
    SYSTEM_DEFAULT("system_default", "Stock Android Font"),
    WORK_SANS("work_sans", "Work Sans");

    companion object {
        fun fromKey(key: String?): AppFont? = entries.find { it.storageKey == key }
    }
}

/** How the files of a folder are shown. */
enum class FolderView { LIST, DETAILS, GRID, COMPACT_GRID }

data class PaneSaved(val path: String?, val sort: SortSpec, val scroll: Int)

private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
private val CONTRAST_COLOR_KEY = stringPreferencesKey("contrast_color")
private val APP_FONT_KEY = stringPreferencesKey("app_font")
private val SERVERS_KEY = stringPreferencesKey("servers")
private val FOLDER_VIEW_KEY = stringPreferencesKey("folder_view")
private val APP_LOCK_KEY = booleanPreferencesKey("app_lock")
private val DISABLE_SCREENSHOTS_KEY = booleanPreferencesKey("disable_screenshots")
private val PERMISSIONS_INTRO_KEY = booleanPreferencesKey("permissions_intro_done")
private val COLORFUL_KEY = booleanPreferencesKey("colorful")
private val BACKGROUND_PLAY_KEY = booleanPreferencesKey("background_play")
private val SHOW_HIDDEN_KEY = booleanPreferencesKey("show_hidden")
private val SHARE_ENABLED_KEY = booleanPreferencesKey("share_enabled")
private val DUAL_PANE_KEY = booleanPreferencesKey("dual_pane")
private val ASKED_NOTIFICATIONS_KEY = booleanPreferencesKey("asked_notifications")
private val BOOKMARKS_KEY = stringPreferencesKey("bookmarks")
private val NC_SERVER_KEY = stringPreferencesKey("nc_server")
private val NC_USER_KEY = stringPreferencesKey("nc_user")
// The password is stored encrypted, see SecretBox
private val NC_PASSWORD_KEY = stringPreferencesKey("nc_password")
private val ALLOW_HTTP_KEY = booleanPreferencesKey("allow_http")


private fun paneKey(index: Int, name: String) = "pane${index}_$name"

private fun parseList(stored: String?): List<String> {
    if (stored.isNullOrEmpty()) return emptyList()
    return try {
        val array = JSONArray(stored)
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

class AppPreferences(private val context: Context) {
    private val store get() = context.settingsDataStore

    val themeMode: Flow<AppThemeMode> = store.data.map { prefs ->
        AppThemeMode.entries.find { it.name == prefs[THEME_MODE_KEY] } ?: AppThemeMode.SYSTEM
    }
    val contrastColor: Flow<ContrastColor> = store.data.map { prefs ->
        ContrastColor.fromKey(prefs[CONTRAST_COLOR_KEY]) ?: ContrastColor.DEFAULT
    }
    // On until the user switches it off
    val colorful: Flow<Boolean> = store.data.map { prefs -> prefs[COLORFUL_KEY] ?: true }
    val appFont: Flow<AppFont> = store.data.map { prefs ->
        // No stored choice means Inter. Someone who picked Stock Android Font has that stored and keeps it
        AppFont.fromKey(prefs[APP_FONT_KEY]) ?: AppFont.INTER
    }
    // Hidden files and sharing are both off until the user switches them on
    val showHidden: Flow<Boolean> = store.data.map { it[SHOW_HIDDEN_KEY] ?: false }
    val shareEnabled: Flow<Boolean> = store.data.map { it[SHARE_ENABLED_KEY] ?: false }
    val folderView: Flow<FolderView> = store.data.map { prefs ->
        FolderView.entries.find { it.name == prefs[FOLDER_VIEW_KEY] } ?: FolderView.LIST
    }
    /** The saved FTP and SMB servers as JSON. The passwords inside are encrypted, see ServerStore. */
    val serversJson: Flow<String?> = store.data.map { it[SERVERS_KEY] }
    val appLock: Flow<Boolean> = store.data.map { it[APP_LOCK_KEY] ?: false }
    val disableScreenshots: Flow<Boolean> = store.data.map { it[DISABLE_SCREENSHOTS_KEY] ?: false }
    /** Video keeps playing (sound only) when the app is left. Music always does. */
    val backgroundPlay: Flow<Boolean> = store.data.map { it[BACKGROUND_PLAY_KEY] ?: false }
    /** True once the first-start explanation of the permissions has been shown and answered. */
    val permissionsIntroDone: Flow<Boolean> = store.data.map { it[PERMISSIONS_INTRO_KEY] ?: false }
    val dualPane: Flow<Boolean> = store.data.map { it[DUAL_PANE_KEY] ?: false }
    val bookmarks: Flow<List<String>> = store.data.map { parseList(it[BOOKMARKS_KEY]) }

    val nextcloudServer: Flow<String?> = store.data.map { it[NC_SERVER_KEY] }
    val nextcloudUser: Flow<String?> = store.data.map { it[NC_USER_KEY] }
    val nextcloudPassword: Flow<String?> = store.data.map { it[NC_PASSWORD_KEY] }
    // http (not encrypted) is refused until the user switches it on
    val allowHttp: Flow<Boolean> = store.data.map { it[ALLOW_HTTP_KEY] ?: false }

    suspend fun saveNextcloud(server: String, user: String, encryptedPassword: String) {
        store.edit {
            it[NC_SERVER_KEY] = server
            it[NC_USER_KEY] = user
            it[NC_PASSWORD_KEY] = encryptedPassword
        }
    }

    suspend fun clearNextcloud() {
        store.edit {
            it.remove(NC_SERVER_KEY)
            it.remove(NC_USER_KEY)
            it.remove(NC_PASSWORD_KEY)
        }
    }

    suspend fun setAllowHttp(value: Boolean) { store.edit { it[ALLOW_HTTP_KEY] = value } }

    suspend fun setThemeMode(mode: AppThemeMode) { store.edit { it[THEME_MODE_KEY] = mode.name } }
    suspend fun setColorful(value: Boolean) { store.edit { it[COLORFUL_KEY] = value } }
    suspend fun setContrastColor(color: ContrastColor) { store.edit { it[CONTRAST_COLOR_KEY] = color.storageKey } }
    suspend fun setAppFont(font: AppFont) { store.edit { it[APP_FONT_KEY] = font.storageKey } }
    suspend fun setShowHidden(value: Boolean) { store.edit { it[SHOW_HIDDEN_KEY] = value } }
    suspend fun setShareEnabled(value: Boolean) { store.edit { it[SHARE_ENABLED_KEY] = value } }
    suspend fun setFolderView(view: FolderView) { store.edit { it[FOLDER_VIEW_KEY] = view.name } }
    suspend fun setServersJson(value: String) { store.edit { it[SERVERS_KEY] = value } }
    suspend fun setAppLock(value: Boolean) { store.edit { it[APP_LOCK_KEY] = value } }
    suspend fun setDisableScreenshots(value: Boolean) { store.edit { it[DISABLE_SCREENSHOTS_KEY] = value } }
    suspend fun setBackgroundPlay(value: Boolean) { store.edit { it[BACKGROUND_PLAY_KEY] = value } }
    suspend fun setPermissionsIntroDone() { store.edit { it[PERMISSIONS_INTRO_KEY] = true } }
    suspend fun setDualPane(value: Boolean) { store.edit { it[DUAL_PANE_KEY] = value } }

    suspend fun askedNotifications(): Boolean = store.data.first()[ASKED_NOTIFICATIONS_KEY] ?: false
    suspend fun setAskedNotifications() { store.edit { it[ASKED_NOTIFICATIONS_KEY] = true } }

    suspend fun loadPane(index: Int): PaneSaved {
        val prefs = store.data.first()
        val sortKey = SortKey.entries.find { it.name == prefs[stringPreferencesKey(paneKey(index, "sort"))] } ?: SortKey.NAME
        val ascending = prefs[booleanPreferencesKey(paneKey(index, "asc"))] ?: true
        return PaneSaved(
            path = prefs[stringPreferencesKey(paneKey(index, "path"))],
            sort = SortSpec(sortKey, ascending),
            scroll = prefs[intPreferencesKey(paneKey(index, "scroll"))] ?: 0
        )
    }

    suspend fun savePane(index: Int, path: String, sort: SortSpec, scroll: Int) {
        store.edit {
            it[stringPreferencesKey(paneKey(index, "path"))] = path
            it[stringPreferencesKey(paneKey(index, "sort"))] = sort.key.name
            it[booleanPreferencesKey(paneKey(index, "asc"))] = sort.ascending
            it[intPreferencesKey(paneKey(index, "scroll"))] = scroll
        }
    }

    suspend fun toggleBookmark(path: String) {
        store.edit { prefs ->
            val list = parseList(prefs[BOOKMARKS_KEY]).toMutableList()
            if (!list.remove(path)) list.add(path)
            prefs[BOOKMARKS_KEY] = JSONArray(list).toString()
        }
    }

    suspend fun removeBookmark(path: String) {
        store.edit { prefs ->
            val list = parseList(prefs[BOOKMARKS_KEY]).filter { it != path }
            prefs[BOOKMARKS_KEY] = JSONArray(list).toString()
        }
    }
}
