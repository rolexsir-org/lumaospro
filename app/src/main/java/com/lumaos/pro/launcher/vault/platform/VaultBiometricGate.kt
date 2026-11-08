package com.lumaos.pro.launcher.vault.platform

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import javax.crypto.Cipher

/**
 * Biometric authorisation through the platform `BiometricPrompt`.
 *
 * The important detail is [authenticate]: it always wraps a `Cipher` in a
 * `CryptoObject`. Vault never treats "the user passed a biometric check" as
 * permission to open data - it asks the Keystore to decrypt the master-key
 * wrapper, and the Keystore itself refuses until authentication succeeds. So a
 * prompt that is dismissed, spoofed by another window, or bypassed by an injected
 * callback yields no key, not merely a different boolean.
 *
 * `AuthenticationCallback` below additionally requires that the returned
 * `CryptoObject` is the very cipher this call created: a hijacked callback with a
 * substituted object is ignored.
 */
class VaultBiometricGate(private val activity: Activity) {

    enum class Outcome {
        SUCCESS,
        CANCELLED,
        NEGATIVE_BUTTON,
        LOCKOUT,
        HARDWARE_UNAVAILABLE,
        SECURITY_UPDATE_REQUIRED,
        FAILED
    }

    private var active: BiometricPrompt? = null
    private var cancellation: CancellationSignal? = null

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /**
     * Shows the system prompt for [cipher].
     *
     * The callback is invoked exactly once. `onOutcome` receives SUCCESS only after
     * the system reports a successful authentication for our own cipher; the
     * caller then performs `cipher.doFinal(...)`, which can still fail if the
     * Keystore was invalidated - both layers must agree.
     */
    fun authenticate(
        cipher: Cipher,
        title: String,
        subtitle: String?,
        onOutcome: (Outcome, Cipher?) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onOutcome(Outcome.HARDWARE_UNAVAILABLE, null)
            return
        }
        val expected = cipher
        var delivered = false
        val signal = CancellationSignal()
        cancellation = signal
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .apply { if (!subtitle.isNullOrBlank()) setSubtitle(subtitle) }
            .setNegativeButton(activity.getString(android.R.string.cancel), activity.mainExecutor) { _, _ ->
                if (!delivered) {
                    delivered = true
                    onOutcome(Outcome.NEGATIVE_BUTTON, null)
                }
            }
            .build()
        active = prompt
        prompt.authenticate(
            BiometricPrompt.CryptoObject(expected),
            signal,
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (delivered) return
                    delivered = true
                    // Only ever hand back the cipher this call created.
                    if (result.cryptoObject?.cipher !== expected) {
                        onOutcome(Outcome.FAILED, null)
                    } else {
                        onOutcome(Outcome.SUCCESS, expected)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (delivered) return
                    delivered = true
                    onOutcome(mapError(errorCode), null)
                }

                override fun onAuthenticationFailed() {
                    // A single non-matching finger. The prompt stays up; the user may
                    // try again, so this is deliberately not surfaced as an outcome.
                }
            }
        )
    }

    /** Dismisses an in-flight prompt; safe to call from `onPause`. */
    fun cancel() {
        runCatching { cancellation?.cancel() }
        cancellation = null
        active = null
    }

    /** True when a secure lock screen is configured, which biometrics require. */
    fun hasSecureLockScreen(): Boolean {
        val keyguard = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false
        return keyguard.isDeviceSecure
    }

    private fun mapError(errorCode: Int): Outcome = when (errorCode) {
        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED -> Outcome.CANCELLED
        BiometricPrompt.BIOMETRIC_ERROR_CANCELED -> Outcome.CANCELLED
        BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT -> Outcome.HARDWARE_UNAVAILABLE
        BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS -> Outcome.HARDWARE_UNAVAILABLE
        BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Outcome.HARDWARE_UNAVAILABLE
        BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
        BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> Outcome.LOCKOUT
        BiometricPrompt.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> Outcome.SECURITY_UPDATE_REQUIRED
        else -> Outcome.FAILED
    }
}
