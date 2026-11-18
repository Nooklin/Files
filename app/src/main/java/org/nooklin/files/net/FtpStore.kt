package org.nooklin.files.net

import kotlinx.coroutines.CoroutineScope
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.nooklin.files.cloud.RemoteItem
import org.nooklin.files.cloud.RemoteStore
import org.nooklin.files.cloud.ServerError
import org.nooklin.files.cloud.ServerException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Duration

/**
 * An FTP server. Plain FTP, the traffic is not encrypted. Paths start with a slash, "" is the root.
 * Names are read as UTF-8. Listing uses MLSD when the server has it and the older LIST when it does not.
 */
class FtpStore(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val password: String,
    scope: CoroutineScope
) : IdleConnection<FTPClient>(scope), RemoteStore {

    override fun open(): FTPClient {
        val client = FTPClient()
        client.setDefaultTimeout(30_000)
        client.connectTimeout = 20_000
        client.setDataTimeout(Duration.ofSeconds(60))
        client.controlEncoding = "UTF-8"
        client.bufferSize = 64 * 1024
        try {
            client.connect(host, port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                throw ServerException(ServerError.OTHER, "Server answered " + client.replyCode)
            }
            // No user name means an anonymous login
            val ok = if (user.isEmpty()) client.login("anonymous", "anonymous@") else client.login(user, password)
            if (!ok) throw ServerException(ServerError.LOGIN, "Login refused")
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
        } catch (e: Exception) {
            close(client)
            throw e
        }
        return client
    }

    override fun isAlive(connection: FTPClient): Boolean =
        connection.isConnected && try {
            connection.sendNoOp()
        } catch (e: IOException) {
            false
        }

    override fun close(connection: FTPClient) {
        try {
            if (connection.isConnected) {
                try {
                    connection.logout()
                } catch (e: IOException) {
                    // The connection is closed anyway
                }
                connection.disconnect()
            }
        } catch (e: IOException) {
            // Already gone
        }
    }

    /** Opens a connection and lists the root, to show that the address, user and password work. */
    suspend fun probe() {
        use { client, _ -> listIn(client, "") }
    }

    override suspend fun list(path: String): List<RemoteItem> = use { client, _ -> listIn(client, path) }

    private fun listIn(client: FTPClient, path: String): List<RemoteItem> {
        val dir = path.ifEmpty { "/" }
        var files: Array<FTPFile>? = try {
            client.mlistDir(dir)
        } catch (e: IOException) {
            null
        }
        // 500 or 502: the server has no MLSD
        if (files == null || !FTPReply.isPositiveCompletion(client.replyCode)) {
            files = client.listFiles(dir)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                val kind = if (client.replyCode == 550) ServerError.NOT_FOUND else ServerError.OTHER
                throw ServerException(kind, client.replyString.trim())
            }
        }
        return files.orEmpty().mapNotNull { file ->
            val name = file.name ?: return@mapNotNull null
            if (name == "." || name == ".." || name.contains('/')) return@mapNotNull null
            val isDir = file.isDirectory
            if (!isDir && !file.isFile && !file.isSymbolicLink) return@mapNotNull null
            val modified = file.timestampInstant?.toEpochMilli() ?: file.timestamp?.timeInMillis ?: 0L
            RemoteItem(name, path.trimEnd('/') + "/" + name, isDir, if (isDir) 0L else maxOf(file.size, 0L), modified)
        }
    }

    override suspend fun makeFolder(path: String) {
        use { client, _ ->
            if (!client.makeDirectory(path)) throw ServerException(ServerError.DENIED, client.replyString.trim())
        }
    }

    override suspend fun delete(path: String) {
        use { client, _ -> deleteIn(client, path, 0) }
    }

    private fun deleteIn(client: FTPClient, path: String, depth: Int) {
        if (client.deleteFile(path)) return
        // Not a file, so a folder: empty it first
        if (depth > 64) throw IOException("Folders are nested too deep")
        for (child in listIn(client, path)) deleteIn(client, child.path, depth + 1)
        if (!client.removeDirectory(path)) throw ServerException(ServerError.DENIED, client.replyString.trim())
    }

    override suspend fun move(from: String, to: String) {
        use { client, _ ->
            if (!client.rename(from, to)) throw ServerException(ServerError.DENIED, client.replyString.trim())
        }
    }

    override suspend fun download(path: String, dest: File, onBytes: (Long) -> Unit) {
        val temp = File(dest.parentFile, ".nooklin-" + dest.name + ".part")
        try {
            use { client, check ->
                val stream = client.retrieveFileStream(path)
                    ?: throw ServerException(ServerError.NOT_FOUND, client.replyString.trim())
                var total = 0L
                stream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            check()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            total += read
                            onBytes(total)
                        }
                    }
                }
                if (!client.completePendingCommand()) throw ServerException(ServerError.OTHER, client.replyString.trim())
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

    override suspend fun upload(path: String, input: () -> InputStream, length: Long, onBytes: (Long) -> Unit) {
        use { client, check ->
            val out = client.storeFileStream(path) ?: throw ServerException(ServerError.DENIED, client.replyString.trim())
            var total = 0L
            out.use { sink ->
                input().use { source ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        check()
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        total += read
                        onBytes(total)
                    }
                }
            }
            if (!client.completePendingCommand()) throw ServerException(ServerError.DENIED, client.replyString.trim())
        }
    }
}
