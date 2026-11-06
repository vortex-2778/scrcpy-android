package com.scrcpy.android.scrcpy

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import com.scrcpy.android.adb.AdbConnection
import com.scrcpy.android.adb.AdbStream
import com.scrcpy.android.adb.AdbSync
import com.scrcpy.android.adb.UsbTransport
import com.scrcpy.android.AdbKeys
import com.scrcpy.android.data.AppSettings
import com.scrcpy.android.util.AppLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One scrcpy session: owns the ADB stack for the phone.
 *
 * Steps (all synchronous in the constructor):
 *  1. ADB handshake (CNXN/AUTH) over the provided transport
 *  2. push scrcpy-server.jar to /data/local/tmp via `sync:`
 *  3. launch it: `app_process / com.genymobile.scrcpy.Server 5.0.1 ...`
 *  4. open localabstract:scrcpy_<scid> sockets in order: video, audio, control
 *  5. start decoders + battery poller
 *
 * Progress messages are reported via [onEvent]; failures throw.
 */
class ScrcpySession(
    val device: UsbDevice,
    private val usbConnection: UsbDeviceConnection,
    private val transport: UsbTransport,
    private val settings: AppSettings,
    val scid: Int,
    private val desktopMode: Boolean,
    private val desktopRes: String,
    private val onEvent: (String) -> Unit = {},
) {
    val TAG = "Session"

    val adb: AdbConnection
    val videoDecoder: VideoDecoder
    val audioDecoder: AudioDecoder?
    val controlWriter: ControlWriter?

    private val stopped = AtomicBoolean(false)
    private var serverThread: Thread? = null
    private var infoPoller: DeviceInfoPoller? = null
    private var shellStream: AdbStream? = null

    init {
        onEvent("ADB handshake…")
        adb = AdbConnection(
            transport,
            { AdbKeys.keyPair() },
            { AdbKeys.publicKeyPayload() }
        )
        adb.handshake()
        adb.start()

        onEvent("Pushing scrcpy server…")
        pushServer()

        val cmd = ServerArgs.build(settings, scid, desktopMode, desktopRes)
        onEvent("Launching server…")
        serverThread = Thread({
            watchServerProcess(cmd)
        }, "scrcpy-server").apply { isDaemon = true; start() }

        // Open the localabstract sockets; the server accepts them in order video→audio→control.
        val socketName = ServerArgs.socketName(scid)

        val videoStream = adb.open("localabstract:$socketName", timeoutMs = 5_000, retries = 6)
        AppLog.i(TAG, "video socket connected")
        videoDecoder = VideoDecoder(videoStream, settings.videoCodec.mime)

        val audioStream = if (settings.audioEnabled) {
            runCatching {
                adb.open("localabstract:$socketName", timeoutMs = 4_000, retries = 25)
            }.getOrNull()
        } else {
            null
        }
        audioDecoder = audioStream?.let { AudioDecoder(it, settings.audioCodec) }

        controlWriter = runCatching {
            adb.open("localabstract:$socketName", timeoutMs = 4_000, retries = 25)
        }.getOrNull()?.let { ControlWriter(it) }

        AppLog.i(TAG, "starting decoders")
        videoDecoder.start()
        audioDecoder?.start()

        if (settings.turnScreenOff && controlWriter != null) {
            AppLog.i(TAG, "turning phone display off; stream continues")
            com.scrcpy.android.mirror.PhoneDisplayState.apply(controlWriter, false)
        }

        infoPoller = DeviceInfoPoller(this)
        infoPoller?.start()
    }

    /** Pushes the packaged server jar to /data/local/tmp via ADB sync. */
    private fun pushServer() {
        val assetBytes = com.scrcpy.android.AdbServerAsset.loadServerJar()
        val pushStream = adb.open("sync:")
        AdbSync.push(pushStream, assetBytes, ServerArgs.SERVER_PATH)
        AppLog.i(TAG, "server.jar pushed (${assetBytes.size} bytes)")
        runCatching { pushStream.close() }
    }

    private fun watchServerProcess(cmd: String) {
        try {
            val shell = adb.openShell(cmd, timeoutMs = 30_000)
            shellStream = shell
            AppLog.i(TAG, "server process launched")
            val buf = ByteArray(2048)
            while (!shell.isClosed && !stopped.get()) {
                try {
                    shell.readFully(buf, 0, buf.size)
                } catch (e: Exception) {
                    if (!stopped.get()) {
                        AppLog.w(TAG, "server connection ended: ${e.message}")
                        onEvent("SERVER_EXITED")
                    }
                    break
                }
                val text = String(buf, Charsets.UTF_8).trim('\u0000')
                if (text.isNotBlank()) {
                    text.split('\n').filter { it.isNotBlank() }.forEach {
                        AppLog.i("phone", it.take(180))
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "shell launch failed: ${e.message}")
            onEvent("Could not launch server: ${e.message}")
        }
    }

    fun setSurface(surface: android.view.Surface?) {
        videoDecoder.surface = surface
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        AppLog.i(TAG, "stopping session")
        videoDecoder.stop()
        audioDecoder?.stop()
        infoPoller?.stop()
        runCatching { shellStream?.close() }
        runCatching { adb.close() }
        serverThread?.interrupt()
    }
}

/**
 * Error bridge so session threads can report into MirrorController without
 * a package-cycle dependency.
 */
object MirrorControllerBridge {
    @Volatile
    var errorHandler: ((String) -> Unit)? = null

    fun onError(message: String) {
        errorHandler?.invoke(message)
    }
}
