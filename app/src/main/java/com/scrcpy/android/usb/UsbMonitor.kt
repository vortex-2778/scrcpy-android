package com.scrcpy.android.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.scrcpy.android.adb.UsbTransport
import com.scrcpy.android.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks ADB-capable phones over USB host and the USB permission flow.
 *
 * States:
 *  - [UsbState.NoDevice]: no phone with an ADB interface plugged.
 *  - [UsbState.DeviceAttached]: phone present but we don't have permission yet.
 *  - [UsbState.Granted]: permission granted and connection usable.
 */
object UsbMonitor {

    sealed interface UsbState {
        data object NoDevice : UsbState
        data class DeviceAttached(val device: UsbDevice) : UsbState
        data class Granted(val device: UsbDevice) : UsbState
    }

    private val _state = MutableStateFlow<UsbState>(UsbState.NoDevice)
    val state: StateFlow<UsbState> = _state

    private const val ACTION_USB_PERM = "com.scrcpy.android.USB_PERMISSION"
    private var appContext: Context? = null

    fun start(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val filter = IntentFilterCompat()
        context.applicationContext.registerReceiver(receiver, filter)
        refresh(context.applicationContext)
    }

    fun refresh(context: Context) {
        val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val adbDevice = usb.deviceList.values.firstOrNull { UsbTransport.hasAdbInterface(it) }
        _state.value = when {
            adbDevice == null -> UsbState.NoDevice
            usb.hasPermission(adbDevice) -> UsbState.Granted(adbDevice)
            else -> UsbState.DeviceAttached(adbDevice)
        }
    }

    /** Requests USB permission; result lands in [state]. */
    fun requestPermission(context: Context, device: UsbDevice) {
        val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val flags = if (android.os.Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERM).setPackage(context.packageName), flags
        )
        usb.requestPermission(device, intent)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERM -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        AppLog.i("Usb", "USB permission granted for ${deviceName(device)}")
                        _state.value = UsbState.Granted(device)
                    } else {
                        AppLog.w("Usb", "USB permission denied")
                        refresh(ctx)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    AppLog.i("Usb", "USB device attached: ${device?.let(::deviceName)}")
                    refresh(ctx)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    AppLog.i("Usb", "USB device detached: ${device?.let(::deviceName)}")
                    refresh(ctx)
                }
            }
        }
    }

    fun deviceName(device: UsbDevice): String =
        "${device.manufacturerName ?: "?"} ${device.productName ?: "?"} (${device.vendorId}:${device.productId.toString(16)})"

    // small helper so we don't import AndroidIntentFilter frameworks
    private class IntentFilterCompat : android.content.IntentFilter() {
        init {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERM)
        }
    }
}
