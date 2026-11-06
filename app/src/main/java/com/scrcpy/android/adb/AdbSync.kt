package com.scrcpy.android.adb

import java.io.IOException

/**
 * ADB file-sync protocol ('sync:' service) v1 push implementation.
 *
 * Framing is little-endian: [ "SEND" | "DATA" | "DONE" | "OKAY" | "FAIL" ][ u32 len ][ payload ]
 */
object AdbSync {

    private val SEND = bytes("SEND")
    private val DATA = bytes("DATA")
    private val DONE = bytes("DONE")
    private val OKAY = bytes("OKAY")
    private val FAIL = bytes("FAIL")

    fun push(stream: AdbStream, data: ByteArray, remotePath: String, mode: String = "0644") {
        val header = (remotePath + "," + mode).toByteArray(Charsets.UTF_8) + 0

        // SEND <len> <path>,<mode>\0
        chunk(stream, SEND, header)

        // DATA chunks of up to 64 KiB
        var sent = 0
        while (sent < data.size) {
            val n = minOf(65536, data.size - sent)
            chunk(stream, DATA, data.copyOfRange(sent, sent + n))
            sent += n
        }

        // DONE <mtime?>
        chunk(stream, DONE, ByteArray(4))

        // status
        val status = readChunkHeader(stream)   // 8 bytes: id + len
        val id0 = status[0]
        when {
            id0 == OKAY[0] && matches(status, OKAY) -> return
            id0 == FAIL[0] && matches(status, FAIL) -> {
                val len = le32(status, 4)
                val payload = ByteArray(len)
                stream.readFully(payload, 0, len)
                throw IOException("Push failed: " + String(payload, Charsets.UTF_8).trim('\u0000'))
            }
            else -> throw IOException("Unexpected sync id after DONE: ${String(status, Charsets.UTF_8)}")
        }
    }

    private fun chunk(stream: AdbStream, id: ByteArray, payload: ByteArray) {
        val buf = ByteArray(8 + payload.size)
        id.copyInto(buf, 0)
        le32(buf, 4, payload.size)
        payload.copyInto(buf, 8)
        stream.write(buf)
    }

    private fun readChunkHeader(stream: AdbStream): ByteArray {
        val b = ByteArray(8)
        stream.readFully(b, 0, 8)
        return b
    }

    private fun le32(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xff) or
            ((buf[off + 1].toInt() and 0xff) shl 8) or
            ((buf[off + 2].toInt() and 0xff) shl 16) or
            ((buf[off + 3].toInt() and 0xff) shl 24)

    private fun le32(buf: ByteArray, off: Int, value: Int) {
        buf[off] = (value and 0xff).toByte()
        buf[off + 1] = ((value shr 8) and 0xff).toByte()
        buf[off + 2] = ((value shr 16) and 0xff).toByte()
        buf[off + 3] = ((value shr 24) and 0xff).toByte()
    }

    private fun matches(have: ByteArray, want: ByteArray) = have[0] == want[0]

    private fun bytes(s: String) = s.toByteArray(Charsets.US_ASCII)
}
