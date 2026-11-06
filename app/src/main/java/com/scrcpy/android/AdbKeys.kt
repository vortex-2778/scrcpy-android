package com.scrcpy.android

import com.scrcpy.android.adb.AdbKeyPair
import java.security.KeyPair

/**
 * Lazily-created, app-lifetime ADB keypair, persisted in AppSettings.
 * The scrcpy server jar asset is kept next to it for clarity.
 */
object AdbKeys {

    private var _keyPair: KeyPair? = null

    @Volatile
    private var publicKeyPayload: ByteArray? = null

    fun keyPair(): KeyPair {
        _keyPair?.let { return it }
        synchronized(this) {
            _keyPair?.let { return it }
            val kp = AdbKeyPair.obtain(
                storeLoader = { App.singleton.settings.adbKey },
                storeSaver = { App.singleton.settings.setAdbKey(it) }
            )
            _keyPair = kp
            return kp
        }
    }

    fun publicKeyPayload(): ByteArray {
        publicKeyPayload?.let { return it }
        synchronized(this) {
            publicKeyPayload?.let { return it }
            val b = AdbKeyPair.encodePublicKey(keyPair())
            publicKeyPayload = b
            return b
        }
    }
}
