package com.lumaos.pro.launcher.vault.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.lumaos.pro.launcher.vault.core.VaultCrypto
import com.lumaos.pro.launcher.vault.core.VaultCryptoException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The device-bound half of biometric unlock.
 *
 * A biometric wrapper is the vault master key encrypted under an AES-256 key that
 * lives in the Android Keystore and **cannot leave it**. The key is created with:
 *
 *  - `setUserAuthenticationRequired(true)` - using it requires the user to
 *    authenticate, every single time (`setUserAuthenticationParameters(0, …)` on
 *    Android 11+, a per-use validity of -1 on Android 9/10);
 *  - `setInvalidatedByBiometricEnrollment(true)` - enrolling a new fingerprint or
 *    face invalidates the key, so someone who adds their own biometric to a
 *    stolen, already-unlocked phone does not inherit the vault;
 *  - `setUnlockedDeviceRequired` on Android 10+ where the device supports it,
 *    so the key cannot be used while the phone is powered down or locked.
 *
 * What this gives the product is a *cryptographic* authorisation: the biometric
 * prompt wraps a `Cipher`, and only after a successful authentication will the
 * Keystore perform the unwrap. What it cannot do is recover a forgotten PIN or
 * survive a factory reset - the Keystore is destroyed with the device, which is
 * exactly why the recovery key exists separately.
 *
 * Vault uses the platform `BiometricPrompt` (API 28+) rather than an extra
 * AndroidX dependency: it is the same system dialog, it is available on every
 * Android version LumaOS Pro supports for biometric unlock, and it keeps the
 * launcher's dependency footprint unchanged. On API 26-27 biometry is reported
 * as unavailable and the vault uses the PIN, which is always present.
 */
class VaultDeviceKeyGuard(private val context: Context) {

    enum class Availability {
        /** A biometric wrapper can be created and used. */
        AVAILABLE,
        /** The OS is too old for the platform BiometricPrompt (API < 28). */
        UNSUPPORTED_OS,
        /** The device has no biometric hardware. */
        NO_HARDWARE,
        /** Hardware exists, but the user has not enrolled a fingerprint or face. */
        NOT_ENROLLED,
        /** Hardware exists, but only weak biometrics are enrolled. */
        WEAK_ONLY
    }

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    fun availability(): Availability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return Availability.UNSUPPORTED_OS
        val pm = context.packageManager
        val hasHardware = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) ||
            pm.hasSystemFeature(PackageManager.FEATURE_FACE) ||
            pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
        if (!hasHardware) return Availability.NO_HARDWARE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
                ?: return Availability.NO_HARDWARE
            // The authenticator-mask overload arrived in Android 11. Android 10
            // exposes only the no-argument check; both paths require the normal
            // USE_BIOMETRIC manifest permission.
            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.canAuthenticate(BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                manager.canAuthenticate()
            }
            return when (status) {
                android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE
                android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED
                android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Availability.NO_HARDWARE
                android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Availability.NOT_ENROLLED
                else -> Availability.WEAK_ONLY
            }
        }
        // Android 9 has BiometricPrompt but no BiometricManager; the deprecated
        // FingerprintManager still answers "is hardware present / enrolled".
        @Suppress("DEPRECATION")
        val fingerprint = context.getSystemService(android.hardware.fingerprint.FingerprintManager::class.java)
            ?: return Availability.NO_HARDWARE
        @Suppress("DEPRECATION")
        if (!fingerprint.isHardwareDetected) return Availability.NO_HARDWARE
        @Suppress("DEPRECATION")
        return if (fingerprint.hasEnrolledFingerprints()) Availability.AVAILABLE else Availability.NOT_ENROLLED
    }

    fun isAvailable(): Boolean = availability() == Availability.AVAILABLE

    fun containsKey(alias: String): Boolean = runCatching { keyStore.containsAlias(alias) }.getOrDefault(false)

    /**
     * Creates (or replaces) the biometric wrapping key and returns a cipher ready
     * to encrypt under it. The cipher must be handed to `BiometricPrompt` as a
     * `CryptoObject`; `doFinal` only works after a successful authentication.
     */
    fun createKeyAndEncryptCipher(alias: String): Cipher {
        require(alias.isNotBlank()) { "alias required" }
        deleteKey(alias)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 0 seconds: authentication is required for every use of the key.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setUnlockedDeviceRequired(true)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(builder.build())
        generator.generateKey()
        // The key exists but is unusable until the user authenticates; asking for
        // the cipher here is what lets BiometricPrompt authorise its `doFinal`.
        return Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.ENCRYPT_MODE, loadKey(alias))
        }
    }

    /** A decrypt cipher bound to a stored IV, to be wrapped in a CryptoObject. */
    fun decryptCipher(alias: String, iv: ByteArray): Cipher {
        val key = loadKey(alias)
        return Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
    }

    fun deleteKey(alias: String) {
        runCatching { if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias) }
    }

    /**
     * True when the Keystore entry is gone even though a wrapper still exists.
     *
     * This is what happens after a biometric enrolment change (by design), after a
     * lock-screen credential reset, or after a restore to a different phone. The
     * UI then says "biometric unlock needs to be set up again" and falls back to
     * the PIN - it never silently pretends biometrics still work.
     */
    fun isInvalidated(alias: String?): Boolean = alias != null && !containsKey(alias)

    private fun loadKey(alias: String): SecretKey {
        val key = keyStore.getKey(alias, null) as? SecretKey
            ?: throw VaultCryptoException("Biometric key is unavailable")
        if (key.encoded != null) {
            // A Keystore key must never hand out its bytes; if it does, this is not
            // a hardware-backed key and Vault refuses to wrap the master key with it.
            VaultCrypto.zeroize(key.encoded)
            throw VaultCryptoException("Biometric key is not hardware protected")
        }
        return key
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORM = "AES/GCM/NoPadding"

        /** Alias for the biometric wrapper key of a given vault. */
        fun aliasFor(vaultId: String): String = "lumaos.vault.bio.$vaultId"

        /** The platform prompt only understands strong (Class 3) biometrics. */
        private const val BIOMETRIC_STRONG = android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
}
