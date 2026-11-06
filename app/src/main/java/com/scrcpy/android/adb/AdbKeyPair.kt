package com.scrcpy.android.adb

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * Android-style RSA-2048 key for ADB authentication:
 *  - generate and persist a keypair (PKCS#8, Base64),
 *  - sign the adbd challenge (SHA1withRSA, PKCS#1 v1.5),
 *  - encode the public key in the adbd RSAPUBLICKEY payload format
 *    (the $ANDROID_PUBKEY format defined in adb's crypto/rsa.c).
 */
object AdbKeyPair {

    val B64 = Base64.getEncoder()

    private const val MODULUS_BYTES = 256 // 2048-bit modulus
    private const val NATIVE_KEY_SIZE = 4 + 4 + MODULUS_BYTES + MODULUS_BYTES + 4 // 524

    private val TWO32 = BigInteger.ONE.shiftLeft(32)

    /** Generates (once) and stores the key via callbacks; returns the keypair. */
    fun obtain(storeLoader: () -> String?, storeSaver: (String) -> Unit): KeyPair {
        storeLoader()?.takeIf { it.isNotBlank() }?.let { saved ->
            runCatching { load(saved) }.getOrNull()?.let { return it }
        }
        val kp = generate()
        storeSaver(encodePrivate(kp))
        return kp
    }

    fun generate(): KeyPair {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(RSAKeyGenParameterSpec(2048, BigInteger.valueOf(65537L)))
        return gen.generateKeyPair()
    }

    fun encodePrivate(keyPair: KeyPair): String =
        B64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)

    fun load(b64: String): KeyPair {
        val keyBytes = Base64.getDecoder().decode(b64)
        val factory = KeyFactory.getInstance("RSA")
        val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val (modulus, exponent) = parsePkcs8RsaKey(privateKey.encoded)
        val publicKey = factory.generatePublic(RSAPublicKeySpec(modulus, exponent))
        return KeyPair(publicKey, privateKey)
    }

    /** Signs a challenge token the way adbd expects: SHA1withRSA (PKCS#1 v1.5, SHA-1 digest). */
    fun sign(keyPair: KeyPair, token: ByteArray): ByteArray {
        val sig = Signature.getInstance("SHA1withRSA")
        sig.initSign(keyPair.private)
        sig.update(token)
        return sig.sign()
    }

    /**
     * RSAPUBLICKEY payload: base64(524-byte native blob) + " " + name + NUL.
     *
     * Native blob:
     *   [ key_len u32 LE ][ n0inv u32 LE ][ modulus 256B BE ][ rr 256B BE ][ exponent u32 LE ]
     *   where n0inv = -modulus^-1 mod 2^32 and rr = 2^4096 mod modulus.
     */
    fun encodePublicKey(keyPair: KeyPair): ByteArray {
        val pub = keyPair.public as java.security.interfaces.RSAPublicKey
        val n = pub.modulus
        val e = pub.publicExponent

        val n0inv = n.modInverse(TWO32).negate().mod(TWO32)
        val rr = BigInteger.ONE.shiftLeft(MODULUS_BYTES * 8 * 2).mod(n)

        val blob = ByteArray(NATIVE_KEY_SIZE)
        putLe(blob, 0, MODULUS_BYTES)
        putLe(blob, 4, n0inv.toInt())
        copyBeRightAligned(blob, 8, MODULUS_BYTES, n)
        copyBeRightAligned(blob, 8 + MODULUS_BYTES, MODULUS_BYTES, rr)
        putLe(blob, 8 + MODULUS_BYTES * 2, e.toInt())

        val b64 = encode(blob)
        return (b64 + " scrcpy-android@host").toByteArray(Charsets.US_ASCII) + 0
    }

    fun encode(blob: ByteArray): String = B64.encodeToString(blob, Base64.NO_WRAP)

    private fun copyBeRightAligned(blob: ByteArray, start: Int, size: Int, value: BigInteger) {
        val bytes = stripLeadingZeros(value.toByteArray())
        check(bytes.size <= size)
        System.arraycopy(bytes, 0, blob, start + (size - bytes.size), bytes.size)
    }

    private fun stripLeadingZeros(bytes: ByteArray): ByteArray =
        if (bytes.isNotEmpty() && bytes[0].toInt() == 0) bytes.copyOfRange(1, bytes.size) else bytes

    private fun putLe(buf: ByteArray, off: Int, value: Int) {
        buf[off] = (value and 0xff).toByte()
        buf[off + 1] = ((value shr 8) and 0xff).toByte()
        buf[off + 2] = ((value shr 16) and 0xff).toByte()
        buf[off + 3] = ((value shr 24) and 0xff).toByte()
    }

    // ---- minimal DER parsing for PKCS#8 -> (modulus, exponent) ----

    private fun parsePkcs8RsaKey(der: ByteArray): Pair<BigInteger, BigInteger> {
        val outer = readSequence(der, 0)
        var pos = outer.second
        // INTEGER version
        val ver = readIntegerAt(der, pos); pos = ver.second
        // SEQUENCE AlgorithmIdentifier
        check(der[pos] == 0x30.toByte())
        val algLen = readLength(der, pos + 1)
        pos = algLen.second + algLen.first
        // OCTET STRING privateKey
        check(der[pos] == 0x04.toByte())
        val octLen = readLength(der, pos + 1)
        val octStart = octLen.second
        // inner SEQUENCE RSAPrivateKey (ASN.1 RSAPrivateKey ::= SEQUENCE { version, n, e, d, ... })
        val inner = readSequence(der, octStart)
        var ipos = inner.second
        val intVersion = readIntegerAt(der, ipos); ipos = intVersion.second
        val modulus = readIntegerAt(der, ipos); ipos = modulus.second
        val exponent = readIntegerAt(der, ipos)
        return modulus.first to exponent.first
    }

    private fun readSequence(der: ByteArray, pos: Int): Pair<Int, Int> {
        check(der[pos] == 0x30.toByte())
        val len = readLength(der, pos + 1)
        return len.first to len.second
    }

    private fun readIntegerAt(der: ByteArray, pos: Int): Pair<BigInteger, Int> {
        check(der[pos] == 0x02.toByte())
        val len = readLength(der, pos + 1)
        val content = len.second
        val bytes = der.copyOfRange(content, content + len.first)
        return BigInteger(1, bytes) to (content + len.first)
    }

    private fun readLength(der: ByteArray, pos: Int): Pair<Int, Int> {
        val first = der[pos].toInt() and 0xff
        return if (first < 0x80) {
            first to pos + 1
        } else {
            val numBytes = first and 0x7f
            var len = 0
            for (i in 0 until numBytes) len = (len shl 8) or (der[pos + 1 + i].toInt() and 0xff)
            len to pos + 1 + numBytes
        }
    }

    // exposed for tests
    fun privateKeyModulus(pk: PrivateKey): BigInteger =
        parsePkcs8RsaKey(pk.encoded).first
}
