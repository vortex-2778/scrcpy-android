package com.scrcpy.android.mirror

import com.scrcpy.android.scrcpy.ControlWriter

/**
 * Routes display-power changes so we also know whether the phone screen is
 * currently off (remote BACK then acts as "wake the phone").
 */
object PhoneDisplayState {

    @Volatile
    var off: Boolean = false

    fun apply(writer: ControlWriter?, on: Boolean) {
        off = !on
        writer?.setDisplayPower(on)
    }
}
