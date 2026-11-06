package com.scrcpy.android.mirror

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Whether the player overlay (chips + buttons) is currently visible. */
object PlayerOverlayState {
    var visible by mutableStateOf(true)
}
