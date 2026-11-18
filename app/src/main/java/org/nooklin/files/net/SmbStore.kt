package org.nooklin.files.net

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.CoroutineScope
import org.nooklin.files.cloud.RemoteItem
import org.nooklin.files.cloud.RemoteStore
import org.nooklin.files.cloud.ServerError
import org.nooklin.files.cloud.ServerException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/** A connection to one share: the client, the connection and the session all have to be closed together. */
class SmbHandle(val client: SMBClient, val connection: Connection, val session: Session, val share: DiskShare)

/**
 * A share on an SMB2 or SMB3 server (not SMB1). Paths start with a slash, "" is the root of the share. The user name
 * can be written as DOMAIN\user. An empty user name logs in as guest.
 */
class SmbStore(
    private val host: String,
    private val port: Int,
    private val shareName: String,
    private val user: String,
    private val password: String,
    scope: CoroutineScope
) : IdleConnection<SmbHandle>(scope), RemoteStore {

    override fun open(): SmbHandle {
        val config = SmbConfig.builder()
            .withTimeout(60, TimeUnit.SECONDS)
            .withSoTimeout(60, TimeUnit.SECONDS)
            .build()
        val client = SMBClient(config)
        try {
            val connection = client.connect(host, port)
            val context = when {
                user.isEmpty() -> AuthenticationContext.guest()
                user.contains('\\') -> AuthenticationContext(user.substringAfter('\\'), password.toCharArray(), user.substringBefore('\\'))
                else -> AuthenticationContext(user, password.toCharArray(), "")
            }
            val session = connection.authenticate(context)
            val share = session.connectShare(shareName) as? DiskShare
                ?: throw ServerException(ServerError.SHARE, "Not a file share")
            return SmbHandle(client, connection, session, share)
        } catch (e: Exception) {
            try {
                client.close()
            } catch (ignored: Exception) {
                // Nothing to close
            }
            throw map(e)
        }
    }

    override fun isAlive(connection: SmbHandle): Boolean = connection.connection.isConnected && connection.share.isConnected

    override fun close(connection: SmbHandle) {
        try {
            connection.share.close()
        } catch (e: Exception) {
            // Already gone
        }
        try {
            connection.session.close()
        } catch (e: Exception) {
            // Already gone
        }
        try {
            connection.client.close()
        } catch (e: Exception) {
            // Already gone
        }
    }

    private fun map(e: Exception): Exception {
        if (e is ServerException) return e
        if (e is SMBApiException) {
            val kind = when (e.status) {
                NtStatus.STATUS_LOGON_FAILURE, NtStatus.STATUS_ACCOUNT_DISABLED, NtStatus.STATUS_PASSWORD_EXPIRED -> ServerError.LOGIN
                NtStatus.STATUS_BAD_NETWORK_NAME -> ServerError.SHARE
                NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND, NtStatus.STATUS_NO_SUCH_FILE -> ServerError.NOT_FOUND
                NtStatus.STATUS_ACCESS_DENIED -> ServerError.DENIED
                else -> ServerError.OTHER
            }
            return ServerException(kind, e.message.orEmpty())
        }
        return e
    }

    /** Smb wants backslashes and no leading slash. */
    private fun smbPath(path: String): String = path.trim('/').replace('/', '\\')

    /** Opens a connection and lists the root of the share, to show that the address, share, user and password work. */
    suspend fun probe() {
        use { handle, _ -> handle.share.list("") }
    }

    override suspend fun list(path: String): List<RemoteItem> = use { handle, _ ->
        try {
            handle.share.list(smbPath(path)).mapNotNull { info ->
                val name = info.fileName
                if (name == "." || name == "..") return@mapNotNull null
                val isDir = info.fileAttributes and DIRECTORY != 0L
                RemoteItem(
                    name, path.trimEnd('/') + "/" + name, isDir,
                    if (isDir) 0L else info.endOfFile, info.lastWriteTime?.toEpochMillis() ?: 0L
                )
            }
        } catch (e: SMBApiException) {
            throw map(e)
        }
    }

    override suspend fun makeFolder(path: String) {
        use { handle, _ ->
            try {
                handle.share.mkdir(smbPath(path))
            } catch (e: SMBApiException) {
                throw map(e)
            }
        }
    }

    override suspend fun delete(path: String) {
        use { handle, _ ->
            try {
                val p = smbPath(path)
                if (handle.share.folderExists(p)) handle.share.rmdir(p, true) else handle.share.rm(p)
            } catch (e: SMBApiException) {
                throw map(e)
            }
        }
    }

    override suspend fun move(from: String, to: String) {
        use { handle, _ ->
            try {
                val entry = handle.share.open(
                    smbPath(from),
                    EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ),
                    null,
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                entry.use { it.rename(smbPath(to), false) }
            } catch (e: SMBApiException) {
                throw map(e)
            }
        }
    }

    override suspend fun download(path: String, dest: File, onBytes: (Long) -> Unit) {
        val temp = File(dest.parentFile, ".nooklin-" + dest.name + ".part")
        try {
            use { handle, check ->
                try {
                    handle.share.openFile(
                        smbPath(path), EnumSet.of(AccessMask.GENERIC_READ), null,
                        SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null
                    ).use { file ->
                        var total = 0L
                        file.inputStream.use { input ->
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
                    }
                } catch (e: SMBApiException) {
                    throw map(e)
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

    override suspend fun upload(path: String, input: () -> InputStream, length: Long, onBytes: (Long) -> Unit) {
        use { handle, check ->
            try {
                handle.share.openFile(
                    smbPath(path), EnumSet.of(AccessMask.GENERIC_WRITE), EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null
                ).use { file ->
                    var total = 0L
                    file.outputStream.use { sink ->
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
                }
            } catch (e: SMBApiException) {
                throw map(e)
            }
        }
    }

    private companion object {
        const val DIRECTORY = 0x10L
    }
}
