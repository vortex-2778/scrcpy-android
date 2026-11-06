package com.scrcpy.android.adb

/**
 * ADB packet framing.
 *
 * The ADB transport multiplexes stream ids (local/remote) over a single USB
 * interface with 24-byte little-endian headers followed by payload bytes:
 *
 *   [ cmd u32 ][ arg0 u32 ][ arg1 u32 ][ dataLen u32 ][ checksum u32 ][ magic u32 ][ data ]
 *
 * - checksum: simple byte sum of payload (adbd tolerates 0, but be correct)
 * - magic: cmd ^ 0xffffffff
 *
 * Command constants are stored little-endian so their LE serialization prints
 * the well-known ASCII names on the wire ("CNXN", "AUTH", "OPEN", ...).
 */
object AdbPacket {

    const val A_CNXN: Int = 0x4e584e43 // LE bytes "CNXN"
    const val A_AUTH: Int = 0x48545541 // LE bytes "AUTH"
    const val A_OPEN: Int = 0x4e45504f // LE bytes "OPEN"
    const val A_OKAY: Int = 0x59414b4f // LE bytes "OKAY"
    const val A_CLSE: Int = 0x45534c43 // LE bytes "CLSE"
    const val A_WRTE: Int = 0x45545257 // LE bytes "WRTE"

    const val COMMAND_LENGTH = 24

    // AUTH sub-types (arg0 of AUTH packets)
    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_PUBLICKEY = 3

    const val PROTOCOL_VERSION = 0x01000001
    const val MAX_PAYLOAD = 4096

    /** Little-endian checksum over payload (sum of bytes). */
    fun checksum(data: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        for (i in off until off + len) sum += data[i].toInt() and 0xff
        return sum
    }

    /** Serializes header + copies payload into [dest] at [offset]. Returns header bytes only in [header]. */
    fun fillHeader(header: ByteArray, cmd: Int, arg0: Int, arg1: Int, dataLen: Int, checksum: Int) {
        putLe32(header, 0, cmd)
        putLe32(header, 4, arg0)
        putLe32(header, 8, arg1)
        putLe32(header, 12, dataLen)
        putLe32(header, 16, checksum)
        putLe32(header, 20, cmd.inv())
    }

    fun putLe32(buf: ByteArray, off: Int, value: Int) {
        buf[off] = (value and 0xff).toByte()
        buf[off + 1] = ((value shr 8) and 0xff).toByte()
        buf[off + 2] = ((value shr 16) and 0xff).toByte()
        buf[off + 3] = ((value shr 24) and 0xff).toByte()
    }

    fun getLe32(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xff) or
            ((buf[off + 1].toInt() and 0xff) shl 8) or
            ((buf[off + 2].toInt() and 0xff) shl 16) or
            ((buf[off + 3].toInt() and 0xff) shl 24)

    fun commandName(cmd: Int): String = when (cmd) {
        A_CNXN -> "CNXN"
        A_AUTH -> "AUTH"
        A_OPEN -> "OPEN"
        A_OKAY -> "OKAY"
        A_CLSE -> "CLSE"
        A_WRTE -> "WRTE"
        else -> String.format("0x%08x", cmd)
    }
}

/** One parsed packet. */
data class AdbPacketData(
    val cmd: Int,
    val arg0: Int,
    val arg1: Int,
    val data: ByteArray,
) {
    fun commandName(): String = AdbPacket.commandName(cmd)
}
