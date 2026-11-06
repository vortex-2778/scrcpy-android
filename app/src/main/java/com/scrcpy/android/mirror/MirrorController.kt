package com.scrcpy.android.mirror

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.scrcpy.android.AdbKeys
import com.scrcpy.android.App
import com.scrcpy.android.adb.UsbTransport
import com.scrcpy.android.data.DisplayMode
import com.scrcpy.android.scrcpy.MirrorControllerBridge
import com.scrcpy.android.scrcpy.ScrcpySession
import com.scrcpy.android.scrcpy.ServerArgs
import com.scrcpy.android.usb.UsbMonitor
import com.scrcpy.android.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * App-level façade owning the single mirroring session.
 *
 * A plain object (no ViewModel); state is exposed via StateFlow and the UI
 * collects it with collectAsState().
 */
object MirrorController {

    /** High-level connection state. */
    sealed interface State {
        data object Idle : State
        /** Handshaking / pushing server / launching. [step] is a human message. */
        data class Connecting(val step: String) : State
        /** Video/audio/control sockets open and decoders running. */
        data class Streaming(val session: ScrcpySession) : State
        /** Terminal problem shown to the user. */
        data class Failed(val message: String) : State
    }

    data class PhoneStatus(
        val batteryPercent: Int? = null,
        /** tenth of a degree as reported by dumpsys */
        val temperatureTenth: Int? = null,
    ) {
        val temperatureCelsius: Int?
            get() = temperatureTenth?.let { (it + 50) / 100 }
    }

    private val TAG = "Mirror"

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val _phoneStatus = MutableStateFlow(PhoneStatus())
    val phoneStatus: StateFlow<PhoneStatus> = _phoneStatus

    @Volatile
    private var session: ScrcpySession? = null

    init {
        MirrorControllerBridge.errorHandler = { message ->
            AppLog.e(TAG, message)
            if (_state.value is State.Streaming || _state.value is State.Connecting) {
                disconnect()
                _state.value = State.Failed(message)
            }
        }
    }

    fun startConnection() {
        if (_state.value is State.Connecting) return
        val settings = App.singleton.settings
        val mode = settings.displayMode
        val desktopRes = settings.desktopRes
        val run = runnableDevice() ?: run {
            _state.value = State.Failed("No phone with an ADB interface; plug in the USB cable")
            return
        }
        val context = App.singleton
        if (!run.manager.hasPermission(run.device)) {
            UsbMonitor.requestPermission(context, run.device)
            _state.value = State.Failed("Grant the USB debugging permission and try again")
            return
        }

        // Stop any previous session first: the ADB interface can only be claimed once.
        disconnect()
        _state.value = State.Connecting("Opening USB transport…")

        Thread({
            fun openSession(desktop: Boolean): ScrcpySession {
                val connection = run.manager.openDevice(run.device)
                    ?: throw IllegalStateException("openDevice returned null")
                val transport = UsbTransport.open(run.device, connection)
                return try {
                    ScrcpySession(
                        run.device, connection, transport,
                        App.singleton.settings,
                        ServerArgs.randomScid(),
                        desktop, desktopRes
                    ) { event ->
                        when {
                            event == "SERVER_EXITED" -> {
                                AppLog.e(TAG, "server process terminated")
                                disconnect()
                                _state.value =
                                    State.Failed("Mirroring ended: the phone closed the connection")
                            }
                            _state.value is State.Connecting ->
                                _state.value = State.Connecting(event)
                        }
                    }
                } catch (t: Throwable) {
                    runCatching { transport.close() }
                    throw t
                }
            }

            try {
                val newSession = when (mode) {
                    DisplayMode.MIRROR -> openSession(false)
                    DisplayMode.DESKTOP -> openSession(true)
                    DisplayMode.AUTO -> {
                        try {
                            openSession(true)
                        } catch (t: Throwable) {
                            val msg = t.message ?: ""
                            if (msg.contains("localabstract", true) ||
                                msg.contains("not supported", true)
                            ) {
                                AppLog.w(TAG, "desktop mode unavailable, falling back to mirror")
                                _state.value = State.Connecting("Desktop unsupported; mirroring…")
                                openSession(false)
                            } else {
                                throw t
                            }
                        }
                    }
                }
                session = newSession
                val scidHex = String.format(Locale.US, "%08x", newSession.scid)
                AppLog.i(TAG, "streaming established scid=$scidHex")
                _state.value = State.Streaming(newSession)
            } catch (t: Throwable) {
                AppLog.e(TAG, "connect failed: ${t.message}")
                _state.value = State.Failed(smartMessage(t))
            }
        }, "mirror-connect").apply { isDaemon = true; start() }
    }

    private fun smartMessage(t: Throwable): String {
        val base = t.message ?: t.javaClass.simpleName
        return when {
            base.contains("reboot, in adb", true) || base.contains("unauthorized", true) ->
                "Phone rejected the ADB key — accept the USB debugging prompt (or revoke and reconnect)"
            base.contains("timeout", true) ->
                "Timeout: no response from phone ($base)"
            else -> "Connection failed: $base"
        }
    }

    fun disconnect() {
        val s = session
        session = null
        if (_state.value is State.Streaming) _state.value = State.Idle
        s?.stop()
    }

    fun clearError() {
        if (_state.value is State.Failed) _state.value = State.Idle
    }

    /** Called by UsbMonitor when the phone is physically unplugged. */
    fun onUsbDisconnected() {
        if (_state.value is State.Streaming || _state.value is State.Connecting) {
            disconnect()
            _state.value = State.Failed("Phone disconnected from USB")
        }
    }

    /** Toggles the phone display power through the control socket. */
    fun setPhoneDisplay(on: Boolean) {
        val writer = (state.value as? State.Streaming)?.session?.controlWriter ?: return
        PhoneDisplayState.apply(writer, on)
    }

    fun wakePhoneScreen() {
        val writer = (state.value as? State.Streaming)?.session?.controlWriter ?: return
        writer.backOrScreenOn(android.view.KeyEvent.ACTION_UP)
        PhoneDisplayState.off = false
    }

    // ---- battery/temp publish (from DeviceInfoPoller) ----

    fun publishBattery(level: Int?, temperatureTenth: Int?) {
        val cur = _phoneStatus.value
        _phoneStatus.value = cur.copy(
            batteryPercent = level ?: cur.batteryPercent,
            temperatureTenth = temperatureTenth ?: cur.temperatureTenth
        )
    }

    // ---- helpers ----

    data class RunnableDevice(val device: UsbDevice, val manager: UsbManager)

    private fun runnableDevice(): RunnableDevice? {
        val context = App.singleton
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = manager.deviceList.values.firstOrNull { UsbTransport.hasAdbInterface(it) }
        return if (device != null) RunnableDevice(device, manager) else null
    }
}
