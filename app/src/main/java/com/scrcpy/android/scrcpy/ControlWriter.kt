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

    // scrcpy ControlMessage ids (ControlMessage.java)
    private val TYPE_INJECT_KEYCODE = 0
    private val TYPE_INJECT_TEXT = 1
    private val TYPE_INJECT_TOUCH_EVENT = 2
    private val TYPE_INJECT_SCROLL_EVENT = 3
    private val TYPE_BACK_OR_SCREEN_ON = 4
    private val TYPE_EXPAND_NOTIFICATION_PANEL = 5
    private val TYPE_EXPAND_SETTINGS_PANEL = 6
    private val TYPE_COLLAPSE_PANELS = 7
    private val TYPE_SET_DISPLAY_POWER = 10
    private val TYPE_ROTATE_DEVICE = 11
    private val TYPE_OPEN_HARD_KEYBOARD_SETTINGS = 15
    private val TYPE_RESET_VIDEO = 17

    // touch action ids (same encoding as android.view.MotionEvent)
    private val ACTION_DOWN = 0

    companion object {
        // pointer ids: the server maps -1 to mouse (hover/scroll) events
        const val MOUSE_POINTER_ID = -1L
        /** real finger on the phone */
        const val TOUCH_POINTER_ID = 0L
    }

    private val lock = Object()

    fun injectKeycode(action: Int, keycode: Int, repeat: Int = 0, metaState: Int = 0) {
        val out = newOut()
        val w = DataOutputStream(out)
        w.writeByte(TYPE_INJECT_KEYCODE)
        w.writeByte(action)
        w.writeInt(keycode)
        w.writeInt(repeat)
        w.writeInt(metaState)
        send(out)
    }

    fun injectText(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val out = newOut()
        val w = DataOutputStream(out)
        w.writeByte(TYPE_INJECT_TEXT)
        w.writeInt(bytes.size)
        w.write(bytes)
        send(out)
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
        val out = newOut()
        val w = DataOutputStream(out)
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
        send(out)
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
        val out = newOut()
        val w = DataOutputStream(out)
        w.writeByte(TYPE_INJECT_SCROLL_EVENT)
        w.writeInt(x)
        w.writeInt(y)
        w.writeShort(screenWidth)
        w.writeShort(screenHeight)
        w.writeShort(i16Fixed(hScroll))
        w.writeShort(i16Fixed(vScroll))
        w.writeInt(buttons)
        send(out)
    }

    fun backOrScreenOn(action: Int) {
        val out = newOut()
        val w = DataOutputStream(out)
        w.writeByte(TYPE_BACK_OR_SCREEN_ON)
        w.writeByte(action)
        send(out)
    }

    fun setDisplayPower(on: Boolean) {
        val out = newOut()
        val w = DataOutputStream(out)
        w.writeByte(TYPE_SET_DISPLAY_POWER)
        w.writeByte(if (on) 1 else 0)
        send(out)
    }

    fun expandNotifications() {
        val out = newOut()
        val w = DataOutputStream(out)
        w.writeByte(TYPE_EXPAND_NOTIFICATION_PANEL); send(out)
    }

    fun expandSettings() {
        val out = newOut()
        val w = DataOutputStream(out)
        w.writeByte(TYPE_EXPAND_SETTINGS_PANEL); send(out)
    }

    fun collapsePanels() {
        val out = newOut()
        val w = DataOutputStream(out)
        w.writeByte(TYPE_COLLAPSE_PANELS); send(out)
    }

    fun rotateDevice() {
        val out = newOut()
        val w = DataOutputStream(out)
        w.writeByte(TYPE_ROTATE_DEVICE); send(out)
    }

    fun openHardKeyboardSettings() {
        val out = newOut()
        val w = DataOutputStream(out)
        w.writeByte(TYPE_OPEN_HARD_KEYBOARD_SETTINGS); send(out)
    }

    fun resetVideo() {
        val out = newOut()
        val w = DataOutputStream(out)
        w.writeByte(TYPE_RESET_VIDEO); send(out)
    }

    /** u16 fixed point conversion as in scrcpy's Binary.u16FixedPointToFloat (inverse). */
    private fun pressureToU16(pressure: Float): Int {
        // server: 0xffff -> 1f else value / 0x10000. Inverse: value * 0xFFFF for 1f.
        return if (pressure >= 1f) 0xffff else (pressure * 0x10000).toInt().coerceIn(0, 0xfffe)
    }

    /** i16 fixed point: server computes i16FixedPointToFloat(raw) * 16 -> raw = v * 32768 / 16. */
    private fun i16Fixed(value: Float): Int =
        ((value / 16f) * 0x8000).toInt().coerceIn(-0x8000, 0x7fff) and 0xffff

    private fun newOut() = ByteArrayOutputStream()

    private fun send(out: ByteArrayOutputStream) {
        val bytes = out.toByteArray()
        synchronized(lock) {
            stream.write(bytes)
        }
    }
}
