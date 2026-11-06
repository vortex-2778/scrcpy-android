package com.scrcpy.android.adb

import com.scrcpy.android.util.AppLog
import java.io.IOException
import java.security.KeyPair
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal ADB host over USB: CNXN/AUTH handshake, stream multiplexing
 * (OPEN/OKAY/WRTE/CLSE) and services (`shell:`, `localabstract:`, `sync:`).
 */
class AdbConnection(
    val transport: UsbTransport,
    private val keyProvider: () -> KeyPair,
    private val publicKeyProvider: () -> ByteArray,
) {

    private val TAG = "Adb"

    private val streams = ConcurrentHashMap<Int, AdbStream>()
    private val localIdCounter = AtomicInteger(0x01000000)
    private val sendLock = Object()

    @Volatile
    private var closed = false

    /** Payload size limit advertised by the device in CNXN; we never exceed it. */
    @Volatile
    var deviceMaxData: Int = AdbPacket.MAX_PAYLOAD
        private set

    /** Protocol version reported by adbd (0x01000001 expected). */
    @Volatile
    var deviceVersion: Int = 0
        private set

    val isClosed: Boolean get() = closed

    /** Runs the CNXN/AUTH handshake. Must be called before start(). */
    fun handshake() {
        sendCnxn()
        var signatureSent = false
        while (true) {
            val pkt = readPacket()
            when (pkt.cmd) {
                AdbPacket.A_CNXN -> {
                    deviceVersion = pkt.arg0
                    deviceMaxData = if (pkt.arg1 in 1..(8 * 4096)) pkt.arg1 else AdbPacket.MAX_PAYLOAD
                    AppLog.i(TAG, String.format("adbd version=%08x maxdata=%d", deviceVersion, deviceMaxData))
                    return
                }
                AdbPacket.A_AUTH -> when (pkt.arg0) {
                    AdbPacket.AUTH_TOKEN -> {
                        if (!signatureSent) {
                            val signature = AdbKeyPair.sign(keyProvider(), pkt.data)
                            sendPacket(AdbPacket.A_AUTH, AdbPacket.AUTH_SIGNATURE, 0, signature)
                            signatureSent = true
                            AppLog.i(TAG, String.format("SIGNATURE sent (%d bytes)", signature.size))
                        } else {
                            // adbd rejects the first signature (unknown key): publish the public
                            // key so the phone shows the "allow USB debugging" dialog.
                            val pub = publicKeyProvider()
                            sendPacket(AdbPacket.A_AUTH, AdbPacket.AUTH_PUBLICKEY, 0, pub)
                            AppLog.i(TAG, "RSAPUBLICKEY sent; confirm on the phone")
                        }
                    }
                    else -> AppLog.w(TAG, "AUTH with unexpected type ${pkt.arg0}")
                }
                else -> AppLog.w(TAG, "unexpected ${pkt.commandName()} during handshake")
            }
        }
    }

    private fun sendCnxn() {
        val identity = "host::".toByteArray(Charsets.US_ASCII)
        val payload = ByteArray(8 + identity.size)
        AdbPacket.putLe32(payload, 0, AdbPacket.PROTOCOL_VERSION)
        AdbPacket.putLe32(payload, 4, AdbPacket.MAX_PAYLOAD)
        identity.copyInto(payload, 8)
        sendPacket(AdbPacket.A_CNXN, AdbPacket.PROTOCOL_VERSION, AdbPacket.MAX_PAYLOAD, payload)
    }

    /** Single synchronized packet send (header + optional payload). */
    private fun sendPacket(cmd: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        synchronized(sendLock) {
            val msg = ByteArray(AdbPacket.COMMAND_LENGTH + payload.size)
            AdbPacket.fillHeader(
                msg, cmd, arg0, arg1, payload.size,
                AdbPacket.checksum(payload, 0, payload.size)
            )
            payload.copyInto(msg, AdbPacket.COMMAND_LENGTH)
            transport.write(msg, 0, msg.size)
        }
    }

    private fun readPacket(): AdbPacketData {
        val header = ByteArray(AdbPacket.COMMAND_LENGTH)
        transport.readFully(header, 0, AdbPacket.COMMAND_LENGTH)
        val cmd = AdbPacket.getLe32(header, 0)
        val arg0 = AdbPacket.getLe32(header, 4)
        val arg1 = AdbPacket.getLe32(header, 8)
        val dataLen = AdbPacket.getLe32(header, 12)
        val data = if (dataLen > 0) ByteArray(dataLen) else EMPTY
        if (dataLen > 0) transport.readFully(data, 0, dataLen)
        return AdbPacketData(cmd, arg0, arg1, data)
    }

    // ---- reader thread ----

    fun start() {
        val t = Thread({ readerLoop() }, "adb-reader")
        t.isDaemon = true
        t.start()
    }

    private fun readerLoop() {
        while (!closed) {
            val pkt = try {
                readPacket()
            } catch (t: Throwable) {
                if (!closed) AppLog.e(TAG, "reader stopped: ${t.message}")
                break
            }
            onPacket(pkt)
        }
        for (s in streams.values) s.deliverClose()
        streams.clear()
    }

    private fun onPacket(pkt: AdbPacketData) {
        when (pkt.cmd) {
            AdbPacket.A_OKAY -> {
                val stream = streams[pkt.arg1]
                if (stream != null) stream.deliverOkay(pkt.arg0)
            }
            AdbPacket.A_WRTE -> {
                val stream = streams[pkt.arg1]
                if (stream != null) {
                    // Flow control: acknowledge device->host data with OKAY before/while consuming.
                    sendPacket(AdbPacket.A_OKAY, pkt.arg1, pkt.arg0, EMPTY)
                    stream.deliverData(pkt.data)
                } else {
                    AppLog.w(TAG, "WRTE for unknown stream id ${pkt.arg1}")
                    sendPacket(AdbPacket.A_OKAY, pkt.arg1, pkt.arg0, EMPTY)
                }
            }
            AdbPacket.A_CLSE -> {
                val stream = streams[pkt.arg1]
                if (stream != null) {
                    stream.deliverClose()
                    streams.remove(pkt.arg1)
                }
            }
            else -> AppLog.w(TAG, "unexpected ${pkt.commandName()} arg0=${pkt.arg0} arg1=${pkt.arg1}")
        }
    }

    // ---- stream API ----

    /**
     * Opens a stream for [service]; blocks until OKAY or timeout, retrying a few
     * times with backoff (useful right after launching the scrcpy server, whose
     * local socket may not be bound yet).
     */
    fun open(service: String, timeoutMs: Long = 5000, retries: Int = 3): AdbStream {
        var lastError: IOException? = null
        for (attempt in 0 until retries) {
            if (closed) throw IOException("ADB connection closed")
            val attemptId = localIdCounter.incrementAndGet()
            val stream = AdbStream(this, attemptId, service)
            streams[attemptId] = stream
            try {
                sendPacket(AdbPacket.A_OPEN, attemptId, 0,
                    service.toByteArray(Charsets.US_ASCII))
                stream.waitOkay(timeoutMs)
                if (stream.okayArrived) {
                    AppLog.i(TAG, "OPEN $service -> local=$attemptId remote=${stream.remoteId}")
                    return stream
                }
                streams.remove(attemptId)
                lastError = IOException("OPEN $service timeout (attempt ${attempt + 1})")
                if (attempt < retries - 1) {
                    AppLog.i(TAG, "open $service retry ${attempt + 1}")
                    Thread.sleep(800)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                streams.remove(attemptId)
                throw IOException("Interrupted while opening $service")
            } catch (e: UsbTransport.TransportException) {
                streams.remove(attemptId)
                throw e
            }
        }
        throw lastError ?: IOException("Cannot open $service")
    }

    /**
     * Sends an outgoing data packet; returns the number of bytes actually sent.
     * Split by the device-advertised max payload size. One OKAY is attached
     * after each WRTE (matches libadb behavior; harmless for adbd).
     */
    internal fun writeStreamPacket(stream: AdbStream, data: ByteArray, offset: Int, available: Int): Int {
        var chunk = available.coerceAtMost(deviceMaxData)
        chunk = chunk.coerceAtMost(AdbPacket.MAX_PAYLOAD)
        if (chunk <= 0) return 0
        sendPacket(AdbPacket.A_WRTE, stream.localId, stream.remoteId,
            data.copyOfRange(offset, offset + chunk))
        sendPacket(AdbPacket.A_OKAY, stream.localId, stream.remoteId, EMPTY)
        return chunk
    }

    internal fun sendClose(stream: AdbStream) {
        sendPacket(AdbPacket.A_CLSE, stream.localId, stream.remoteId, EMPTY)
    }

    internal fun removeStream(stream: AdbStream) {
        streams.remove(stream.localId)
    }

    /** Opens a shell exec stream (no PTY; raw output merge on stdout/stderr). */
    @JvmOverloads
    fun openShell(command: String, timeoutMs: Long = 5000, retries: Int = 2): AdbStream =
        open("shell:$command", timeoutMs, retries)

    fun close() {
        if (closed) return
        closed = true
        for (s in streams.values) runCatching { s.deliverClose() }
        streams.clear()
        runCatching { transport.close() }
    }

    companion object {
        private val EMPTY = ByteArray(0)
    }
}
