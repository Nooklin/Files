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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.files.FilesApplication
import org.nooklin.files.R
import org.nooklin.files.appString
import org.nooklin.files.cloud.DavAccount
import org.nooklin.files.cloud.DavException
import org.nooklin.files.cloud.DavUrls
import org.nooklin.files.cloud.RemoteTrashItem
import org.nooklin.files.cloud.ServerCheck
import org.nooklin.files.fs.FileItem
import org.nooklin.files.net.SecretBox
import org.nooklin.files.ops.OpRequest
import org.nooklin.files.ops.OpType
import org.nooklin.files.ops.remoteErrorText
import org.nooklin.files.net.WebDavClient
import org.nooklin.files.ui.common.openFile
import java.io.File

enum class NcLink { UNKNOWN, CHECKING, ONLINE, OFFLINE }

/** Whether the server answers right now. error is the reason when it does not. */
data class NcStatus(val link: NcLink = NcLink.UNKNOWN, val error: String? = null)

data class NcConnectUi(val testing: Boolean = false, val error: String? = null)

data class RemoteUi(
    val path: String = "",
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val selected: Set<String> = emptySet(),
    val busy: String? = null,
    val selecting: Boolean = false
)

data class RemoteTrashUi(
    val items: List<RemoteTrashItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val selected: Set<String> = emptySet()
)

/** Everything about the Nextcloud account: connecting, browsing, file actions and the server trash. */
class NextcloudController(private val app: FilesApplication, private val scope: CoroutineScope) {
    private val prefs = app.prefs

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Short messages for a toast. */
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    val allowHttp: StateFlow<Boolean> = prefs.allowHttp.stateIn(scope, SharingStarted.Eagerly, false)

