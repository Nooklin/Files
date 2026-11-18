package org.nooklin.files.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.nooklin.files.cloud.DavAccount
import org.nooklin.files.cloud.DavException
import org.nooklin.files.cloud.DavParser
import org.nooklin.files.cloud.DavUrls
import org.nooklin.files.cloud.RemoteItem
import org.nooklin.files.cloud.RemoteStore
import org.nooklin.files.cloud.RemoteTrashItem
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Talks WebDAV to a Nextcloud server. Every call runs off the main thread and stops when its coroutine is cancelled. */
class WebDavClient(private val account: DavAccount) : RemoteStore {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val xml = "application/xml".toMediaType()
    private val propfindFiles =
        """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
    private val propfindTrash =
        """<?xml version="1.0"?><d:propfind xmlns:d="DAV:" xmlns:nc="http://nextcloud.org/ns"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><nc:trashbin-filename/><nc:trashbin-original-location/><nc:trashbin-deletion-time/></d:prop></d:propfind>"""

    private fun request(url: String, method: String, body: RequestBody? = null, vararg headers: Pair<String, String>): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(account.user, account.password, Charsets.UTF_8))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.method(method, body).build()
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    /** Runs the request and fails with a DavException for any answer that is not a success. */
    private suspend fun <T> execute(request: Request, handle: suspend (Response) -> T): T {
        val response = client.newCall(request).await()
        response.use {
            if (!it.isSuccessful) throw DavException(it.code, it.message)
            return handle(it)
        }
    }

    /** Checks the address and the login: asks for the root folder. */
    suspend fun probe() {
        val url = DavUrls.filesUrl(account.server, account.user, "")
        execute(request(url, "PROPFIND", propfindFiles.toRequestBody(xml), "Depth" to "0")) { }
    }

    override suspend fun list(path: String): List<RemoteItem> {
        val url = DavUrls.filesUrl(account.server, account.user, path) + if (path.isEmpty()) "/" else "/"
        val text = execute(request(url, "PROPFIND", propfindFiles.toRequestBody(xml), "Depth" to "1")) { response ->
            withContext(Dispatchers.IO) { response.body?.string().orEmpty() }
        }
        return DavParser.toItems(DavParser.parse(text), path)
    }

    override suspend fun makeFolder(path: String) {
        execute(request(DavUrls.filesUrl(account.server, account.user, path), "MKCOL")) { }
    }

    override suspend fun delete(path: String) {
        execute(request(DavUrls.filesUrl(account.server, account.user, path), "DELETE")) { }
    }

    /** Renames or moves. Fails when the target exists. */
    override suspend fun move(from: String, to: String) {
        val target = DavUrls.filesUrl(account.server, account.user, to)
        execute(request(DavUrls.filesUrl(account.server, account.user, from), "MOVE", null, "Destination" to target, "Overwrite" to "F")) { }
    }

    /** Copies on the server. Fails when the target exists. */
    suspend fun copy(from: String, to: String) {
        val target = DavUrls.filesUrl(account.server, account.user, to)
        execute(request(DavUrls.filesUrl(account.server, account.user, from), "COPY", null, "Destination" to target, "Overwrite" to "F")) { }
    }

    /** Downloads to a temporary file next to dest and renames it when complete. onBytes gets the bytes added so far. */
    override suspend fun download(path: String, dest: File, onBytes: (Long) -> Unit) {
        val temp = File(dest.parentFile, ".nooklin-" + dest.name + ".part")
        try {
            execute(request(DavUrls.filesUrl(account.server, account.user, path), "GET")) { response ->
                withContext(Dispatchers.IO) {
                    val body = response.body ?: throw IOException("Empty answer")
                    var total = 0L
                    body.byteStream().use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                total += read
                                onBytes(total)
                            }
                        }
                    }
                }
            }
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
    }

    /** Uploads the stream to path. length is -1 when it is not known. Overwrites what is there. */
    override suspend fun upload(path: String, input: () -> InputStream, length: Long, onBytes: (Long) -> Unit) {
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = length
            override fun writeTo(sink: BufferedSink) {
                var total = 0L
                input().use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        total += read
                        onBytes(total)
                    }
                }
            }
        }
        execute(request(DavUrls.filesUrl(account.server, account.user, path), "PUT", body)) { }
    }

    // The Nextcloud trash

    suspend fun listTrash(): List<RemoteTrashItem> {
        val url = DavUrls.trashUrl(account.server, account.user, "trash") + "/"
        val text = execute(request(url, "PROPFIND", propfindTrash.toRequestBody(xml), "Depth" to "1")) { response ->
            withContext(Dispatchers.IO) { response.body?.string().orEmpty() }
        }
        return DavParser.toTrashItems(DavParser.parse(text))
    }

    /** Puts the item back where it was deleted. */
    suspend fun restore(item: RemoteTrashItem) {
        val last = item.trashPath.substringAfterLast('/')
        val target = DavUrls.trashUrl(account.server, account.user, "restore/$last")
        execute(request(DavUrls.trashUrl(account.server, account.user, item.trashPath), "MOVE", null, "Destination" to target)) { }
    }

    suspend fun deleteFromTrash(item: RemoteTrashItem) {
        execute(request(DavUrls.trashUrl(account.server, account.user, item.trashPath), "DELETE")) { }
    }

    suspend fun emptyTrash() {
        execute(request(DavUrls.trashUrl(account.server, account.user, "trash"), "DELETE")) { }
    }
}
