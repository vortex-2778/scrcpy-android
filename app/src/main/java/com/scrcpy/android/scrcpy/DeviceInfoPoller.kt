package com.scrcpy.android.scrcpy

import com.scrcpy.android.mirror.MirrorController
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Periodically reads battery level/temperature from the phone with
 * `dumpsys battery` and publishes it in MirrorController.
 */
class DeviceInfoPoller(private val session: ScrcpySession) {

    private val TAG = "Poller"
    private val stopped = AtomicBoolean(false)
    private var thread: Thread? = null

    fun start() {
        thread = Thread({ run() }, "scrcpy-poll").apply { isDaemon = true; start() }
    }

    fun stop() {
        stopped.set(true)
        thread?.interrupt()
    }

    private fun run() {
        var i = 0
        while (!stopped.get() && !session.adb.isClosed) {
            try {
                val shell = session.adb.openShell("dumpsys battery", timeoutMs = 5000, retries = 1)
                val output = readAll(shell, 32768)
                runCatching { shell.close() }
                val level = Regex("level:\\s*(\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()
                val tempTenth = Regex("temperature:\\s*(\\d+)").find(output)
                    ?.groupValues?.get(1)?.toIntOrNull()
                if (level != null || tempTenth != null) {
                    MirrorController.publishBattery(level, tempTenth)
                }
            } catch (e: Exception) {
                if (!stopped.get() && i % 6 == 0) AppLog.w(TAG, "battery poll failed: ${e.message}")
            }
            i++
            try {
                Thread.sleep(10_000)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun readAll(stream: com.scrcpy.android.adb.AdbStream, cap: Int): String {
        val sb = StringBuilder()
        val buf = ByteArray(4096)
        while (sb.length < cap && !stream.isClosed) {
            val n = runCatching {
                stream.readFully(buf, 0, buf.size)
                buf.size
            }.getOrDefault(-1)
            if (n <= 0) break
            sb.append(String(buf, Charsets.UTF_8))
        }
        return sb.toString()
    }
}