    /** The connected account, or null. The password is decrypted here, in memory only. */
    val account: StateFlow<DavAccount?> = combine(
        prefs.nextcloudServer, prefs.nextcloudUser, prefs.nextcloudPassword
    ) { server, user, encrypted ->
        if (server == null || user == null || encrypted == null) null
        else SecretBox.decrypt(encrypted)?.let { DavAccount(server, user, it) }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    private val _connect = MutableStateFlow(NcConnectUi())
    val connect: StateFlow<NcConnectUi> = _connect.asStateFlow()

    private val _status = MutableStateFlow(NcStatus())
    val status: StateFlow<NcStatus> = _status.asStateFlow()
    private var checkJob: Job? = null

    private val _remote = MutableStateFlow(RemoteUi())
    val remote: StateFlow<RemoteUi> = _remote.asStateFlow()

    private val _trash = MutableStateFlow(RemoteTrashUi())
    val trash: StateFlow<RemoteTrashUi> = _trash.asStateFlow()

    private var cachedClient: Pair<DavAccount, WebDavClient>? = null
    private var opened = false
    private var listJob: Job? = null
    private var trashJob: Job? = null
    private var actionJob: Job? = null

    init {
        app.operations.remoteStore = { client() }
        // After an upload or download the list of the server is read again
        scope.launch {
            app.operations.finished.collect { summary ->
                if (summary.type != OpType.UPLOAD && summary.type != OpType.DOWNLOAD) return@collect
                if (opened) load()
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
                if (summary.type == OpType.DOWNLOAD && summary.target == downloads && summary.errors.isEmpty() && !summary.cancelled) {
                    _notices.tryEmit(appString(R.string.nc_downloaded, itemsLabel(summary.itemCount)))
                }
            }
        }
    }

    fun setAllowHttp(value: Boolean) {
        scope.launch { prefs.setAllowHttp(value) }
    }

    // Account

    fun connect(serverInput: String, user: String, password: String) {
        if (_connect.value.testing) return
        scope.launch {
            _connect.value = NcConnectUi(testing = true)
            val error = try {
                tryConnect(serverInput, user.trim(), password)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorText(e)
            }
            _connect.value = NcConnectUi(false, error)
        }
    }

    /** Null when connected and saved, otherwise the text that tells the user what is wrong. */
    private suspend fun tryConnect(serverInput: String, user: String, password: String): String? {
        val server = when (val check = DavUrls.normalizeServer(serverInput, allowHttp.value)) {
            is ServerCheck.Ok -> check.server
            ServerCheck.Empty -> return appString(R.string.nc_error_server_empty)
            ServerCheck.Invalid -> return appString(R.string.nc_error_server_invalid)
            ServerCheck.HttpNotAllowed -> return appString(R.string.nc_error_http_off)
        }
        if (user.isEmpty() || password.isEmpty()) return appString(R.string.nc_error_login_empty)
        try {
            WebDavClient(DavAccount(server, user, password)).probe()
        } catch (e: DavException) {
            // A path behind the host is the usual reason for "not found", for example a pasted web address
            if (e.code == 404 && DavUrls.hasPath(server)) return appString(R.string.nc_error_not_found_path)
            throw e
        }
        val encrypted = try {
            withContext(Dispatchers.Default) { SecretBox.encrypt(password) }
        } catch (e: Exception) {
            return appString(R.string.nc_error_secure)
        }
        prefs.saveNextcloud(server, user, encrypted)
        return null
    }

    /** Asks the server for its root folder, to show whether the connection works now. */
    fun checkConnection() {
        checkJob?.cancel()
        val client = client()
        if (client == null) {
            _status.value = NcStatus()
            return
        }
        _status.value = NcStatus(NcLink.CHECKING)
        checkJob = scope.launch {
            _status.value = try {
                client.probe()
                NcStatus(NcLink.ONLINE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NcStatus(NcLink.OFFLINE, errorText(e))
            }
        }
    }

    fun resetStatus() {
        checkJob?.cancel()
        _status.value = NcStatus()
    }

    fun clearConnectError() {
        _connect.update { it.copy(error = null) }
    }

    fun disconnect() {
        scope.launch {
            actionJob?.cancel()
            listJob?.cancel()
            trashJob?.cancel()
            prefs.clearNextcloud()
            SecretBox.deleteKey()
            cachedClient = null
            _remote.value = RemoteUi()
            _trash.value = RemoteTrashUi()
            File(app.cacheDir, "remote").deleteRecursively()
        }
    }

    /** Removes files that were downloaded only to be opened. */
    fun clearCache() {
        File(app.cacheDir, "remote").deleteRecursively()
    }

    private fun client(): WebDavClient? {
        val account = account.value ?: return null
        if (account.server.startsWith("http://") && !allowHttp.value) {
            _notices.tryEmit(appString(R.string.nc_error_http_off))
            return null
        }
        cachedClient?.let { (cached, client) -> if (cached === account) return client }
        return WebDavClient(account).also { cachedClient = account to it }
    }

    private fun errorText(e: Exception): String = remoteErrorText(e)

    // Browsing

    fun open(path: String) {
        opened = true
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
        listJob = scope.launch {
            val client = client()
            if (client == null) {
                _remote.update { it.copy(loading = false, error = appString(R.string.nc_error_not_connected)) }
                return@launch
            }
            try {
                val items = client.list(path)
                    .sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
                    .map { FileItem(it.name, it.path, it.isDir, it.size, it.modified) }
                _remote.update { if (it.path == path) it.copy(items = items, loading = false, error = null) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _remote.update { if (it.path == path) it.copy(loading = false, error = errorText(e)) else it }
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

    private fun run(busy: String, after: () -> Unit, block: suspend (WebDavClient) -> Unit) {
        if (actionJob?.isActive == true) return
        val client = client() ?: return
        actionJob = scope.launch {
            _remote.update { it.copy(busy = busy) }
            try {
                block(client)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notices.tryEmit(errorText(e))
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
        run(appString(R.string.nc_busy_opening, item.name), ::noReload) { client ->
            val dir = File(app.cacheDir, "remote/" + Integer.toHexString(item.path.hashCode())).apply { mkdirs() }
            val dest = File(dir, item.name)
            client.download(item.path, dest) { }
            openFile(context, dest.path)
        }
    }

    /** Files and folders. They are saved in the Downloads folder of the phone, with the progress in a notification. */
    fun downloadSelected() {
        val items = selectedItems()
        if (items.isEmpty()) return
        val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        app.operations.submit(OpRequest.Download(false, items, folder.path))
        clearSelection()
    }

    /** The files from the document picker go to the open folder, with the progress in a notification. */
    fun upload(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        // The upload can run after the screen is closed, so ask to keep the permission for these files
        for (uri in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Not every provider offers it, the temporary permission still works while the app runs
            }
        }
        app.operations.submit(OpRequest.UploadDocuments(uris.map { it.toString() }, _remote.value.path))
    }

    /** Files and folders of the phone to a folder on the server. */
    fun uploadPaths(paths: List<String>, move: Boolean, destPath: String) {
        if (paths.isEmpty()) return
        app.operations.submit(OpRequest.Upload(move, paths, destPath))
    }

    /** Files and folders of the server to a folder on the phone. */
    fun downloadItems(items: List<FileItem>, move: Boolean, destDir: String) {
        if (items.isEmpty()) return
        app.operations.submit(OpRequest.Download(move, items, destDir))
    }

    /** Copy or move inside the server: the server does the work, nothing passes through the phone. */
    fun pasteWithin(items: List<FileItem>, move: Boolean, destPath: String) {
        if (items.isEmpty()) return
        run(appString(R.string.nc_busy_working), ::load) { client ->
            val taken = client.list(destPath).mapTo(HashSet()) { it.name }
            for (item in items) {
                if (item.isDir && (destPath == item.path || destPath.startsWith(item.path + "/"))) {
                    _notices.tryEmit(appString(R.string.error_into_itself))
                    continue
                }
                // A move to the folder it is already in changes nothing
                if (move && DavUrls.parentOf(item.path) == destPath) continue
                val name = DavUrls.uniqueName(item.name, taken)
                taken.add(name)
                val target = destPath.trimEnd('/') + "/" + name
                if (move) client.move(item.path, target) else client.copy(item.path, target)
            }
        }
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
        run(appString(R.string.nc_busy_deleting, itemsLabel(items.size)), ::load) { client ->
            for (item in items) client.delete(item.path)
        }
        clearSelection()
    }

    private fun noReload() {}

    private fun itemsLabel(count: Int): String =
        if (count == 1) appString(R.string.items_one) else appString(R.string.items_many, count)

    // The Nextcloud trash

    fun refreshTrash() {
        trashJob?.cancel()
        _trash.update { it.copy(loading = true, error = null) }
        trashJob = scope.launch {
            val client = client()
            if (client == null) {
                _trash.update { it.copy(loading = false, error = appString(R.string.nc_error_not_connected)) }
                return@launch
            }
            try {
                val items = client.listTrash().sortedByDescending { it.deletedAt }
                _trash.update { state ->
                    val ids = items.mapTo(HashSet()) { it.trashPath }
                    state.copy(items = items, loading = false, error = null, selected = state.selected.filterTo(HashSet()) { it in ids })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _trash.update { it.copy(loading = false, error = errorText(e)) }
            }
        }
    }

    fun toggleTrashSelect(trashPath: String) {
        _trash.update { it.copy(selected = if (trashPath in it.selected) it.selected - trashPath else it.selected + trashPath) }
    }

    fun clearTrashSelection() {
        _trash.update { it.copy(selected = emptySet()) }
    }

    fun selectAllTrash() {
        _trash.update { state -> state.copy(selected = state.items.mapTo(HashSet()) { it.trashPath }) }
    }

    private fun selectedTrash(): List<RemoteTrashItem> {
        val state = _trash.value
        return state.items.filter { it.trashPath in state.selected }
    }

    fun restoreSelectedTrash() {
        val items = selectedTrash()
        if (items.isEmpty()) return
        run(appString(R.string.nc_busy_working), ::refreshTrash) { client ->
            for (item in items) client.restore(item)
        }
        clearTrashSelection()
    }

    fun deleteSelectedTrash() {
        val items = selectedTrash()
        if (items.isEmpty()) return
        run(appString(R.string.nc_busy_deleting, itemsLabel(items.size)), ::refreshTrash) { client ->
            for (item in items) client.deleteFromTrash(item)
        }
        clearTrashSelection()
    }

    fun emptyTrash() {
        run(appString(R.string.nc_busy_working), ::refreshTrash) { it.emptyTrash() }
        clearTrashSelection()
    }
}
