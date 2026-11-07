package com.scrcpy.android.scrcpy

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.view.Surface
import com.scrcpy.android.adb.AdbStream
import com.scrcpy.android.util.AppLog
import java.nio.ByteBuffer

/**
 * Decodes the scrcpy video stream onto a [Surface].
 *
 * Stream layout (send_device_meta / send_stream_meta / send_frame_meta = true):
 *   [ 64B device name ][ 4B codec id ][ 12B session meta (flags u32, width u32, height u32) ]
 *   then per packet: [ 12B meta (u64 ptsAndFlags BE + u32 size) ][ payload ]
 *
 *   - a meta block whose 32-bit flags word has bit31 set is a session meta
 *     (encode (re)start), not a frame: flags u32 | width u32 | height u32
 *   - frame ptsAndFlags bit62 => CONFIG packet (codec-specific data)
 *   - handling both in one loop makes rotation/resizes work mid-stream
 */
class VideoDecoder(
    private val stream: AdbStream,
    private val mime: String,
) {

    data class Meta(val width: Int, val height: Int)

    var deviceName: String = ""
        private set

    @Volatile
    var meta: Meta = Meta(0, 0)
        private set

    /** Destination surface; set by the player right after construction. */
    @Volatile
    var surface: Surface? = null
        private set

    @Volatile
    private var restartRequested = false

    @Volatile
    var frameCount: Long = 0
        private set

    private val TAG = "Video"
    @Volatile
    private var running = true
    private var thread: Thread? = null

    /**
     * Player surface lifecycle: when the TV app is backgrounded the SurfaceView
     * surface is destroyed; frames keep being consumed but not rendered, and the
     * decoder is recreated when a new surface arrives.
     */
    fun updateSurface(newSurface: Surface?) {
        if (newSurface === surface) return
        surface = newSurface
        if (newSurface != null) restartRequested = true
    }

    fun start() {
        thread = Thread({ run() }, "scrcpy-video").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
    }

    private fun run() {
        try {
            val nameBytes = readExact(AdbHeaderStub.DEVICE_NAME_SIZE)
            deviceName = String(nameBytes, Charsets.UTF_8).trim('\u0000')
            AppLog.i(TAG, "phone: $deviceName")

            readExact(AdbHeaderStub.CODEC_ID_SIZE) // codec id ("h264"/"h265")

            decodeLoop()
        } catch (e: InterruptedException) {
            // normal shutdown
        } catch (e: Exception) {
            if (running) AppLog.e(TAG, "video stopped: ${e.message}")
        }
    }

    private fun decodeLoop() {
        val metaBuf = ByteArray(AdbHeaderStub.SESSION_META_SIZE)
        var codec: MediaCodec? = null
        var pendingCsd: ByteArray? = null
        var lastConfigureAttempt = 0L

        fun releaseCodec() {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            codec = null
        }

        /**
         * (Re)creates the decoder once both the codec config and a surface are
         * available. While the TV app is backgrounded there is no surface: the
         * stream keeps being consumed so the ADB socket stays in sync.
         */
        fun ensureCodec() {
            val csd = pendingCsd ?: return
            val target = surface ?: return
            if (codec != null) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastConfigureAttempt < 1_000) return
            lastConfigureAttempt = now
            try {
                codec = configure(csd, target)
            } catch (t: Throwable) {
                AppLog.e(TAG, "decoder configure failed: ${t.message}")
                releaseCodec()
            }
        }

        try {
            while (running) {
                // Surface gone (activity stopped): release the decoder but keep
                // draining packets so the stream stays aligned.
                if (surface == null && codec != null) releaseCodec()

                if (restartRequested) {
                    restartRequested = false
                    releaseCodec()
                }
                ensureCodec()

                readFully(metaBuf, 0, metaBuf.size)
                val wordAt0 = be32(metaBuf, 0)
                if ((wordAt0 and 0x80000000.toInt()) != 0) {
                    // session meta: encode session (re)started
                    val w = be32(metaBuf, 4)
                    val h = be32(metaBuf, 8)
                    if (w in 1..16384 && h in 1..16384) {
                        meta = Meta(w, h)
                        AppLog.i(TAG, "encode session ${w}x${h}")
                    }
                    // next block is a CONFIG frame declaring the (possibly new) csd
                    continue
                }

                val ptsAndFlags = be64(metaBuf, 0)
                val size = be32(metaBuf, 8)
                if (size <= 0 || size > 16 * 1024 * 1024) {
                    AppLog.e(TAG, "bogus packet size $size")
                    break
                }
                val payload = readExact(size)
                frameCount++

                if ((ptsAndFlags and PTS_FLAG_CONFIG) != 0L) {
                    // New codec-specific data: (re)start the decoder with it.
                    val sameCsd = pendingCsd?.contentEquals(payload) == true
                    if (!sameCsd) {
                        releaseCodec()
                        pendingCsd = payload
                    }
                    ensureCodec()
                } else if (codec != null) {
                    submitFrame(codec!!, payload, ptsAndFlags)
                }
            }
        } finally {
            releaseCodec()
        }
    }

    private fun configure(csd: ByteArray, target: Surface): MediaCodec {
        val format = MediaFormat.createVideoFormat(
            mime,
            if (meta.width > 0) meta.width else 1280,
            if (meta.height > 0) meta.height else 720
        )
        if (mime == MediaFormat.MIMETYPE_VIDEO_AVC) {
            // split concatenated SPS+PPS into csd-0/csd-1 by start-code boundaries
            val (sps, pps) = splitAvcCsd(csd)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))
        } else {
            // hevc tolerates the full VPS+SPS+PPS blob in csd-0
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd))
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        }
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(format, target, null, 0)
            codec.start()
            return codec
        } catch (t: Throwable) {
            runCatching { codec.release() }
            throw t
        }
    }

    private fun submitFrame(codec: MediaCodec, data: ByteArray, ptsAndFlags: Long) {
        val index = try {
            codec.dequeueInputBuffer(20_000)
        } catch (e: IllegalStateException) {
            return
        }
        if (index < 0) return
        try {
            codec.getInputBuffer(index)?.let { input ->
                input.clear()
                input.put(data)
                val pts = ptsAndFlags and 0x3fffffffffffffffL
                codec.queueInputBuffer(index, 0, data.size, pts, 0)
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "input dropped: ${e.message}")
            return
        }
        drainOutput(codec)
    }

    private fun drainOutput(codec: MediaCodec) {
        var guard = 0
        while (guard++ < 16) {
            val info = MediaCodec.BufferInfo()
            val out = try {
                codec.dequeueOutputBuffer(info, 0)
            } catch (e: IllegalStateException) {
                return
            }
            if (out == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (out < 0) continue
            runCatching { codec.releaseOutputBuffer(out, info.size > 0) }
        }
    }

    /** Splits concatenated NAL headers (SPS/PPS) by H.264 start codes. */
    private fun splitAvcCsd(csd: ByteArray): Pair<ByteArray, ByteArray> {
        var i = 3
        while (i < csd.size - 4) {
            if (csd[i] == 0.toByte() && csd[i + 1] == 0.toByte() && csd[i + 2] == 1.toByte()) {
                val nalType = csd[i + 3].toInt() and 0x1f
                if (nalType == 8) {
                    // 00 00 01 68 found: csd[0..i-1] = SPS, csd[i..] = PPS
                    return csd.copyOf(i) to csd.copyOfRange(i, csd.size)
                }
            }
            i++
        }
        return csd to csd
    }

    private fun readExact(count: Int): ByteArray {
        val buf = ByteArray(count)
        stream.readFully(buf, 0, count)
        return buf
    }

    private fun readFully(buf: ByteArray, off: Int, len: Int) {
        stream.readFully(buf, off, len)
    }

    private fun be32(b: ByteArray, off: Int): Int {
        var res = 0
        for (i in 0 until 4) res = (res shl 8) or (b[off + i].toInt() and 0xff)
        return res
    }

    private fun be64(b: ByteArray, off: Int): Long {
        var res = 0L
        for (i in 0 until 8) res = (res shl 8) or (b[off + i].toLong() and 0xff)
        return res
    }

    companion object {
        const val PTS_FLAG_CONFIG = 1L shl 62
        const val PTS_FLAG_KEY_FRAME = 1L shl 61
        const val PTS_FLAG_SESSION = 1L shl 63
    }
}

/** Stream framing constants. */
object AdbHeaderStub {
    const val DEVICE_NAME_SIZE = 64
    const val CODEC_ID_SIZE = 4
    const val SESSION_META_SIZE = 12
    const val FRAME_META_SIZE = 12
}
