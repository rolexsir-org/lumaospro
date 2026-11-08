package com.lumaos.pro.launcher.vault.core

/**
 * Authenticates the unlock-throttle record.
 *
 * `guard.json` has to be readable *before* the vault is unlocked, so it cannot be
 * protected by the master key. Left as plain JSON it has an obvious weakness: an
 * attacker with write access to the app's private directory can rewrite the failure
 * count to zero between attempts, or delete the file, and the progressive delay
 * disappears.
 *
 * This interface closes that gap with a key the attacker's *file* access does not
 * reach: a non-exportable key in the Android Keystore. The record stays plaintext
 * (it holds no secrets), but it carries a tag that only that key can produce. The
 * consequences are:
 *
 *  - an edited failure count, deadline or timestamp fails verification and is read
 *    as tampering rather than obeyed;
 *  - a deleted record is still detectable, because the *key* outlives the file -
 *    see [isInitialised];
 *  - copying a record in from another install fails, because that install's key is
 *    not in this device's Keystore.
 *
 * Deliberately small, and deliberately not named after any key material: the
 * implementation lives in `vault/platform` where the Keystore is available, and the
 * policy that consumes it is pure Kotlin and unit-tested with a fake.
 */
interface VaultGuardAuthenticator {

    /**
     * True when this vault has ever written a throttle record.
     *
     * This is the witness that makes deletion detectable. A Keystore entry survives
     * the deletion of every file in the vault directory, so "the key exists but the
     * record does not" means the record was removed - it cannot mean a first run.
     */
    fun isInitialised(): Boolean

    /** Creates the signing entry if it does not exist yet. */
    fun ensureInitialised()

    /** Returns the authentication tag for [data], or null if the key is unusable. */
    fun sign(data: ByteArray): ByteArray?

    /** Constant-time verification of [tag] over [data]. */
    fun verify(data: ByteArray, tag: ByteArray): Boolean
}
