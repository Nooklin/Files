package org.nooklin.files.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.files.FilesApplication
import org.nooklin.files.R
import org.nooklin.files.appString
import org.nooklin.files.cloud.DavUrls
import org.nooklin.files.cloud.RemoteStore
import org.nooklin.files.cloud.SavedServer
import org.nooklin.files.cloud.ServerJson
import org.nooklin.files.cloud.ServerType
import org.nooklin.files.fs.FileItem
import org.nooklin.files.net.FtpStore
import org.nooklin.files.net.SecretBox
import org.nooklin.files.net.SmbStore
import org.nooklin.files.ops.OpRequest
import org.nooklin.files.ops.OpType
import org.nooklin.files.ops.remoteErrorText
import org.nooklin.files.ui.common.openFile
import java.io.File
import java.util.UUID

/** The result of trying a server from the form: null while nothing was tried, otherwise the reason or success. */
data class ServerTestUi(val testing: Boolean = false, val error: String? = null, val ok: Boolean = false)

/** The saved FTP and SMB servers, and browsing the one that is open. Looks like the Nextcloud controller, without a trash. */
class ServerController(private val app: FilesApplication, private val scope: CoroutineScope) {
    private val prefs = app.prefs

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    val servers: StateFlow<List<SavedServer>> = prefs.serversJson
        .map { json -> ServerJson.read(json) { SecretBox.decrypt(it, SecretBox.ALIAS_SERVERS) } }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _current = MutableStateFlow<SavedServer?>(null)
    val current: StateFlow<SavedServer?> = _current.asStateFlow()

    private val _remote = MutableStateFlow(RemoteUi())
    val remote: StateFlow<RemoteUi> = _remote.asStateFlow()

    private val _test = MutableStateFlow(ServerTestUi())
    val test: StateFlow<ServerTestUi> = _test.asStateFlow()

    private var browseStore: RemoteStore? = null
    private var transferStore: RemoteStore? = null
    private var listJob: Job? = null
    private var actionJob: Job? = null
    private var testJob: Job? = null

    init {
        // After an upload or download the list is read again
        scope.launch {
            app.operations.finished.collect { summary ->
                if (summary.type != OpType.UPLOAD && summary.type != OpType.DOWNLOAD) return@collect
                if (_current.value != null) load()
            }
        }
    }

    private fun newStore(server: SavedServer): RemoteStore = when (server.type) {
        ServerType.FTP -> FtpStore(server.host, server.port, server.user, server.password, scope)
        ServerType.SMB -> SmbStore(server.host, server.port, server.share, server.user, server.password, scope)
    }

    private fun disconnect(store: RemoteStore?) {
        when (store) {
            is FtpStore -> store.disconnect()
            is SmbStore -> store.disconnect()
        }
    }

    // Saved servers

    private suspend fun write(list: List<SavedServer>) {
        val json = withContext(Dispatchers.Default) { ServerJson.write(list) { SecretBox.encrypt(it, SecretBox.ALIAS_SERVERS) } }
        prefs.setServersJson(json)
    }

