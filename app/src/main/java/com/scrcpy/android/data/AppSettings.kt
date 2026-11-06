package com.scrcpy.android.data

import android.content.Context
import android.content.SharedPreferences

/** Video codec negotiated with the scrcpy server. */
enum class VideoCodec(val id: String, val mime: String, val codecId: Int) {
    H264("h264", "video/avc", 0x68323634),
    H265("h265", "video/hevc", 0x68323635);
}

/** Audio codec negotiated with the scrcpy server. */
enum class AudioCodec(val id: String, val mime: String, val codecId: Int) {
    OPUS("opus", "audio/opus", 0x6f707573),
    AAC("aac", "audio/mp4a-latm", 0x00616163),
    FLAC("flac", "audio/flac", 0x666c6163),
    RAW("raw", "audio/raw", 0x00726177);

    companion object {
        fun fromId(name: String): AudioCodec = entries.first { it.id == name }
    }
}

/**
 * Simple SharedPreferences-backed settings store.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("scrcpy_android", Context.MODE_PRIVATE)

    val adbKey: String?
        get() = prefs.getString("adb_key", null)

    fun setAdbKey(value: String) {
        prefs.edit().putString("adb_key", value).apply()
    }

    var videoCodec: VideoCodec
        get() = runCatching {
            VideoCodec.valueOf(prefs.getString("video_codec", null) ?: VideoCodec.H264.name)
        }.getOrDefault(VideoCodec.H264)
        set(value) = prefs.edit().putString("video_codec", value.name).apply()

    var audioEnabled: Boolean
        get() = prefs.getBoolean("audio_enabled", true)
        set(value) = prefs.edit().putBoolean("audio_enabled", value).apply()

    var audioCodec: AudioCodec
        get() = runCatching {
            AudioCodec.valueOf(prefs.getString("audio_codec", null) ?: AudioCodec.OPUS.name)
        }.getOrDefault(AudioCodec.OPUS)
        set(value) = prefs.edit().putString("audio_codec", value.name).apply()

    /** bits per second, 1_000_000 - 60_000_000 */
    var videoBitRate: Int
        get() = clampInt(prefs.getInt("video_bit_rate", 8_000_000), 500_000, 60_000_000)
        set(value) = prefs.edit().putInt("video_bit_rate", clampInt(value, 500_000, 60_000_000)).apply()

    /** 0 = uncapped */
    var maxFps: Int
        get() = clampInt(prefs.getInt("max_fps", 0), 0, 120)
        set(value) = prefs.edit().putInt("max_fps", clampInt(value, 0, 120)).apply()

    /** Downscale to at most maxSize px; 0 = native resolution */
    var maxSize: Int
        get() = clampInt(prefs.getInt("max_size", 0), 0, 3840)
        set(value) = prefs.edit().putInt("max_size", clampInt(value, 0, 3840)).apply()

    var controlEnabled: Boolean
        get() = prefs.getBoolean("control_enabled", true)
        set(value) = prefs.edit().putBoolean("control_enabled", value).apply()

    /**
     * When true, the phone screen is turned off (-while streaming) right after the
     * stream is established: classic scrcpy --stay-awake + display power off mode.
     */
    var turnScreenOff: Boolean
        get() = prefs.getBoolean("turn_screen_off", false)
        set(value) = prefs.edit().putBoolean("turn_screen_off", value).apply()

    var stayAwake: Boolean
        get() = prefs.getBoolean("stay_awake", false)
        set(value) = prefs.edit().putBoolean("stay_awake", value).apply()

    var powerOffOnClose: Boolean
        get() = prefs.getBoolean("power_off_on_close", false)
        set(value) = prefs.edit().putBoolean("power_off_on_close", value).apply()

    /** Turn the phone screen back on at start (scrcpy power_on option). */
    var powerOn: Boolean
        get() = prefs.getBoolean("power_on", true)
        set(value) = prefs.edit().putBoolean("power_on", value).apply()

    var keepActive: Boolean
        get() = prefs.getBoolean("keep_active", false)
        set(value) = prefs.edit().putBoolean("keep_active", value).apply()

    companion object {
        private fun clampInt(value: Int, min: Int, max: Int): Int =
            if (value < min) min else if (value > max) max else value
    }
}
