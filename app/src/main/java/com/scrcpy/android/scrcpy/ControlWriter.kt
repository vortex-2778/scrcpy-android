package com.scrcpy.android.scrcpy

import com.scrcpy.android.adb.AdbStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Writes control messages to the scrcpy server over the control ADB stream.
 *
 * Wire format (big-endian):
 *   [ type: u8 ][ per-type fields ]
 *
 * Types implemented (scrcpy ControlMessage.java):
 *   0 INJECT_KEYCODE  1 INJECT_TEXT   2 INJECT_TOUCH   3 INJECT_SCROLL
 *   4 BACK_OR_SCREEN_ON 5/6 EXPAND_*  7 COLLAPSE_PANELS
 *   9 SET_CLIPBOARD  10 SET_DISPLAY_POWER 11 ROTATE_DEVICE
 *   15 OPEN_HARD_KEYBOARD_SETTINGS 17 RESET_VIDEO
 */
class ControlWriter(private val stream: AdbStream) {

    // scrcpy ControlMessage ids
    val TYPE_INJECT_KEYCODE = 0
    val TYPE_INJECT_TOUCH_EVENT = 2
    val TYPE_INJECT_SCROLL_EVENT = 3
    val TYPE_BACK_OR_SCREEN_ON = 4
    val TYPE_EXPAND_NOTIFICATION_PANEL = 5
    val TYPE_EXPAND_SETTINGS_PANEL = 6
    val TYPE_COLLAPSE_PANELS = 7
    val TYPE_SET_DISPLAY_POWER = 10
    val TYPE_ROTATE_DEVICE = 11
    val TYPE_OPEN_HARD_KEYBOARD_SETTINGS = 15
    val TYPE_RESET_VIDEO = 17

    // pointer ids used by the server for mouse emulation / touch
    val POINTER_ID_MOUSE = -1L

    const val MOUSE_POINTER_ID = -1L
    const val TOUCH_POINTER_ID = 0L

    // touch action ids
    val ACTION_DOWN = 0
    val ACTION_UP = 1
    val ACTION_MOVE = 2
    val ACTION_HOVER_MOVE = 7

    private val lock = Object()

    fun injectKeycode(action: Int, keycode: Int, repeat: Int = 0, metaState: Int = 0) {
        val w = newWriter()
        w.writeByte(TYPE_INJECT_KEYCODE)
        w.writeByte(action)
        w.writeInt(keycode)
        w.writeInt(repeat)
        w.writeInt(metaState)
        send(w)
    }

    fun injectText(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val w = newWriter()
        w.writeByte(1)
        w.writeInt(bytes.size)
        w.write(bytes)
        send(w)
    }

    /**
     * Injects a touch/mouse event.
     * position is expressed in the device coordinate space (phone resolution).
     */
    fun injectTouch(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float,
        actionButton: Int = 0,
        buttons: Int = 0,
    ) {
        val w = newWriter()
        w.writeByte(TYPE_INJECT_TOUCH_EVENT)
        w.writeByte(action)
        w.writeLong(pointerId)
        w.writeInt(x)
        w.writeInt(y)
        w.writeShort(screenWidth)
        w.writeShort(screenHeight)
        w.writeShort(pressureToU16(pressure))
        w.writeInt(actionButton)
        w.writeInt(buttons)
        send(w)
    }

    fun injectScroll(
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        hScroll: Float,
        vScroll: Float,
        buttons: Int = 0,
    ) {
        val w = newWriter()
        w.writeByte(TYPE_INJECT_SCROLL_EVENT)
        w.writeInt(x)
        w.writeInt(y)
        w.writeShort(screenWidth)
        w.writeShort(screenHeight)
        w.writeShort(i16Fixed(hScroll))
        w.writeShort(i16Fixed(vScroll))
        w.writeInt(buttons)
        send(w)
    }

    fun backOrScreenOn(action: Int) {
        val w = newWriter()
        w.writeByte(TYPE_BACK_OR_SCREEN_ON)
        w.writeByte(action)
        send(w)
    }

    fun setDisplayPower(on: Boolean) {
        val w = newWriter()
        w.writeByte(TYPE_SET_DISPLAY_POWER)
        w.writeByte(if (on) 1 else 0)
        send(w)
    }

    fun expandNotifications() {
        val w = newWriter(); w.writeByte(TYPE_EXPAND_NOTIFICATION_PANEL); send(w)
    }

    fun expandSettings() {
        val w = newWriter(); w.writeByte(TYPE_EXPAND_SETTINGS_PANEL); send(w)
    }

    fun collapsePanels() {
        val w = newWriter(); w.writeByte(TYPE_COLLAPSE_PANELS); send(w)
    }

    fun rotateDevice() {
        val w = newWriter(); w.writeByte(TYPE_ROTATE_DEVICE); send(w)
    }

    fun openHardKeyboardSettings() {
        val w = newWriter(); w.writeByte(TYPE_OPEN_HARD_KEYBOARD_SETTINGS); send(w)
    }

    fun resetVideo() {
        val w = newWriter(); w.writeByte(TYPE_RESET_VIDEO); send(w)
    }

    /** u16 fixed point conversion as in scrcpy's Binary.u16FixedPointToFloat (inverse). */
    private fun pressureToU16(pressure: Float): Int {
        // server: 0xffff -> 1f else value / 0x10000. Inverse: value * 0xFFFF for 1f.
        return if (pressure >= 1f) 0xffff else (pressure * 0x10000).toInt().coerceIn(0, 0xfffe)
    }

    /** i16 fixed point: server computes i16FixedPointToFloat(raw) * 16 -> raw = v * 32768 / 16. */
    private fun i16Fixed(value: Float): Int =
        ((value / 16f) * 0x8000).toInt().coerceIn(-0x8000, 0x7fff) and 0xffff

    private fun newWriter() = ByteArrayOutputStream().let(::DataOutputStream)

    private fun send(w: DataOutputStream) {
        val bytes = (w.out as ByteArrayOutputStream).toByteArray()
        synchronized(lock) {
            stream.write(bytes)
        }
    }
}