    /** Saves a new server (id empty) or replaces the one with this id. */
    fun save(server: SavedServer) {
        scope.launch {
            val list = servers.value
            val saved = if (server.id.isEmpty()) server.copy(id = UUID.randomUUID().toString()) else server
            val next = if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it } else list + saved
            try {
                write(next)
            } catch (e: Exception) {
                _notices.tryEmit(appString(R.string.nc_error_secure))
                return@launch
            }
            if (_current.value?.id == saved.id) closeServer()
        }
    }

    fun delete(id: String) {
        scope.launch {
            if (_current.value?.id == id) closeServer()
            try {
                write(servers.value.filter { it.id != id })
            } catch (e: Exception) {
                _notices.tryEmit(appString(R.string.nc_error_secure))
            }
        }
    }

    /** Tries the details from the form: connects, logs in and lists the root. */
    fun testConnection(server: SavedServer) {
        testJob?.cancel()
        _test.value = ServerTestUi(testing = true)
        val store = newStore(server)
        testJob = scope.launch {
            _test.value = try {
                when (store) {
                    is FtpStore -> store.probe()
                    is SmbStore -> store.probe()
                }
                ServerTestUi(ok = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ServerTestUi(error = remoteErrorText(e))
            } finally {
                disconnect(store)
            }
        }
    }

    fun clearTest() {
        testJob?.cancel()
        _test.value = ServerTestUi()
    }

    // Browsing

    fun openServer(id: String) {
        val server = servers.value.firstOrNull { it.id == id } ?: return
        if (_current.value?.id != id) {
            closeServer()
            _current.value = server
            browseStore = newStore(server)
            transferStore = newStore(server)
        }
        open("")
    }

    /** Leaves the server: the connections close and the list is forgotten. */
    fun closeServer() {
        listJob?.cancel()
        actionJob?.cancel()
        disconnect(browseStore)
        // A transfer that is still running keeps its own connection until it is done, then it closes after being idle
        browseStore = null
        transferStore = null
        _current.value = null
        _remote.value = RemoteUi()
    }

    fun open(path: String) {
        _remote.update { it.copy(path = path, items = emptyList(), loading = true, error = null, selected = emptySet(), selecting = false) }
        load()
    }

    fun refresh() {
        _remote.update { it.copy(loading = true, error = null) }
        load()
    }

    private fun load() {
        listJob?.cancel()
        val path = _remote.value.path
        val store = browseStore ?: return
        listJob = scope.launch {
            try {
                val items = store.list(path)
                    .sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
                    .map { FileItem(it.name, it.path, it.isDir, it.size, it.modified) }
                _remote.update { if (it.path == path) it.copy(items = items, loading = false, error = null) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _remote.update { if (it.path == path) it.copy(loading = false, error = remoteErrorText(e)) else it }
            }
        }
    }

    fun canGoUp(): Boolean = _remote.value.path.isNotEmpty()

    fun goUp() {
        open(DavUrls.parentOf(_remote.value.path))
    }

    fun toggleSelect(path: String) {
        _remote.update { it.copy(selected = if (path in it.selected) it.selected - path else it.selected + path) }
    }

    fun startSelecting() {
        _remote.update { it.copy(selecting = true) }
    }

    fun clearSelection() {
        _remote.update { it.copy(selected = emptySet(), selecting = false) }
    }

    fun selectAll() {
        _remote.update { state -> state.copy(selected = state.items.mapTo(HashSet()) { it.path }) }
    }

    fun selectedItems(): List<FileItem> {
        val state = _remote.value
        return state.items.filter { it.path in state.selected }
    }

    fun checkName(name: String, ignorePath: String? = null): NameProblem? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return NameProblem.EMPTY
        if (trimmed == "." || trimmed == ".." || trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains('\u0000')) {
            return NameProblem.INVALID
        }
        val clash = _remote.value.items.any { it.name == trimmed && it.path != ignorePath }
        return if (clash) NameProblem.EXISTS else null
    }

    // Actions. One at a time, the busy text with a Cancel button shows while one runs.

    private fun run(busy: String, after: () -> Unit, block: suspend (RemoteStore) -> Unit) {
        if (actionJob?.isActive == true) return
        val store = browseStore ?: return
        actionJob = scope.launch {
            _remote.update { it.copy(busy = busy) }
            try {
                block(store)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notices.tryEmit(remoteErrorText(e))
            } finally {
                _remote.update { it.copy(busy = null) }
                after()
            }
        }
    }

    fun cancelAction() {
        actionJob?.cancel()
    }

    /** Downloads the file to the cache and hands it to the app Android picks. */
    fun openRemoteFile(context: Context, item: FileItem) {
        run(appString(R.string.nc_busy_opening, item.name), {}) { store ->
            val dir = File(app.cacheDir, "remote/" + Integer.toHexString((_current.value?.id + item.path).hashCode())).apply { mkdirs() }
            val dest = File(dir, item.name)
            store.download(item.path, dest) { }
            openFile(context, dest.path)
        }
    }

    /** Files and folders are saved in the Downloads folder of the phone, with the progress in a notification. */
    fun downloadSelected() {
        val items = selectedItems()
        val store = transferStore ?: return
        if (items.isEmpty()) return
        val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        app.operations.submit(OpRequest.Download(false, items, folder.path, store))
        clearSelection()
    }

    /** The files from the document picker go to the open folder, with the progress in a notification. */
    fun upload(context: Context, uris: List<Uri>) {
        val store = transferStore ?: return
        if (uris.isEmpty()) return
        for (uri in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Not every provider offers it, the temporary permission still works while the app runs
            }
        }
        app.operations.submit(OpRequest.UploadDocuments(uris.map { it.toString() }, _remote.value.path, store))
    }

    /** Files and folders of the phone (the clipboard) to the open server folder; a cut removes them afterwards. */
    fun uploadPaths(paths: List<String>, move: Boolean) {
        val store = transferStore ?: return
        if (paths.isEmpty()) return
        app.operations.submit(OpRequest.Upload(move, paths, _remote.value.path, store))
    }

    /** Items copied from Nextcloud to the open server folder. They pass through the phone. */
    fun pasteFromNextcloud(items: List<FileItem>, move: Boolean) {
        val store = transferStore ?: return
        if (items.isEmpty()) return
        app.operations.submit(OpRequest.RemoteToRemote(items, move, _remote.value.path, store))
    }

    fun makeFolder(name: String) {
        val path = _remote.value.path + "/" + name.trim()
        run(appString(R.string.nc_busy_working), ::load) { it.makeFolder(path) }
    }

    fun rename(path: String, newName: String) {
        val target = DavUrls.parentOf(path) + "/" + newName.trim()
        run(appString(R.string.nc_busy_working), ::load) { it.move(path, target) }
        clearSelection()
    }

    fun deleteSelected() {
        val items = selectedItems()
        if (items.isEmpty()) return
        run(appString(R.string.nc_busy_deleting, if (items.size == 1) appString(R.string.items_one) else appString(R.string.items_many, items.size)), ::load) { store ->
            for (item in items) store.delete(item.path)
        }
        clearSelection()
    }
}
