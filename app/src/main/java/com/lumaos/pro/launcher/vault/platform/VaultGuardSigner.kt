package com.lumaos.pro.launcher.vault.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.vault.core.VaultGuardAuthenticator
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Keystore-backed authenticator for `guard.json`.
 *
 * An HMAC-SHA-256 key is generated inside the Android Keystore with
 * `PURPOSE_SIGN | PURPOSE_VERIFY`. Its bytes are never returned to the app, so the
 * tag on the retry record can only be produced by code running on this device, in
 * this app. Copying `guard.json` to another install fails verification, and editing
 * it fails verification, which is what turns a plaintext counter into a
 * tamper-evident one.
 *
 * ## Why HMAC and not a signature
 *
 * `KeyProperties.KEY_ALGORITHM_HMAC_SHA256` is backed by the TEE/StrongBox on every
 * device this app supports for Keystore-backed keys, needs no RSA/ECDSA key
 * generation on the lock-screen path (which would cost hundreds of milliseconds),
 * and has a stable 32-byte tag. A hardware-backed signature would add nothing here:
 * there is no third party to convince, only this process.
 *
 * ## What it does not do
 *
 * Authenticating the record is not encrypting it, and it is not the PIN check. The
 * record still contains only a count and two timestamps; the vault still cannot be
 * opened without unwrapping the master key through PBKDF2. And, as the threat model
 * says plainly, **an attacker who can run code as this app, or who is root, can ask
 * the Keystore for the same tag**. Rollback protection is therefore "cannot be
 * edited or replayed from another device through the filesystem alone", not
 * "cannot be defeated by a compromised OS".
 */
class VaultGuardSigner(private val context: Context) : VaultGuardAuthenticator {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    override fun isInitialised(): Boolean = runCatching { keyStore.containsAlias(KEY_ALIAS) }
        .getOrDefault(false)

    override fun ensureInitialised() {
        if (isInitialised()) return
        try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .build()
            )
            generator.generateKey()
            LumaLog.i("Vault throttle signing key created in the Android Keystore")
        } catch (e: Exception) {
            // A device without the HMAC KeyStore algorithm is not a device the vault
            // can be used on anyway (the PIN wrapper needs the same Keystore family),
            // but failing to sign must never fail the unlock path itself. The guard
            // then runs in its documented degraded mode.
            LumaLog.e("Vault throttle signing key could not be created: ${e.javaClass.simpleName}")
        }
    }

    override fun sign(data: ByteArray): ByteArray? = runCatching {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(loadKey())
        mac.doFinal(data)
    }.getOrNull()

    override fun verify(data: ByteArray, tag: ByteArray): Boolean {
        val expected = sign(data) ?: return false
        return MessageDigest.isEqual(expected, tag)
    }

    private fun loadKey(): SecretKey {
        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("Throttle signing key is unavailable")
        // A Keystore key must never export its bytes; if it does, this is a software
        // provider and the tag would be forgeable, so refuse it rather than pretend.
        if (key.encoded != null) {
            val bytes = key.encoded
            bytes.fill(0)
            throw IllegalStateException("Throttle signing key is not hardware protected")
        }
        return key
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALGORITHM = "HmacSHA256"

        /** One vault per install, so one alias; the vault id is bound into the MAC. */
        private const val KEY_ALIAS = "lumaos.vault.throttle"
    }
}
