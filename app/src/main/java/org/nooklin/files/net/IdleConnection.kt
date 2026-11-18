package org.nooklin.files.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One connection to a server, used by one request at a time. The connection is opened by the first request, kept
 * for the next ones and closed after it was idle for a while, so no connection stays open when nothing is going on.
 * A request that fails or is cancelled throws the connection away: it may be in the middle of a transfer.
 */
abstract class IdleConnection<C : Any>(private val scope: CoroutineScope, private val idleMillis: Long = 30_000L) {
    private val mutex = Mutex()
    private var connection: C? = null
    private var idleJob: Job? = null

    protected abstract fun open(): C
    protected abstract fun isAlive(connection: C): Boolean
    protected abstract fun close(connection: C)

    /** Runs the block on the connection, on a background thread. */
    protected suspend fun <T> use(block: (C, () -> Unit) -> T): T = mutex.withLock {
        idleJob?.cancel()
        try {
            withContext(Dispatchers.IO) {
                // The second argument of the block throws when the request was cancelled, loops call it between buffers
                val job = currentCoroutineContext()[Job]!!
                var current = connection?.takeIf { isAlive(it) }
                if (current == null) {
                    connection?.let { dropQuietly(it) }
                    connection = null
                    current = open()
                    connection = current
                }
                block(current) { job.ensureActive() }
            }
        } catch (e: Throwable) {
            connection?.let { dropQuietly(it) }
            connection = null
            throw e
        } finally {
            if (connection != null) scheduleClose()
        }
    }

    private fun scheduleClose() {
        idleJob = scope.launch {
            try {
                delay(idleMillis)
            } catch (e: CancellationException) {
                return@launch
            }
            mutex.withLock {
                connection?.let { dropQuietly(it) }
                connection = null
            }
        }
    }

    /** Closes the connection now, for example when the server is left or its settings changed. */
    fun disconnect() {
        scope.launch {
            mutex.withLock {
                idleJob?.cancel()
                connection?.let { dropQuietly(it) }
                connection = null
            }
        }
    }

    private fun dropQuietly(connection: C) {
        try {
            close(connection)
        } catch (e: Exception) {
            // Already gone
        }
    }
}
