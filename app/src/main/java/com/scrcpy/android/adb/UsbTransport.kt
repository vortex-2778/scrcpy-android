package com.scrcpy.android.adb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

/**
 * Low-level USB transport for the ADB (0xff/0x42/0x01) interface.
 */
class UsbTransport(
    val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    val inEndpoint: UsbEndpoint,
    val outEndpoint: UsbEndpoint,
) {

    companion object {
        /** True when the device exposes an ADB interface (class 0xFF, subclass 0x42, protocol 1). */
        fun hasAdbInterface(device: UsbDevice): Boolean = findAdbInterface(device) != null

        fun findAdbInterface(device: UsbDevice): UsbInterface? {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                // ADB interface: vendor-specific class (0xFF), subclass 0x42, protocol 1.
                if (iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                    iface.interfaceSubclass == 0x42 &&
                    iface.interfaceProtocol == 1
                ) {
                    return iface
                }
            }
            return null
        }

        fun open(device: UsbDevice, connection: UsbDeviceConnection): UsbTransport {
            val iface = findAdbInterface(device)
                ?: throw IllegalStateException("No ADB interface on $device")
            connection.claimInterface(iface, true)
            var inEndpoint: UsbEndpoint? = null
            var outEndpoint: UsbEndpoint? = null
            for (i in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(i)
                when (ep.direction) {
                    UsbConstants.USB_DIR_OUT -> outEndpoint = ep
                    UsbConstants.USB_DIR_IN -> inEndpoint = ep
                }
            }
            check(inEndpoint != null && outEndpoint != null) { "ADB interface with missing endpoints" }
            return UsbTransport(connection, iface, inEndpoint, outEndpoint)
        }
    }

    /** Writes exactly [len] bytes starting at [offset]. Blocking. */
    fun write(data: ByteArray, offset: Int, len: Int) {
        var written = 0
        while (written < len) {
            val n = connection.bulkTransfer(outEndpoint, data, offset + written, len - written, 5000)
            if (n < 0) throw TransportException("USB write failed at $written/$len")
            written += n
        }
    }

    /** Reads up to [len] bytes; times out and returns 0 on timeout? No: returns -1 on failure. */
    fun read(data: ByteArray, offset: Int, len: Int, timeoutMs: Int): Int =
        connection.bulkTransfer(inEndpoint, data, offset, len, timeoutMs)

    /** Reads exactly [len] bytes or throws. Blocks with up to [totalTimeoutMs] budget. */
    fun readFully(data: ByteArray, offset: Int, len: Int, totalTimeoutMs: Int = 20000) {
        var got = 0
        val deadline = System.currentTimeMillis() + totalTimeoutMs
        while (got < len) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) throw TransportException("USB read timeout ($got/$len)")
            val n = connection.bulkTransfer(inEndpoint, data, offset + got, len - got,
                remaining.coerceAtMost(5000))
            if (n < 0) {
                if (System.currentTimeMillis() >= deadline) throw TransportException("USB read timeout ($got/$len)")
                continue
            }
            got += n
        }
    }

    fun close() {
        runCatching { connection.releaseInterface(usbInterface) }
        runCatching { connection.close() }
    }

    class TransportException(message: String) : java.io.IOException(message)
}
