package org.nooklin.files.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.nooklin.files.fs.ErrorKind
import org.nooklin.files.fs.FileEngine
import org.nooklin.files.fs.FileItem
import org.nooklin.files.fs.OpContext
import org.nooklin.files.fs.OpError
import java.io.File
import java.io.IOException
import java.io.InputStream

/** What the transfer code needs from the server. The WebDAV client implements it, the tests use a fake. */
interface RemoteStore {
    suspend fun list(path: String): List<RemoteItem>
    suspend fun makeFolder(path: String)
    suspend fun delete(path: String)
    suspend fun download(path: String, dest: File, onBytes: (Long) -> Unit)
    suspend fun upload(path: String, input: () -> InputStream, length: Long, onBytes: (Long) -> Unit)
    /** Renames or moves inside the server. Only the stores that offer it override this. */
    suspend fun move(from: String, to: String) { throw UnsupportedOperationException() }
}

/** One file or folder inside a folder that is going to be downloaded. relative is empty for a single file. */
class PlanItem(val remotePath: String, val relative: String, val isDir: Boolean, val size: Long)

class PlanTop(val entry: FileItem, val items: List<PlanItem>)

class DownloadPlan(val tops: List<PlanTop>, val errors: List<OpError>) {
    val totalBytes: Long get() = tops.sumOf { top -> top.items.sumOf { it.size } }
}

private class UnsafeName : Exception()

/**
 * Copy and move between the phone and the server, folders included. A name that is already taken on the other
 * side gets a number (name (1).ext), nothing is ever overwritten. A move deletes the source of an item only
 * when everything in that item was transferred without a problem.
 */
object RemoteTransfer {
    private const val MAX_DEPTH = 64

    fun safeName(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\') && !name.contains('\u0000')

    private fun delta(cumulative: Long, last: Long): Long = if (cumulative >= last) cumulative - last else cumulative

    private fun join(folder: String, name: String): String = folder.trimEnd('/') + "/" + name

    // Download

    /** Lists everything that will be downloaded, so the total size is known before the first byte moves. */
    suspend fun planDownload(store: RemoteStore, entries: List<FileItem>, describe: (Exception) -> String): DownloadPlan {
        val tops = ArrayList<PlanTop>()
        val errors = ArrayList<OpError>()
        for (entry in entries) {
            currentCoroutineContext().ensureActive()
            try {
                if (!safeName(entry.name)) throw UnsafeName()
                val items = ArrayList<PlanItem>()
                if (entry.isDir) walk(store, entry.path, "", items, 0) else items.add(PlanItem(entry.path, "", false, entry.size))
                tops.add(PlanTop(entry, items))
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnsafeName) {
                errors.add(OpError(entry.path, ErrorKind.UNSAFE_ENTRY))
            } catch (e: Exception) {
                errors.add(OpError(entry.path, ErrorKind.IO, describe(e)))
            }
        }
        return DownloadPlan(tops, errors)
    }

    private suspend fun walk(store: RemoteStore, path: String, relative: String, out: MutableList<PlanItem>, depth: Int) {
        if (depth > MAX_DEPTH) throw IOException("Folders are nested too deep")
        currentCoroutineContext().ensureActive()
        for (child in store.list(path)) {
            if (!safeName(child.name)) throw UnsafeName()
            val childRelative = if (relative.isEmpty()) child.name else relative + "/" + child.name
            if (child.isDir) {
                out.add(PlanItem(child.path, childRelative, true, 0L))
                walk(store, child.path, childRelative, out, depth + 1)
            } else {
                out.add(PlanItem(child.path, childRelative, false, child.size))
            }
        }
    }

    suspend fun download(
        store: RemoteStore,
        plan: DownloadPlan,
        destDir: File,
        move: Boolean,
        ctx: OpContext,
        describe: (Exception) -> String
    ): List<OpError> {
        val errors = ArrayList<OpError>(plan.errors)
        for (top in plan.tops) {
            currentCoroutineContext().ensureActive()
            val before = errors.size
            val local = FileEngine.uniqueFile(destDir, top.entry.name, top.entry.isDir)
            ctx.setCurrent(top.entry.name)
            try {
                if (top.entry.isDir && !local.mkdirs() && !local.isDirectory) throw IOException("Cannot create the folder")
                for (item in top.items) {
                    currentCoroutineContext().ensureActive()
                    val target = if (item.relative.isEmpty()) local else File(local, item.relative)
                    if (item.isDir) {
                        target.mkdirs()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    ctx.setCurrent(target.name)
                    var last = 0L
                    try {
                        store.download(item.remotePath, target) { cumulative ->
                            ctx.addDone(delta(cumulative, last))
                            last = cumulative
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errors.add(OpError(item.remotePath, ErrorKind.IO, describe(e)))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add(OpError(top.entry.path, ErrorKind.IO, describe(e)))
            }
            if (move && errors.size == before) {
                try {
                    store.delete(top.entry.path)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors.add(OpError(top.entry.path, ErrorKind.IO, describe(e)))
                }
            }
        }
        return errors
    }

    // Upload

    suspend fun upload(
        store: RemoteStore,
        sources: List<File>,
        destPath: String,
        move: Boolean,
        ctx: OpContext,
        describe: (Exception) -> String
    ): List<OpError> {
        val errors = ArrayList<OpError>()
        val taken = try {
            store.list(destPath).mapTo(HashSet()) { it.name }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.add(OpError(destPath, ErrorKind.IO, describe(e)))
            return errors
        }
        for (source in sources) {
            currentCoroutineContext().ensureActive()
            if (!source.exists()) {
                errors.add(OpError(source.path, ErrorKind.NOT_FOUND))
                continue
            }
            if (FileEngine.isLink(source)) {
                errors.add(OpError(source.path, ErrorKind.SYMLINK))
                continue
            }
            val name = DavUrls.uniqueName(source.name, taken)
            taken.add(name)
            val before = errors.size
            ctx.setCurrent(source.name)
            try {
                uploadTree(store, source, join(destPath, name), ctx, errors, describe)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add(OpError(source.path, ErrorKind.IO, describe(e)))
            }
            if (move && errors.size == before) FileEngine.deleteQuietly(source)
        }
        return errors
    }

    private suspend fun uploadTree(
        store: RemoteStore,
        file: File,
        target: String,
        ctx: OpContext,
        errors: MutableList<OpError>,
        describe: (Exception) -> String
    ) {
        currentCoroutineContext().ensureActive()
        if (file.isDirectory) {
            store.makeFolder(target)
            val children = file.listFiles()?.sortedBy { it.name } ?: throw IOException("Cannot read the folder")
            for (child in children) {
                if (FileEngine.isLink(child)) {
                    errors.add(OpError(child.path, ErrorKind.SYMLINK))
                    continue
                }
                try {
                    uploadTree(store, child, join(target, child.name), ctx, errors, describe)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errors.add(OpError(child.path, ErrorKind.IO, describe(e)))
                }
            }
        } else {
            ctx.setCurrent(file.name)
            var last = 0L
            store.upload(target, { file.inputStream() }, file.length()) { cumulative ->
                ctx.addDone(delta(cumulative, last))
                last = cumulative
            }
        }
    }
}
