package com.scrcpy.android

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import com.scrcpy.android.mirror.MirrorController
import com.scrcpy.android.mirror.PhoneDisplayState
import com.scrcpy.android.mirror.PlayerOverlayState
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
        hideSystemBars()
        AppLog.i("Ui", "activity start (tv=${isTvDevice()})")

        setContent {
            ScrcpyAndroidTheme {
                val screen by Navigator.current
                AnimatedContent(
                    targetState = Screen.entries[screen],
                    transitionSpec = {
                        // expressive depth: fade + slight vertical settle
                        (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 16 })
                            .togetherWith(fadeOut(tween(150)))
                    },
                    label = "screens"
                ) { target ->
                    when (target) {
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
    }

    /** Disconnect cleanly and close the app (Ctrl+Esc / HOME). */
    private fun exitApplication() {
        AppLog.i("Ui", "exit requested (${if (Navigator.isPlayer()) "player" else "home"})")
        MirrorController.disconnect()
        finishAffinity()
    }

    /**
     * Leaving with the remote HOME button: drop the session so the phone screen
     * is restored and USB is released; reopening the app starts fresh.
     */
    override fun onStop() {
        super.onStop()
        val state = MirrorController.state.value
        if (state is MirrorController.State.Streaming) {
            AppLog.i("Ui", "activity left while streaming -> disconnecting")
            MirrorController.disconnect()
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
     * Exit paths while mirroring (all keys otherwise go to the phone):
     *  - remote HOME / TV launcher button  -> leaves the app (onStop disconnects)
     *  - keyboard Ctrl+Esc                  -> exits immediately from anywhere
     *  - BACK in player                     -> exits the player
     *  - MENU in player                     -> toggles the overlay
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Ctrl+Esc: hard exit from any screen, incl. while forwarding to phone.
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_ESCAPE &&
            event.metaState and KeyEvent.META_CTRL_MASK != 0
        ) {
            exitApplication()
            return true
        }
        // Some STB remotes do deliver HOME keycodes; treat it as an app exit too
        // (most Android TVs deliver HOME to the system instead of key events).
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_HOME
        ) {
            exitApplication()
            return true
        }
        if (Navigator.isPlayer()) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> when (event.keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        if (PhoneDisplayState.off) {
                            MirrorController.wakePhoneScreen()
                        } else {
                            MirrorController.disconnect()
                            Navigator.open(Screen.HOME)
                        }
                    }
                    KeyEvent.KEYCODE_MENU -> {
                        PlayerOverlayState.visible = !PlayerOverlayState.visible
                    }
                    else -> {
                        val writer = controlWriter() ?: return super.dispatchKeyEvent(event)
                        writer.injectKeycode(
                            KeyEvent.ACTION_DOWN, event.keyCode,
                            event.repeatCount, event.metaState
                        )
                    }
                }
                KeyEvent.ACTION_UP -> if (event.keyCode != KeyEvent.KEYCODE_BACK &&
                    event.keyCode != KeyEvent.KEYCODE_MENU
                ) {
                    val writer = controlWriter() ?: return super.dispatchKeyEvent(event)
                    writer.injectKeycode(
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
