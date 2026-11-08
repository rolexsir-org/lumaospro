package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultGuardAuthenticator
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A stand-in for the Keystore HMAC used in production.
 *
 * The real key lives in the Android Keystore and cannot be created on a plain JVM,
 * so the *policy* under test - verify the tag, refuse what does not match, treat a
 * missing record as deletion - is exercised with this. It is a real HMAC over a real
 * key, not a stub that always returns true: the tests it serves are about rejecting
 * bad signatures, and an authenticator that cannot reject anything would prove
 * nothing.
 *
 * [keyBytesDistinctFrom] gives each test its own key so "record copied from another
 * device" can be expressed.
 */
class FakeGuardAuthenticator(private val keyBytes: ByteArray = DEFAULT_KEY) : VaultGuardAuthenticator {

    private var initialised = false

    override fun isInitialised(): Boolean = initialised

    override fun ensureInitialised() {
        initialised = true
    }

    override fun sign(data: ByteArray): ByteArray? {
        if (!initialised) return null
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(keyBytes, ALGORITHM))
        return mac.doFinal(data)
    }

    override fun verify(data: ByteArray, tag: ByteArray): Boolean {
        val expected = sign(data) ?: return false
        return java.security.MessageDigest.isEqual(expected, tag)
    }

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private val DEFAULT_KEY = ByteArray(32) { (it * 7 + 13).toByte() }

        /** A signer with an unrelated key: simulates a record from another device. */
        fun keyBytesDistinctFrom(other: FakeGuardAuthenticator): FakeGuardAuthenticator =
            FakeGuardAuthenticator(ByteArray(32) { (it * 11 + 3).toByte() })
    }
}
