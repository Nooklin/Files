package org.nooklin.files.ops

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.nooklin.files.R
import org.nooklin.files.appString
import org.nooklin.files.cloud.DavUrls
import org.nooklin.files.cloud.RemoteStore
import org.nooklin.files.cloud.RemoteTransfer
import org.nooklin.files.fs.Conflict
import org.nooklin.files.fs.ErrorKind
import org.nooklin.files.fs.FileEngine
import org.nooklin.files.fs.FileItem
import org.nooklin.files.fs.OpContext
import org.nooklin.files.fs.OpError
import org.nooklin.files.fs.Resolution
import org.nooklin.files.fs.TrashItem
import org.nooklin.files.fs.TrashStore
import org.nooklin.files.fs.ZipEngine
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

enum class OpType { COPY, MOVE, TRASH, DELETE, RESTORE, EMPTY_TRASH, DELETE_TRASH_ITEMS, EXTRACT, COMPRESS, UPLOAD, DOWNLOAD }

/** A password for a zip. toString hides it so it never ends up in a log. */
class Password(val value: String) {
    override fun toString() = "***"
}

sealed interface OpRequest {
    val type: OpType
    val itemCount: Int

    data class Transfer(val move: Boolean, val sources: List<String>, val destDir: String) : OpRequest {
        override val type get() = if (move) OpType.MOVE else OpType.COPY
        override val itemCount get() = sources.size
    }

    data class ToTrash(val sources: List<String>) : OpRequest {
        override val type get() = OpType.TRASH
        override val itemCount get() = sources.size
    }

    data class DeletePermanent(val sources: List<String>) : OpRequest {
        override val type get() = OpType.DELETE
        override val itemCount get() = sources.size
    }

    data class Restore(val items: List<TrashItem>) : OpRequest {
        override val type get() = OpType.RESTORE
        override val itemCount get() = items.size
    }

    data class DeleteTrashItems(val items: List<TrashItem>) : OpRequest {
        override val type get() = OpType.DELETE_TRASH_ITEMS
        override val itemCount get() = items.size
    }

    data object EmptyTrash : OpRequest {
        override val type get() = OpType.EMPTY_TRASH
        override val itemCount get() = 0
    }

    data class Extract(val zipPath: String, val destDir: String, val password: Password? = null) : OpRequest {
        override val type get() = OpType.EXTRACT
        override val itemCount get() = 1
    }

    data class Compress(val sources: List<String>, val destDir: String, val password: Password? = null) : OpRequest {
        override val type get() = OpType.COMPRESS
        override val itemCount get() = sources.size
    }

    /** Files and folders of the phone to a folder on the server. move deletes them from the phone afterwards. */
    data class Upload(val move: Boolean, val sources: List<String>, val destPath: String, val store: RemoteStore? = null) : OpRequest {
        override val type get() = OpType.UPLOAD
        override val itemCount get() = sources.size
    }

    /**
     * Items of the Nextcloud server (source null) to a folder of another server (FTP or SMB). They pass through a temporary
     * folder on the phone. move deletes the originals when everything arrived.
     */
    data class RemoteToRemote(val items: List<FileItem>, val move: Boolean, val destPath: String, val store: RemoteStore, val source: RemoteStore? = null) : OpRequest {
        override val type get() = OpType.UPLOAD
        override val itemCount get() = items.size
    }

    /** Files chosen with the document picker to a folder on the server. */
    data class UploadDocuments(val uris: List<String>, val destPath: String, val store: RemoteStore? = null) : OpRequest {
        override val type get() = OpType.UPLOAD
        override val itemCount get() = uris.size
    }

    /** Files and folders of the server to a folder on the phone. move deletes them from the server afterwards. */
    data class Download(val move: Boolean, val items: List<FileItem>, val destDir: String, val store: RemoteStore? = null) : OpRequest {
        override val type get() = OpType.DOWNLOAD
        override val itemCount get() = items.size
    }
}

