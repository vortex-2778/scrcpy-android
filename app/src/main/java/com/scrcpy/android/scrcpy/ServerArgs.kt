package com.scrcpy.android.scrcpy

import com.scrcpy.android.data.AppSettings
import java.util.Locale
import kotlin.random.Random

/**
 * Builds the scrcpy server v5.0.1 launch command line (options are all `key=value`).
 */
object ServerArgs {

    const val SERVER_VERSION = "5.0.1"
    const val SERVER_PATH = "/data/local/tmp/scrcpy-android-server.jar"

    /** Random 31-bit session id -> "%08x". -1 allowed only server-side; we always send one. */
    fun randomScid(): Int = Random.nextInt(0, 0x7FFFFFFF) and 0x7FFFFFFF

    fun socketName(scid: Int): String = String.format(Locale.US, "scrcpy_%08x", scid)

    /**
     * @param scid           session id
     * @param desktopMode    true = create a virtual display (desktop mode) instead of mirroring
     * @param desktopRes     e.g. "1920x1080/160"
     */
    fun build(settings: AppSettings, scid: Int, desktopMode: Boolean, desktopRes: String): String {
        val parts = mutableListOf<String>()

        parts += String.format(Locale.US, "scid=%08x", scid)
        parts += "tunnel_forward=true"
        parts += "video=true"
        parts += "audio=${settings.audioEnabled}"
        parts += "video_codec=${settings.videoCodec.id}"
        parts += "video_bit_rate=${settings.videoBitRate}"
        if (settings.maxFps in 1..120) parts += "max_fps=${settings.maxFps}"
        if (settings.audioEnabled) {
            parts += "audio_codec=${settings.audioCodec.id}"
        }
        if (settings.controlEnabled) parts += "control=true"
        parts += "stay_awake=${settings.stayAwake}"
        parts += "power_on=${settings.powerOn}"
        parts += "power_off_on_close=${settings.powerOffOnClose}"
        parts += "clipboard_autosync=false"
        parts += "cleanup=true"
        if (settings.keepActive) parts += "keep_active=true"

        if (desktopMode) {
            parts += "new_display=$desktopRes"
            parts += "vd_system_decorations=true"
            parts += "display_ime_policy=local"
        } else {
            if (settings.maxSize > 0) parts += "max_size=${settings.maxSize}"
            parts += "display_id=0"
        }

        val args = parts.joinToString(" ")
        return "CLASSPATH=$SERVER_PATH app_process / com.genymobile.scrcpy.Server $SERVER_VERSION $args"
    }
}
