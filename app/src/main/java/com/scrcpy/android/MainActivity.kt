package com.scrcpy.android

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import com.scrcpy.android.mirror.MirrorController
import com.scrcpy.android.mirror.PhoneDisplayState
import com.scrcpy.android.scrcpy.ControlWriter
import com.scrcpy.android.ui.screens.HomeScreen
import com.scrcpy.android.ui.screens.LogScreen
import com.scrcpy.android.ui.screens.PlayerScreen
import com.scrcpy.android.ui.screens.SettingsScreen
import com.scrcpy.android.ui.theme.ScrcpyAndroidTheme
import com.scrcpy.android.util.AppLog
import kotlin.math.roundToInt

/**
 * Single-activity app: Home/Settings/Log screens + fullscreen Player.
 *
 * Input interception happens here so Compose navigation cannot misroute:
 * key/mouse events are forwarded to the phone only while the player is open
 * and the session has a control channel.
 */
class MainActivity : ComponentActivity() {

    enum class Screen { HOME, PLAYER, SETTINGS, LOG }

    object Navigator {
        val current = mutableIntStateOf(Screen.HOME.ordinal)

        fun screen(): Screen = Screen.entries[current.intValue]
        fun open(s: Screen) {
            current.intValue = s.ordinal
        }

        fun isPlayer(): Boolean = screen() == Screen.PLAYER
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isTvDevice()) {
            hideSystemBars()
        }
        AppLog.i("Ui", "activity start (tv=${isTvDevice()})")

        setContent {
            ScrcpyAndroidTheme {
                val screen by Navigator.current
                when (Screen.entries[screen]) {
                    Screen.PLAYER -> PlayerScreen(
                        onExit = {
                            Navigator.open(Screen.HOME)
                            MirrorController.disconnect()
                        }
                    )
                    Screen.SETTINGS -> SettingsScreen(onBack = { Navigator.open(Screen.HOME) })
                    Screen.LOG -> LogScreen(onBack = { Navigator.open(Screen.HOME) })
                    Screen.HOME -> HomeScreen(
                        onOpenPlayer = { Navigator.open(Screen.PLAYER) },
                        onOpenSettings = { Navigator.open(Screen.SETTINGS) },
                        onOpenLog = { Navigator.open(Screen.LOG) }
                    )
                }
            }
        }
    }

    private fun isTvDevice(): Boolean {
        val uim = getSystemService(UiModeManager::class.java) ?: return false
        return uim.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun hideSystemBars() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    // ---------------- input forwarding ----------------

    private fun controlWriter(): ControlWriter? =
        (MirrorController.state.value as? MirrorController.State.Streaming)?.session?.controlWriter

    private fun scope(): Pair<Int, Int>? {
        if (!Navigator.isPlayer()) return null
        val session = (MirrorController.state.value as? MirrorController.State.Streaming)?.session
            ?: return null
        val m = session.videoDecoder.meta
        if (m.width <= 0 || m.height <= 0) return null
        return m.width to m.height
    }

    /**
     * D-pad/IR keyboard → phone keys while the player is open.
     * Remote BACK exits the player; if the phone display is off, BACK wakes it.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (Navigator.isPlayer()) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (PhoneDisplayState.off) {
                        MirrorController.wakePhoneScreen()
                    } else {
                        MirrorController.disconnect()
                        Navigator.open(Screen.HOME)
                    }
                } else {
                    controlWriter()?.injectKeycode(
                        KeyEvent.ACTION_DOWN, event.keyCode,
                        event.repeatCount, event.metaState
                    )
                }
                KeyEvent.ACTION_UP -> if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    // handled above on DOWN (wake/home switch)
                } else {
                    controlWriter()?.injectKeycode(
                        KeyEvent.ACTION_UP, event.keyCode, 0, event.metaState
                    )
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Mouse (hover/click/scroll) and touch mapped into the phone coordinate space. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val (w, h) = scope() ?: return super.dispatchGenericMotionEvent(event)
        val x = (event.x / viewWidth() * w).roundToInt()
        val y = (event.y / viewHeight() * h).roundToInt()
        val writer = controlWriter()
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE -> {
                writer?.injectTouch(
                    MotionEvent.ACTION_HOVER_MOVE, ControlWriter.MOUSE_POINTER_ID,
                    x, y, w, h, 0f
                )
                return true
            }
            MotionEvent.ACTION_SCROLL -> {
                val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                val hsc = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                writer?.injectScroll(x, y, w, h, hsc, -v)
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                val button = event.actionButton
                if (button != 0) {
                    writer?.injectTouch(
                        MotionEvent.ACTION_DOWN, ControlWriter.MOUSE_POINTER_ID,
                        x, y, w, h, 1f, button, button
                    )
                    return true
                }
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                if (event.actionButton != 0) {
                    writer?.injectTouch(
                        MotionEvent.ACTION_UP, ControlWriter.MOUSE_POINTER_ID,
                        x, y, w, h, 0f, event.actionButton, 0
                    )
                    return true
                }
            }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val action = event.actionMasked
                val pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f
                writer?.injectTouch(
                    action, ControlWriter.MOUSE_POINTER_ID, x, y, w, h, pressure,
                    0, if (action == MotionEvent.ACTION_UP) 0 else 1
                )
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /** Touchscreen input on phones (fire-tablets, non-TV debugging) too. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Compose receives touch for focus navigation; mirroring input happens above.
        return super.dispatchTouchEvent(ev)
    }

    private fun viewWidth(): Int {
        val r = window.decorView.width
        return if (r > 0) r else 1920
    }

    private fun viewHeight(): Int {
        val r = window.decorView.height
        return if (r > 0) r else 1080
    }
}
