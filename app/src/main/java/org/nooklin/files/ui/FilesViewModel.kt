package org.nooklin.files.ui

import android.app.Application
import android.content.Context
import android.os.storage.StorageManager
import android.os.Environment
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.files.FilesApplication
import org.nooklin.files.R
import org.nooklin.files.data.AppFont
import org.nooklin.files.data.AppThemeMode
import org.nooklin.files.data.ContrastColor
import org.nooklin.files.data.FolderView
import org.nooklin.files.fs.FileEngine
import org.nooklin.files.fs.FileItem
import org.nooklin.files.fs.FileLister
import org.nooklin.files.editor.TextFiles
import org.nooklin.files.fs.FileSearch
import org.nooklin.files.fs.MediaFile
import org.nooklin.files.fs.MediaGroup
import org.nooklin.files.fs.MediaKind
import org.nooklin.files.fs.MediaKinds
import org.nooklin.files.fs.MediaScan
import org.nooklin.files.fs.RecentFiles
import org.nooklin.files.fs.Resolution
import org.nooklin.files.fs.SortSpec
import org.nooklin.files.fs.TrashItem
import org.nooklin.files.fs.ZipEngine
import org.nooklin.files.ops.OpRequest
import org.nooklin.files.ops.Password
import org.nooklin.files.ui.common.openFile
import java.io.File
import java.io.IOException

data class PaneUi(
    val path: String = "",
    // Changes at every navigation, so a list view starts fresh for a new folder but not for a reload
    val navId: Int = 0,
    val sort: SortSpec = SortSpec(),
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = true,
    val unreadable: Boolean = false,
    val selected: Set<String> = emptySet(),
    // Select mode switched on from the menu: tapping selects, even with nothing selected yet
    val selecting: Boolean = false,
    val restoreScroll: Int = 0,
    val canGoBack: Boolean = false
)

/** A removable drive (SD card or USB drive) that is mounted. */
data class Volume(val path: String, val label: String)

/** What the built-in viewer shows: the files it can page through and the one to start with. */
data class ViewerRequest(val paths: List<String>, val index: Int)

data class MediaUi(val files: List<MediaFile> = emptyList(), val loading: Boolean = false)

data class RecentsUi(val items: List<FileItem> = emptyList(), val loading: Boolean = false)

/** What was copied or cut. remote is set when it comes from the Nextcloud server, then paths is empty. */
/** The password dialog is open for this: a zip to open, or files to pack. */
sealed interface PasswordRequest {
    data class Extract(val zipPath: String, val destDir: String) : PasswordRequest
    data class Compress(val sources: List<String>, val destDir: String) : PasswordRequest
}

data class Clipboard(val paths: List<String>, val move: Boolean, val remote: List<FileItem>? = null) {
    val count: Int get() = remote?.size ?: paths.size
}

data class SearchUi(
    val query: String = "",
    val results: List<FileItem> = emptyList(),
    val running: Boolean = false,
    val capped: Boolean = false
)

data class SettingsUi(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val contrastColor: ContrastColor = ContrastColor.DEFAULT,
    val appFont: AppFont = AppFont.INTER,
    val showHidden: Boolean = false,
    val shareEnabled: Boolean = false,
    val folderView: FolderView = FolderView.LIST,
    val disableScreenshots: Boolean = false,
    val colorful: Boolean = true
)

enum class NameProblem { EMPTY, INVALID, EXISTS }

private data class BackEntry(val path: String, val scroll: Int)

class FilesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FilesApplication
    private val prefs = app.prefs
    private val trash = app.trashStore
    val trashLimits get() = trash.limits
    val operations = app.operations

    val root: File = Environment.getExternalStorageDirectory()

    val panes: List<MutableStateFlow<PaneUi>> = listOf(MutableStateFlow(PaneUi()), MutableStateFlow(PaneUi()))
    private val backStacks = listOf(ArrayList<BackEntry>(), ArrayList<BackEntry>())
    private val scrollIndex = intArrayOf(0, 0)
    private val loadJobs = arrayOfNulls<Job>(2)

    private val _activePane = MutableStateFlow(0)
    val activePane: StateFlow<Int> = _activePane.asStateFlow()

    val dualPane: StateFlow<Boolean> = prefs.dualPane.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _clipboard = MutableStateFlow<Clipboard?>(null)
    val clipboard: StateFlow<Clipboard?> = _clipboard.asStateFlow()

    private val _search = MutableStateFlow<SearchUi?>(null)
    val search: StateFlow<SearchUi?> = _search.asStateFlow()
    private var searchJob: Job? = null

    private val _passwordRequest = MutableStateFlow<PasswordRequest?>(null)
    val passwordRequest: StateFlow<PasswordRequest?> = _passwordRequest.asStateFlow()

    // True while the second pane shows the Nextcloud server instead of a folder of the phone
    private val _secondRemote = MutableStateFlow(false)
    val secondRemote: StateFlow<Boolean> = _secondRemote.asStateFlow()

    fun setSecondRemote(value: Boolean) {
        if (value == _secondRemote.value) return
        _secondRemote.value = value
        if (value) {
            nc.clearSelection()
            nc.open(nc.remote.value.path)
        } else if (_activePane.value == 1) {
            // The active pane is the phone again, a selection on the server no longer belongs to anything
            nc.clearSelection()
        }
    }

    /** The Nextcloud account and everything on the server. */
    val nc = NextcloudController(app, viewModelScope)

    /** The saved FTP and SMB servers. */
    val servers = ServerController(app, viewModelScope)

    private val _volumes = MutableStateFlow<List<Volume>>(emptyList())
    val volumes: StateFlow<List<Volume>> = _volumes.asStateFlow()

    private val _recents = MutableStateFlow(RecentsUi())
    val recents: StateFlow<RecentsUi> = _recents.asStateFlow()
    private var recentsJob: Job? = null

    val bookmarks: StateFlow<List<String>> = prefs.bookmarks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<SettingsUi> = combine(
        prefs.themeMode, prefs.contrastColor, prefs.appFont, prefs.showHidden, prefs.shareEnabled,
        prefs.folderView, prefs.disableScreenshots, prefs.colorful
    ) { values ->
        SettingsUi(
            values[0] as AppThemeMode, values[1] as ContrastColor, values[2] as AppFont,
            values[3] as Boolean, values[4] as Boolean, values[5] as FolderView, values[6] as Boolean, values[7] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUi())

    // Null until the stored choice has loaded, so no lock screen flashes and no content shows before it
    val appLock: StateFlow<Boolean?> = prefs.appLock.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // True after one successful unlock, it lives as long as this process (one check per cold start)
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun unlock() { _unlocked.value = true }

    fun setAppLock(value: Boolean) {
        // Switching it on happens inside the open app, so no unlock is asked right after
        if (value) _unlocked.value = true
        viewModelScope.launch { prefs.setAppLock(value) }
    }

    fun setDisableScreenshots(value: Boolean) {
        viewModelScope.launch { prefs.setDisableScreenshots(value) }
    }

    fun setFolderView(view: FolderView) {
        viewModelScope.launch { prefs.setFolderView(view) }
    }

    private val _trashItems = MutableStateFlow<List<TrashItem>>(emptyList())
    val trashItems: StateFlow<List<TrashItem>> = _trashItems.asStateFlow()
    private val _trashSelected = MutableStateFlow<Set<String>>(emptySet())
    val trashSelected: StateFlow<Set<String>> = _trashSelected.asStateFlow()

    // A string resource id for a short message (shown as a toast)
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages.asSharedFlow()

    private var hidden = false
    private var started = false

    /** Called once the app has all files access: restores the panes and starts listening for changes. */
    fun start() {
        if (started) return
        started = true
        refreshVolumes()
        viewModelScope.launch(Dispatchers.IO) { nc.clearCache() }
        viewModelScope.launch {
            hidden = prefs.showHidden.first()
            for (i in 0..1) {
                val saved = prefs.loadPane(i)
                val existing = withContext(Dispatchers.IO) { saved.path?.let { nearestExistingDir(it) } }
                val path = existing ?: root.path
                setPath(i, path, if (path == saved.path) saved.scroll else 0, saved.sort)
            }
            launch {
                prefs.showHidden.drop(1).collect {
                    hidden = it
                    reloadAll()
                }
            }
            launch {
                operations.changes.drop(1).collect {
                    reloadAll()
                    refreshTrash()
                }
            }
            refreshTrash()
        }
    }

    private fun nearestExistingDir(path: String): String? {
        var file: File? = File(path)
        while (file != null && !file.isDirectory) file = file.parentFile
        return file?.path
    }

    // Navigation

    fun setActivePane(index: Int) {
        _activePane.value = index
    }

    fun open(index: Int, path: String) {
        val current = panes[index].value
        if (current.path.isNotEmpty() && current.path != path) {
            backStacks[index].add(BackEntry(current.path, scrollIndex[index]))
        }
        setPath(index, path, 0, current.sort)
    }

    /** Opening a folder from the Home screen starts a new trail, so Back from there returns to Home. */
    fun openFromHome(index: Int, path: String) {
        backStacks[index].clear()
        setPath(index, path, 0, panes[index].value.sort)
    }

    fun goBack(index: Int): Boolean {
        val stack = backStacks[index]
        if (stack.isEmpty()) return false
        val entry = stack.removeAt(stack.lastIndex)
        setPath(index, entry.path, entry.scroll, panes[index].value.sort)
        return true
    }

    fun canGoUp(index: Int): Boolean {
        val path = panes[index].value.path
        return path.isNotEmpty() && path != root.path && volumeFor(path)?.path != path && File(path).parentFile != null
    }

    // Removable drives

    /** Reads the mounted SD cards and USB drives. Called when Home opens and when a drive is attached or removed. */
    fun refreshVolumes() {
        val manager = getApplication<Application>().getSystemService(StorageManager::class.java)
        _volumes.value = manager.storageVolumes
            .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { volume ->
                val dir = volume.directory ?: return@mapNotNull null
                Volume(dir.path, volume.getDescription(getApplication()) ?: dir.name)
            }
    }

    fun volumeFor(path: String): Volume? =
        _volumes.value.firstOrNull { path == it.path || path.startsWith(it.path + "/") }

    /** Drives have no trash, items deleted there are deleted for good. */
    fun isOnDrive(path: String): Boolean = volumeFor(path) != null

    fun goUp(index: Int) {
        val parent = File(panes[index].value.path).parentFile ?: return
        open(index, parent.path)
    }

    private fun setPath(index: Int, path: String, scroll: Int, sort: SortSpec) {
        scrollIndex[index] = scroll
        panes[index].update {
            it.copy(
                path = path,
                navId = it.navId + 1,
                sort = sort,
                items = emptyList(),
                loading = true,
                unreadable = false,
                selected = emptySet(),
                selecting = false,
                restoreScroll = scroll,
                canGoBack = backStacks[index].isNotEmpty()
            )
        }
        persist(index)
        load(index)
    }

    fun reportScroll(index: Int, firstVisible: Int) {
        if (scrollIndex[index] == firstVisible) return
        scrollIndex[index] = firstVisible
        persist(index)
    }

    private fun persist(index: Int) {
        val state = panes[index].value
        if (state.path.isEmpty()) return
        val scroll = scrollIndex[index]
        viewModelScope.launch { prefs.savePane(index, state.path, state.sort, scroll) }
    }

    private fun load(index: Int) {
        loadJobs[index]?.cancel()
        loadJobs[index] = viewModelScope.launch {
            val requested = panes[index].value
            val result = withContext(Dispatchers.IO) {
                val nearest = nearestExistingDir(requested.path)
                if (nearest == null || nearest != requested.path) {
                    null to nearest
                } else {
                    FileLister.list(File(requested.path), hidden, requested.sort) to nearest
                }
            }
            val (items, nearest) = result
            if (nearest != null && nearest != requested.path) {
                // The folder is gone (deleted or moved away): go to the nearest folder that still exists
                setPath(index, nearest, 0, requested.sort)
                return@launch
            }
            panes[index].update { current ->
                if (current.path != requested.path) {
                    current
                } else {
                    val present = items.orEmpty().mapTo(HashSet()) { it.path }
                    current.copy(
                        items = items.orEmpty(),
                        unreadable = items == null,
                        loading = false,
                        selected = current.selected.filterTo(HashSet()) { it in present }
                    )
                }
            }
        }
    }

    private fun reloadAll() {
        for (i in 0..1) {
            if (panes[i].value.path.isNotEmpty()) load(i)
        }
    }

    fun setSort(index: Int, sort: SortSpec) {
        panes[index].update { it.copy(sort = sort) }
        persist(index)
        load(index)
    }

    fun toggleDualPane() {
        val next = !dualPane.value
        viewModelScope.launch { prefs.setDualPane(next) }
        if (!next) _activePane.value = 0
    }

    // Selection

    fun toggleSelect(index: Int, path: String) {
        panes[index].update {
            it.copy(selected = if (path in it.selected) it.selected - path else it.selected + path)
        }
    }

    fun startSelecting(index: Int) {
        panes[index].update { it.copy(selecting = true) }
    }

    fun clearSelection(index: Int) {
        panes[index].update { it.copy(selected = emptySet(), selecting = false) }
    }

    fun selectAll(index: Int) {
        panes[index].update { pane -> pane.copy(selected = pane.items.mapTo(HashSet()) { it.path }) }
    }

    /** The selected paths of a pane in list order. */
    fun selectedPaths(index: Int): List<String> {
        val pane = panes[index].value
        return pane.items.filter { it.path in pane.selected }.map { it.path }
    }

    // Clipboard and operations

    fun copySelection(index: Int, move: Boolean) {
        val paths = selectedPaths(index)
        if (paths.isEmpty()) return
        _clipboard.value = Clipboard(paths, move)
        clearSelection(index)
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    /** True when pane index shows the server. Only the second pane can. */
    fun isRemotePane(index: Int): Boolean = index == 1 && dualPane.value && _secondRemote.value

    /** Copy or cut the selection of the server pane. Pasted into a folder of the phone it is downloaded. */
    fun copyRemoteSelection(move: Boolean) {
        val items = nc.selectedItems()
        if (items.isEmpty()) return
        _clipboard.value = Clipboard(emptyList(), move, items)
        nc.clearSelection()
    }

    /** Paste into the Nextcloud folder that is open full screen. Only phone files can be pasted from the clipboard here. */
    fun pasteToNextcloud() {
        val clip = _clipboard.value ?: return
        val destPath = nc.remote.value.path
        val remoteSource = clip.remote
        if (remoteSource != null) nc.pasteWithin(remoteSource, clip.move, destPath) else nc.uploadPaths(clip.paths, clip.move, destPath)
        if (clip.move) _clipboard.value = null
    }

    /** Paste phone files into the FTP or SMB folder that is open. Items copied from Nextcloud pass through the phone. */
    fun pasteToServer() {
        val clip = _clipboard.value ?: return
        val remoteSource = clip.remote
        if (remoteSource != null) servers.pasteFromNextcloud(remoteSource, clip.move) else servers.uploadPaths(clip.paths, clip.move)
        if (clip.move) _clipboard.value = null
    }

    fun paste(index: Int) {
        val clip = _clipboard.value ?: return
        val remoteSource = clip.remote
        if (isRemotePane(index)) {
            val destPath = nc.remote.value.path
            if (remoteSource != null) nc.pasteWithin(remoteSource, clip.move, destPath) else nc.uploadPaths(clip.paths, clip.move, destPath)
        } else {
            val destDir = panes[index].value.path
            if (remoteSource != null) nc.downloadItems(remoteSource, clip.move, destDir)
            else operations.submit(OpRequest.Transfer(clip.move, clip.paths, destDir))
        }
        if (clip.move) _clipboard.value = null
    }

    fun transferToOtherPane(index: Int, move: Boolean) {
        val paths = selectedPaths(index)
        if (paths.isEmpty()) return
        if (isRemotePane(1 - index)) {
            nc.uploadPaths(paths, move, nc.remote.value.path)
        } else {
            operations.submit(OpRequest.Transfer(move, paths, panes[1 - index].value.path))
        }
        clearSelection(index)
    }

    /** The selection of the server pane goes to the folder shown in the first pane. */
    fun remoteToLocalPane(move: Boolean) {
        val items = nc.selectedItems()
        if (items.isEmpty()) return
        nc.downloadItems(items, move, panes[0].value.path)
        nc.clearSelection()
    }

    fun trashSelection(index: Int) {
        val paths = selectedPaths(index)
        if (paths.isEmpty()) return
        operations.submit(OpRequest.ToTrash(paths))
        clearSelection(index)
    }

    fun deleteSelectionPermanently(index: Int) {
        val paths = selectedPaths(index)
        if (paths.isEmpty()) return
        operations.submit(OpRequest.DeletePermanent(paths))
        clearSelection(index)
    }

    fun compressSelection(index: Int) {
        val paths = selectedPaths(index)
        if (paths.isEmpty()) return
        operations.submit(OpRequest.Compress(paths, panes[index].value.path))
        clearSelection(index)
    }

    /** Asks for a password first, then packs the selection into a zip with AES encryption. */
    fun compressSelectionWithPassword(index: Int) {
        val paths = selectedPaths(index)
        if (paths.isEmpty()) return
        _passwordRequest.value = PasswordRequest.Compress(paths, panes[index].value.path)
        clearSelection(index)
    }

    /** A zip with a password asks for it first, any other zip is extracted at once. */
    fun extract(index: Int, zipPath: String) {
        val destDir = panes[index].value.path
        clearSelection(index)
        viewModelScope.launch {
            val encrypted = withContext(Dispatchers.IO) { ZipEngine.isEncrypted(File(zipPath)) }
            if (encrypted) _passwordRequest.value = PasswordRequest.Extract(zipPath, destDir)
            else operations.submit(OpRequest.Extract(zipPath, destDir))
        }
    }

    /** The answer of the password dialog, null when it was cancelled. */
    fun answerPassword(password: String?) {
        val request = _passwordRequest.value
        _passwordRequest.value = null
        if (request == null || password == null) return
        when (request) {
            is PasswordRequest.Extract -> operations.submit(OpRequest.Extract(request.zipPath, request.destDir, Password(password)))
            is PasswordRequest.Compress -> operations.submit(OpRequest.Compress(request.sources, request.destDir, Password(password)))
        }
    }

    fun answerConflict(resolution: Resolution, applyToAll: Boolean) = operations.answerConflict(resolution, applyToAll)

    // New folder, new file, rename

    fun checkName(dir: String, name: String, ignorePath: String? = null): NameProblem? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return NameProblem.EMPTY
        if (trimmed == "." || trimmed == ".." || trimmed.contains('/') || trimmed.contains('\u0000')) return NameProblem.INVALID
        val target = File(dir, trimmed)
        if (target.exists() && target.path != ignorePath) return NameProblem.EXISTS
        return null
    }

    fun createFolder(index: Int, name: String) = runFileAction(R.string.msg_create_failed) {
        File(panes[index].value.path, name.trim()).mkdir()
    }

    fun createFile(index: Int, name: String) = runFileAction(R.string.msg_create_failed) {
        try {
            File(panes[index].value.path, name.trim()).createNewFile()
        } catch (e: IOException) {
            false
        }
    }

    fun rename(index: Int, path: String, newName: String) = runFileAction(R.string.msg_rename_failed) {
        val source = File(path)
        source.renameTo(File(source.parentFile, newName.trim()))
    }

    /** A copy next to the original, the name gets a number. */
    fun duplicate(index: Int, paths: List<String>) = runFileAction(R.string.msg_duplicate_failed) {
        var ok = true
        for (path in paths) {
            val src = File(path)
            val target = FileEngine.uniqueFile(src.parentFile ?: return@runFileAction false, src.name, src.isDirectory)
            try {
                if (src.isDirectory) {
                    if (!src.copyRecursively(target, overwrite = false)) ok = false
                } else {
                    src.copyTo(target, overwrite = false)
                }
            } catch (e: Exception) {
                ok = false
            }
        }
        ok
    }

    /** One name with a number for all items. Two steps, so a name that another selected item has now cannot clash. */
    fun batchRename(items: List<String>, names: List<String>) = runFileAction(R.string.msg_rename_failed) {
        val files = items.map { File(it) }
        val temps = files.map { FileEngine.tempFileFor(it.parentFile!!) }
        var ok = true
        val moved = ArrayList<Int>()
        for (i in files.indices) {
            if (files[i].renameTo(temps[i])) moved.add(i) else ok = false
        }
        for (i in moved) {
            val target = File(files[i].parentFile, names[i])
            if (!temps[i].renameTo(target)) {
                temps[i].renameTo(files[i])
                ok = false
            }
        }
        ok
    }

    private fun runFileAction(failureMessage: Int, action: () -> Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { action() }
            if (!ok) _messages.tryEmit(failureMessage)
            reloadAll()
            clearSelection(_activePane.value)
        }
    }

    // Favorites (stored as bookmarks)

    fun toggleBookmark(path: String) {
        viewModelScope.launch { prefs.toggleBookmark(path) }
    }

    fun removeBookmark(path: String) {
        viewModelScope.launch { prefs.removeBookmark(path) }
    }


    // Editor: which text file is open, if any
    private val _editorPath = MutableStateFlow<String?>(null)
    val editorPath: StateFlow<String?> = _editorPath.asStateFlow()

    /**
     * Images, video, music, pdf, docx and epub open in the built-in viewers, text files up to 1 MB in the editor,
     * everything else in the app Android picks. queue is the list the viewer pages through; without it that
     * is the files of the same kind in the folder of the file.
     */
    fun openFileOrEditor(context: Context, path: String, queue: List<String>? = null) {
        val file = File(path)
        // Another app asked this app for a file: tapping a file chooses it
        pickCallback?.let { pick ->
            if (!file.isDirectory) {
                pick(path)
                return
            }
        }
        val kind = MediaKinds.of(file)
        if (kind != MediaKind.OTHER) {
            viewModelScope.launch {
                val list = queue ?: withContext(Dispatchers.IO) {
                    if (kind == MediaKind.IMAGE || kind == MediaKind.VIDEO || kind == MediaKind.AUDIO) {
                        file.parentFile?.listFiles()?.filter { it.isFile && MediaKinds.of(it) == kind }
                            ?.sortedBy { it.name.lowercase() }?.map { it.path }
                    } else null
                } ?: listOf(path)
                val index = list.indexOf(path)
                _viewer.value = if (index >= 0) ViewerRequest(list, index) else ViewerRequest(listOf(path), 0)
            }
        } else if (TextFiles.canEdit(file)) {
            _editorPath.value = path
        } else {
            openFile(context, path)
        }
    }

    /** Set while another app has asked for a file (the picker). Null in the normal app. */
    var pickCallback: ((String) -> Unit)? = null

    // Viewer: which files the built-in viewer shows, if it is open
    private val _viewer = MutableStateFlow<ViewerRequest?>(null)
    val viewer: StateFlow<ViewerRequest?> = _viewer.asStateFlow()

    /** Opens the player screen for a queue that is already playing (from the mini player). */
    fun openPlayer(paths: List<String>, index: Int) {
        if (paths.isNotEmpty()) _viewer.value = ViewerRequest(paths, index.coerceIn(0, paths.lastIndex))
    }

    fun closeViewer() {
        _viewer.value = null
    }

    // First start: null until the stored value is read
    val permissionsIntroDone: StateFlow<Boolean?> = prefs.permissionsIntroDone.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setPermissionsIntroDone() {
        viewModelScope.launch { prefs.setPermissionsIntroDone() }
    }

    val backgroundPlay: StateFlow<Boolean> = prefs.backgroundPlay.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setBackgroundPlay(value: Boolean) {
        viewModelScope.launch { prefs.setBackgroundPlay(value) }
    }

    // Media screen: every video, image, music and document file on the phone, read from disk when the screen opens
    private val _media = MutableStateFlow(MediaUi())
    val media: StateFlow<MediaUi> = _media.asStateFlow()
    private var mediaJob: Job? = null

    fun refreshMedia() {
        mediaJob?.cancel()
        mediaJob = viewModelScope.launch {
            _media.update { it.copy(loading = true) }
            val files = withContext(Dispatchers.IO) {
                MediaScan.scan(listOf(root), hidden, setOf(File(root, "Android")))
            }
            _media.value = MediaUi(files, false)
        }
    }

    fun closeEditor() {
        _editorPath.value = null
    }

    // Recents: the newest files on the phone, read from disk when the tab opens
    fun refreshRecents() {
        recentsJob?.cancel()
        recentsJob = viewModelScope.launch {
            _recents.update { it.copy(loading = true) }
            val items = withContext(Dispatchers.IO) {
                RecentFiles.scan(root, hidden, setOf(File(root, "Android")))
            }
            _recents.value = RecentsUi(items, false)
        }
    }

    // Search in the folder of the active pane and everything below it

    fun startSearch() {
        _search.value = SearchUi()
    }

    fun stopSearch() {
        searchJob?.cancel()
        _search.value = null
    }

    /** rootPath: where to search, the folder of the active pane when null (the Home screen passes the phone). */
    fun setQuery(query: String, rootPath: String? = null) {
        _search.update { it?.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _search.update { it?.copy(results = emptyList(), running = false, capped = false) }
            return
        }
        val searchRoot = File(rootPath ?: panes[_activePane.value].value.path)
        searchJob = viewModelScope.launch {
            delay(300)
            _search.update { it?.copy(results = emptyList(), running = true, capped = false) }
            val found = ArrayList<FileItem>()
            var capped = false
            var lastPush = 0L
            withContext(Dispatchers.IO) {
                FileSearch.search(searchRoot, query, hidden) { item ->
                    found.add(item)
                    if (found.size >= FileSearch.MAX_RESULTS) {
                        capped = true
                        false
                    } else {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastPush > 250) {
                            lastPush = now
                            val snapshot = ArrayList(found)
                            _search.update { it?.copy(results = snapshot) }
                        }
                        true
                    }
                }
            }
            _search.update { it?.copy(results = ArrayList(found), running = false, capped = capped) }
        }
    }

    // Trash

    fun refreshTrash() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { trash.list() }
            _trashItems.value = items
            val ids = items.mapTo(HashSet()) { it.id }
            _trashSelected.update { selected -> selected.filterTo(HashSet()) { it in ids } }
        }
    }

    fun toggleTrashSelect(id: String) {
        _trashSelected.update { if (id in it) it - id else it + id }
    }

    fun clearTrashSelection() {
        _trashSelected.value = emptySet()
    }

    fun selectAllTrash() {
        _trashSelected.value = _trashItems.value.mapTo(HashSet()) { it.id }
    }

    private fun selectedTrashItems(): List<TrashItem> {
        val ids = _trashSelected.value
        return _trashItems.value.filter { it.id in ids }
    }

    fun restoreSelectedTrash() {
        val items = selectedTrashItems()
        if (items.isEmpty()) return
        operations.submit(OpRequest.Restore(items))
        clearTrashSelection()
    }

    fun deleteSelectedTrash() {
        val items = selectedTrashItems()
        if (items.isEmpty()) return
        operations.submit(OpRequest.DeleteTrashItems(items))
        clearTrashSelection()
    }

    fun emptyTrash() {
        operations.submit(OpRequest.EmptyTrash)
        clearTrashSelection()
    }

    // Settings

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setColorful(value: Boolean) {
        viewModelScope.launch { prefs.setColorful(value) }
    }

    fun setContrastColor(color: ContrastColor) {
        viewModelScope.launch { prefs.setContrastColor(color) }
    }

    fun setAppFont(font: AppFont) {
        viewModelScope.launch { prefs.setAppFont(font) }
    }

    fun setShowHidden(value: Boolean) {
        viewModelScope.launch { prefs.setShowHidden(value) }
    }

    fun setShareEnabled(value: Boolean) {
        viewModelScope.launch { prefs.setShareEnabled(value) }
    }

    suspend fun askedNotifications(): Boolean = prefs.askedNotifications()

    fun markAskedNotifications() {
        viewModelScope.launch { prefs.setAskedNotifications() }
    }
}
