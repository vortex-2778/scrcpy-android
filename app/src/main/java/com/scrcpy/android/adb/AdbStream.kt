package com.scrcpy.android.adb

import java.io.IOException
import java.util.ArrayDeque

/**
 * One ADB stream (an OPEN'ed channel multiplexed over the transport).
 *
 * Thread model:
 *  - the connection reader thread delivers data via [deliverData] / [deliverOkay] / [deliverClose],
 *  - any thread may [write] (chunked, thread-safe) and [readFully] (blocking, signal-based).
 */
class AdbStream internal constructor(
    val connection: AdbConnection,
    val localId: Int,
    val service: String,
) {

    @Volatile
    internal var _remoteId: Int = 0

    val remoteId: Int get() = _remoteId

    val isClosed: Boolean
        get() = closed || peerClosed

    private val chunks = ArrayDeque<ByteArray>()
    private var currentChunk: ByteArray? = null
    private var currentPos = 0

    @Volatile
    private var closed = false

    @Volatile
    private var peerClosed = false

    private val readLock = Object()
    private val okayLock = Object()

    // ---- lifecycle (called by connection reader thread) ----

    internal fun deliverOkay(remoteId: Int) {
        _remoteId = remoteId
        synchronized(okayLock) { okayLock.notifyAll() }
    }

    internal fun deliverData(payload: ByteArray) {
        synchronized(readLock) {
            if (!closed && !peerClosed) {
                chunks.addLast(payload)
                readLock.notifyAll()
            }
        }
    }

    internal fun deliverClose() {
        val notifyOkay: Boolean
        synchronized(readLock) {
            peerClosed = true
            notifyOkay = true
            readLock.notifyAll()
        }
        if (notifyOkay) {
            synchronized(okayLock) { okayLock.notifyAll() }
        }
    }

    // ---- client API ----

    /** Blocks until OKAY/CLSE arrives or [timeoutMs] elapse. */
    internal fun waitOkay(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(okayLock) {
            while (_remoteId == 0 && !peerClosed && !closed) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                okayLock.wait(remaining.coerceAtLeast(1))
            }
        }
    }

    fun readFully(dest: ByteArray, offset: Int, length: Int) {
        var copied = 0
        synchronized(readLock) {
            while (copied < length) {
                if (closed) throw IOException("Stream closed locally: $service")
                if (peerClosed && chunks.isEmpty()) {
                    throw IOException(
                        "ADB stream closed by peer while reading ($service): $copied/$length bytes"
                    )
                }
                if (currentChunk == null || currentPos >= currentChunk!!.size) {
                    if (chunks.isEmpty()) {
                        // Blocking wait; 30s sanity cap so a wedged connection doesn't wait forever.
                        readLock.wait(5_000)
                        continue
                    }
                    currentChunk = chunks.removeFirst()
                    currentPos = 0
                }
                val chunk = currentChunk!!
                val n = (chunk.size - currentPos).coerceAtMost(length - copied)
                System.arraycopy(chunk, currentPos, dest, offset + copied, n)
                currentPos += n
                copied += n
            }
        }
    }

    fun write(data: ByteArray) = write(data, 0, data.size)

    fun write(data: ByteArray, offset: Int, length: Int) {
        if (closed) throw IOException("Stream closed locally: $service")
        if (peerClosed) throw IOException("ADB stream closed by peer: $service")
        var sent = 0
        while (sent < length) {
            val n = connection.writeStreamPacket(this, data, offset + sent, length - sent)
            sent += n
        }
    }

    fun close() {
        val wasClosed: Boolean
        synchronized(readLock) { wasClosed = closed; closed = true; readLock.notifyAll() }
        if (wasClosed) return
        if (_remoteId != 0) runCatching { connection.sendClose(this) }
        synchronized(okayLock) { okayLock.notifyAll() }
        connection.removeStream(this)
    }

    // ---- big-endian helpers used by the scrcpy protocol ----

    fun readBe32(): Int {
        val b = ByteArray(4)
        readFully(b, 0, 4)
        return ((b[0].toInt() and 0xff) shl 24) or
            ((b[1].toInt() and 0xff) shl 16) or
            ((b[2].toInt() and 0xff) shl 8) or
            (b[3].toInt() and 0xff)
    }
}