data class OpState(
    val type: OpType,
    val itemCount: Int,
    val preparing: Boolean,
    val current: String,
    val done: Long,
    val total: Long,
    val bytesBased: Boolean,
    val bytesPerSecond: Long,
    val etaSeconds: Long,
    val conflict: Conflict?,
    val queued: Int
) {
    val fraction: Float get() = if (total > 0) (done.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else 0f
}

data class OpSummary(val type: OpType, val errors: List<OpError>, val cancelled: Boolean, val itemCount: Int = 0, val target: String = "")

private data class ConflictAnswer(val resolution: Resolution, val applyToAll: Boolean)

private class Tracker : OpContext {
    val done = AtomicLong()
    @Volatile var total = 0L
    @Volatile var currentName = ""
    @Volatile var preparing = true
    @Volatile var bytesBased = true
    @Volatile var conflict: Conflict? = null
    @Volatile var pending: CompletableDeferred<ConflictAnswer>? = null
    @Volatile var applyToAll: Resolution? = null

    override fun addDone(units: Long) {
        done.addAndGet(units)
    }

    override fun setCurrent(name: String) {
        currentName = name
    }

    override suspend fun resolve(conflict: Conflict): Resolution {
        applyToAll?.let { return it }
        val deferred = CompletableDeferred<ConflictAnswer>()
        pending = deferred
        this.conflict = conflict
        try {
            val answer = deferred.await()
            if (answer.applyToAll) applyToAll = answer.resolution
            return answer.resolution
        } finally {
            this.conflict = null
            pending = null
        }
    }
}

/**
 * Runs copy, move, delete, trash, zip and restore one after the other in the background. Only one runs at a time,
 * the rest wait in a queue. While anything runs a foreground service keeps the process alive with a notification.
 */
class OperationManager(private val app: Application, private val trash: TrashStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ArrayDeque<OpRequest>()
    private var running = false

    /** Gives the server connection for uploads and downloads, null when there is no account. Set by the Nextcloud controller. */
    @Volatile var remoteStore: (() -> RemoteStore?)? = null

    @Volatile private var currentJob: Job? = null
    @Volatile private var currentTracker: Tracker? = null

    private val _state = MutableStateFlow<OpState?>(null)
    val state: StateFlow<OpState?> = _state.asStateFlow()

    // Counts finished operations, the lists reload when it changes
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val _finished = MutableSharedFlow<OpSummary>(extraBufferCapacity = 8)
    val finished: SharedFlow<OpSummary> = _finished.asSharedFlow()

    fun submit(request: OpRequest) {
        val launchWorker: Boolean
        synchronized(queue) {
            queue.add(request)
            launchWorker = !running
            if (launchWorker) running = true
        }
        if (launchWorker) scope.launch { workLoop() }
    }

    fun cancelCurrent() {
        currentJob?.cancel()
    }

    fun answerConflict(resolution: Resolution, applyToAll: Boolean) {
        currentTracker?.pending?.complete(ConflictAnswer(resolution, applyToAll))
    }

    private suspend fun workLoop() {
        val powerManager = app.getSystemService(PowerManager::class.java)
        // Keeps the copy going with the screen off. The timeout is a safety net, it is released when the queue is empty.
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nooklin:files")
        try {
            wakeLock.acquire(4L * 60L * 60L * 1000L)
            var serviceStarted = false
            while (true) {
                val request = synchronized(queue) {
                    val next = queue.pollFirst()
                    if (next == null) running = false
                    next
                } ?: break
                val tracker = Tracker()
                currentTracker = tracker
                publish(request, tracker, 0L)
                if (!serviceStarted) {
                    serviceStarted = true
                    startService()
                }
                runOne(request, tracker)
            }
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            currentTracker = null
            currentJob = null
            _state.value = null
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, TransferService::class.java))
        } catch (e: Exception) {
            // Without the service the work still runs while the app is open
        }
    }

    private suspend fun runOne(request: OpRequest, tracker: Tracker) {
        val errors = ArrayList<OpError>()
        val job = scope.launch {
            try {
                execute(request, tracker, errors)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                errors.add(OpError("", ErrorKind.IO, e.message.orEmpty()))
            } catch (e: Exception) {
                errors.add(OpError("", ErrorKind.IO, e.toString()))
            }
        }
        currentJob = job
        val ticker = scope.launch {
            var lastDone = 0L
            var lastTime = SystemClock.elapsedRealtime()
            var speed = 0.0
            while (isActive) {
                delay(250)
                val now = SystemClock.elapsedRealtime()
                val done = tracker.done.get()
                val seconds = (now - lastTime) / 1000.0
                if (seconds > 0) {
                    val instant = (done - lastDone) / seconds
                    speed = if (speed == 0.0) instant else speed * 0.7 + instant * 0.3
                }
                lastDone = done
                lastTime = now
                publish(request, tracker, speed.toLong())
            }
        }
        job.join()
        ticker.cancel()
        val cancelled = job.isCancelled
        _changes.update { it + 1 }
        val target = when (request) {
            is OpRequest.Download -> request.destDir
            is OpRequest.Upload -> request.destPath
            is OpRequest.UploadDocuments -> request.destPath
            is OpRequest.RemoteToRemote -> request.destPath
            else -> ""
        }
        _finished.tryEmit(OpSummary(request.type, errors, cancelled, request.itemCount, target))
    }

    private fun publish(request: OpRequest, tracker: Tracker, bytesPerSecond: Long) {
        val remaining = tracker.total - tracker.done.get()
        val eta = if (tracker.bytesBased && bytesPerSecond > 0 && remaining > 0) remaining / bytesPerSecond else 0L
        _state.value = OpState(
            type = request.type,
            itemCount = request.itemCount,
            preparing = tracker.preparing,
            current = tracker.currentName,
            done = tracker.done.get(),
            total = tracker.total,
            bytesBased = tracker.bytesBased,
            bytesPerSecond = bytesPerSecond,
            etaSeconds = eta,
            conflict = tracker.conflict,
            queued = synchronized(queue) { queue.size }
        )
    }

    private suspend fun uploadDocuments(store: RemoteStore, request: OpRequest.UploadDocuments, t: Tracker, errors: MutableList<OpError>) {
        class Doc(val uri: Uri, val name: String, val size: Long)

        val resolver = app.contentResolver
        val docs = request.uris.map { text ->
            val uri = Uri.parse(text)
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { name = it }
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
            Doc(uri, name, size)
        }
        t.total = docs.sumOf { maxOf(it.size, 0L) }
        t.preparing = false
        val taken = try {
            store.list(request.destPath).mapTo(HashSet()) { it.name }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.add(OpError(request.destPath, ErrorKind.IO, remoteErrorText(e)))
            return
        }
        for (doc in docs) {
            currentCoroutineContext().ensureActive()
            // An existing file is never overwritten: the new one gets a number
            val name = DavUrls.uniqueName(doc.name, taken)
            taken.add(name)
            t.setCurrent(doc.name)
            var last = 0L
            try {
                store.upload(request.destPath.trimEnd('/') + "/" + name, { resolver.openInputStream(doc.uri) ?: throw IOException("Cannot read the file") }, doc.size) { cumulative ->
                    t.addDone(if (cumulative >= last) cumulative - last else cumulative)
                    last = cumulative
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add(OpError(doc.name, ErrorKind.IO, remoteErrorText(e)))
            }
        }
    }

    private suspend fun execute(request: OpRequest, t: Tracker, errors: MutableList<OpError>) {
        when (request) {
            is OpRequest.Transfer -> {
                val files = request.sources.map { File(it) }
                t.total = FileEngine.totalSize(files)
                t.preparing = false
                errors += FileEngine.transfer(files, File(request.destDir), request.move, t)
            }

            is OpRequest.ToTrash -> {
                t.bytesBased = false
                t.total = request.sources.size.toLong()
                t.preparing = false
                for (path in request.sources) {
                    currentCoroutineContext().ensureActive()
                    val file = File(path)
                    t.setCurrent(file.name)
                    try {
                        when {
                            !file.exists() -> errors.add(OpError(path, ErrorKind.NOT_FOUND))
                            !trash.fits(FileEngine.sizeOfPlain(file)) -> errors.add(OpError(path, ErrorKind.TOO_LARGE_FOR_TRASH))
                            else -> trash.put(file)
                        }
                    } catch (e: IOException) {
                        errors.add(OpError(path, ErrorKind.IO, e.message.orEmpty()))
                    }
                    t.addDone(1)
                }
                trash.cleanup()
            }

            is OpRequest.DeletePermanent -> {
                t.bytesBased = false
                val files = request.sources.map { File(it) }
                var total = 0L
                for (file in files) total += FileEngine.countEntries(file)
                t.total = total
                t.preparing = false
                for (file in files) {
                    t.setCurrent(file.name)
                    FileEngine.deleteTree(file, t, errors)
                }
            }

            is OpRequest.Restore -> {
                t.total = request.items.sumOf { it.size }
                t.preparing = false
                for (item in request.items) {
                    currentCoroutineContext().ensureActive()
                    val payload = trash.payloadFile(item)
                    val parent = File(item.originalPath).parentFile
                    if (parent == null || !payload.exists()) {
                        errors.add(OpError(item.originalPath, ErrorKind.NOT_FOUND))
                        continue
                    }
                    parent.mkdirs()
                    errors += FileEngine.transfer(listOf(payload), parent, true, t)
                    // A skipped item stays in the trash, a restored one is forgotten
                    if (!payload.exists()) trash.forget(item)
                }
            }

            is OpRequest.DeleteTrashItems -> {
                t.bytesBased = false
                t.total = request.items.size.toLong()
                t.preparing = false
                for (item in request.items) {
                    currentCoroutineContext().ensureActive()
                    t.setCurrent(item.name)
                    trash.deletePermanently(item)
                    t.addDone(1)
                }
            }

            is OpRequest.EmptyTrash -> {
                t.bytesBased = false
                t.preparing = false
                trash.empty()
            }

            is OpRequest.Extract -> {
                val zip = File(request.zipPath)
                val password = request.password
                if (password != null) {
                    t.total = ZipEngine.totalUncompressedEncrypted(zip)
                    t.preparing = false
                    ZipEngine.extractEncrypted(zip, File(request.destDir), password.value.toCharArray(), t, errors)
                } else {
                    t.total = ZipEngine.totalUncompressed(zip)
                    t.preparing = false
                    ZipEngine.extract(zip, File(request.destDir), t, errors)
                }
            }

            is OpRequest.Compress -> {
                val files = request.sources.map { File(it) }
                t.total = FileEngine.totalSize(files)
                t.preparing = false
                val password = request.password
                if (password != null) {
                    ZipEngine.compressEncrypted(files, File(request.destDir), password.value.toCharArray(), t, errors)
                } else {
                    ZipEngine.compress(files, File(request.destDir), t, errors)
                }
            }

            is OpRequest.Upload -> {
                val store = request.store ?: remoteStore?.invoke()
                if (store == null) {
                    errors.add(OpError("", ErrorKind.IO, appString(R.string.nc_error_not_connected)))
                    return
                }
                val files = request.sources.map { File(it) }
                t.total = FileEngine.totalSize(files)
                t.preparing = false
                errors += RemoteTransfer.upload(store, files, request.destPath, request.move, t, ::remoteErrorText)
            }

            is OpRequest.UploadDocuments -> {
                val store = request.store ?: remoteStore?.invoke()
                if (store == null) {
                    errors.add(OpError("", ErrorKind.IO, appString(R.string.nc_error_not_connected)))
                    return
                }
                uploadDocuments(store, request, t, errors)
            }

            is OpRequest.RemoteToRemote -> {
                val source = request.source ?: remoteStore?.invoke()
                if (source == null) {
                    errors.add(OpError("", ErrorKind.IO, appString(R.string.nc_error_not_connected)))
                    return
                }
                val temp = File(app.cacheDir, "transfer-" + System.nanoTime().toString(36))
                temp.mkdirs()
                try {
                    val plan = RemoteTransfer.planDownload(source, request.items, ::remoteErrorText)
                    // Every byte is counted twice: once on the way in, once on the way out
                    t.total = plan.totalBytes * 2
                    t.preparing = false
                    val before = errors.size
                    errors += RemoteTransfer.download(source, plan, temp, false, t, ::remoteErrorText)
                    currentCoroutineContext().ensureActive()
                    val files = temp.listFiles().orEmpty().toList()
                    errors += RemoteTransfer.upload(request.store, files, request.destPath, false, t, ::remoteErrorText)
                    if (request.move && errors.size == before) {
                        for (item in request.items) {
                            currentCoroutineContext().ensureActive()
                            try {
                                source.delete(item.path)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                errors.add(OpError(item.name, ErrorKind.IO, remoteErrorText(e)))
                            }
                        }
                    }
                } finally {
                    temp.deleteRecursively()
                }
            }

            is OpRequest.Download -> {
                val store = request.store ?: remoteStore?.invoke()
                if (store == null) {
                    errors.add(OpError("", ErrorKind.IO, appString(R.string.nc_error_not_connected)))
                    return
                }
                val plan = RemoteTransfer.planDownload(store, request.items, ::remoteErrorText)
                t.total = plan.totalBytes
                t.preparing = false
                val destDir = File(request.destDir)
                destDir.mkdirs()
                errors += RemoteTransfer.download(store, plan, destDir, request.move, t, ::remoteErrorText)
            }
        }
    }
}
