package com.scrcpy.android.scrcpy

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import com.scrcpy.android.adb.AdbStream
import com.scrcpy.android.data.AudioCodec
import com.scrcpy.android.util.AppLog
import java.nio.ByteBuffer

/**
 * Decodes/plays the scrcpy audio stream.
 *
 * Layout:
 *   [ 4B codec id ]              (or disable code 0 = video-only, 1 = fatal)
 *   [ 12B meta + payload ]*      (ptsAndFlags bit62 = CONFIG packet)
 *
 * For raw codec the payload is PCM16 stereo 48 kHz; no config packet exists.
 */
class AudioDecoder(
    private val stream: AdbStream,
    private val codec: AudioCodec,
) {
    private val TAG = "Audio"

    @Volatile
    private var running = true
    private var thread: Thread? = null

    fun start() {
        thread = Thread({ run() }, "scrcpy-audio").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
    }

    private fun run() {
        // First 4 bytes: codec id or a "disable stream" code.
        val first = readExact(4)
        val id = be32(first, 0)
        if (id == DISP_DISABLE) {
            AppLog.i(TAG, "audio stream disabled by phone (video-only)")
            return
        }
        if (id == DISP_FATAL) {
            AppLog.e(TAG, "audio stream fatal on phone: disconnecting")
            throw java.io.IOException("audio stream fatal")
        }
        if (id != codec.codecId) {
            AppLog.w(TAG, String.format("audio codec id %08x != expected %08x", id, codec.codecId))
        }

        try {
            when (codec) {
                AudioCodec.RAW -> playRaw()
                else -> decodeCompressed()
            }
        } catch (e: InterruptedException) {
            // shutdown
        } catch (e: Exception) {
            if (running) AppLog.e(TAG, "audio stopped: ${e.message}")
        }
    }

    private fun playRaw() {
        val sampleRate = 48000
        val channels = 2
        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(16 * 1024)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf)
            .build()
        val metaBuf = ByteArray(AdbHeaderStub.FRAME_META_SIZE)
        try {
            track.play()
            while (running) {
                stream.readFully(metaBuf, 0, metaBuf.size)
                val size = be32(metaBuf, 8)
                if (size <= 0 || size > 2 * 1024 * 1024) {
                    AppLog.e(TAG, "bogus audio size $size")
                    break
                }
                val payload = readExact(size)
                track.write(payload, 0, payload.size)
            }
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    private fun decodeCompressed() {
        val metaBuf = ByteArray(AdbHeaderStub.FRAME_META_SIZE)
        var csd: ByteArray? = null

        while (running) {
            stream.readFully(metaBuf, 0, metaBuf.size)
            val ptsAndFlags = be64(metaBuf, 0)
            val size = be32(metaBuf, 8)
            if (size <= 0 || size > 2 * 1024 * 1024) {
                AppLog.e(TAG, "bogus audio size $size")
                break
            }
            val payload = readExact(size)
            val isConfig = (ptsAndFlags and VideoDecoder.PTS_FLAG_CONFIG) != 0L
            if (isConfig) {
                csd = payload
                continue
            }
            csd ?: break // frames before any config: cannot decode safely
            playWithMediaCodec(csd, payload)
        }
    }

    // MediaCodec decode path for opus/aac/flac; one persistent decoder + AudioTrack.
    private var mediaCodec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private var sampleRate = -1

    private fun playWithMediaCodec(csd: ByteArray, frame: ByteArray) {
        if (mediaCodec == null) {
            // Build decoder + track from csd packet info
            val rate = when (codec) {
                AudioCodec.OPUS -> 48000
                AudioCodec.AAC -> 44100 // real rate announced inside AAC csd; corrected on MUSIC_FIRST frame
                AudioCodec.FLAC -> parseFlacSampleRate(csd)
                else -> 48000
            }
            val format = MediaFormat.createAudioFormat(codec.mime, rate, 2)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd))
            val decoder = MediaCodec.createDecoderByType(codec.mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            mediaCodec = decoder

            val minBuf = AudioTrack.getMinBufferSize(
                rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT
            ).coerceAtLeast(32 * 1024)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minBuf)
                .build()
            track.play()
            audioTrack = track
            AppLog.i(TAG, "codec ${codec.id}: rate=$rate")
        }

        val decoder = mediaCodec ?: return
        val index = try {
            decoder.dequeueInputBuffer(20_000)
        } catch (e: IllegalStateException) {
            return
        }
        if (index < 0) return
        decoder.getInputBuffer(index)?.let {
            it.clear()
            it.put(frame)
            decoder.queueInputBuffer(index, 0, frame.size, 0, 0)
        }
        var guard = 0
        while (guard++ < 8) {
            val info = MediaCodec.BufferInfo()
            val out = try {
                decoder.dequeueOutputBuffer(info, 0)
            } catch (e: IllegalStateException) {
                return
            }
            if (out == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val fmt = decoder.outputFormat
                sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                audioTrack?.release()
                val ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT
                ).coerceAtLeast(32 * 1024)
                val track = AudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setChannelMask(if (ch == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(minBuf)
                    .build()
                track.play()
                audioTrack = track
                AppLog.i(TAG, "audio format: $sampleRate Hz, $ch ch")
                continue
            }
            if (out < 0) continue
            val buf = decoder.getOutputBuffer(out) ?: continue
            val track = audioTrack ?: return
            try {
                track.write(buf, info.size, AudioTrack.WRITE_BLOCKING)
            } catch (e: Exception) {
                AppLog.w(TAG, "track write failed: ${e.message}")
            }
            runCatching { decoder.releaseOutputBuffer(out, false) }
        }
    }

    /** FLAC STREAMINFO carries the sample rate as a 20-bit big-endian field at byte 10..12. */
    private fun parseFlacSampleRate(csd: ByteArray): Int {
        if (csd.size < 13) return 48000
        val v = ((csd[10].toInt() and 0x0f) shl 12) or
            ((csd[11].toInt() and 0xff) shl 4) or
            ((csd[12].toInt() and 0xf0) shr 4)
        return if (v <= 0) 48000 else v
    }

    private fun readExact(count: Int): ByteArray {
        val buf = ByteArray(count)
        stream.readFully(buf, 0, count)
        return buf
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
        /** Instead of the codec id, the server may stream codes 0 (video-only) or 1 (fatal). */
        const val DISP_DISABLE = 0
        const val DISP_FATAL = 1
    }
}
