package com.lumaos.pro.launcher.vault.core

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Vault session lifecycle: the single authority on whether the master key is
 * available to the rest of the app.
 *
 * The session exists for exactly as long as the vault is unlocked, holds the
 * master key, and destroys it on lock. Nothing else in Vault keeps a long-lived
 * copy of key material, so a lock is one call that zeroizes one buffer and
 * notifies the UI.
 *
 * Transitions:
 *
 * ```
 *  LOCKED --unlock--> UNLOCKED --conceal--> UNLOCKED_CONCEALED
 *     ^                  |                       |
 *     |                  +------ reveal ---------+
 *     +------------------ lock (screen off, timeout, leave, user action, process death)
 * ```
 *
 * "Concealed" is what happens the instant the app stops being visible: Vault
 * hides every preview immediately (the activity shows an opaque shield), but the
 * idle timer keeps running, so a quick glance at a notification does not force a
 * full re-authentication while an abandoned session still expires.
 *
 * This class is pure Kotlin so its rules are unit-tested without an emulator; the
 * Android side only feeds it screen-off, background and clock events.
 */
class VaultSession(
    private val clock: () -> Long = System::currentTimeMillis
) {

    enum class State { LOCKED, UNLOCKED, UNLOCKED_CONCEALED }

    enum class LockReason(val id: String) {
        USER("user"),
        SCREEN_OFF("screen_off"),
        TIMEOUT("timeout"),
        LEFT_APP("left_app"),
        PROCESS_DEATH("process_death"),
        RESTORE("restore"),
        FAILED_BIOMETRIC("failed_biometric")
    }

    /** Key material for an unlocked session. Owns the byte array it is handed. */
    class Keys(val masterKey: ByteArray, val vaultId: String) {
        @Volatile
        private var destroyed = false

        val isDestroyed: Boolean get() = destroyed

        /** True while the key bytes are still usable. */
        fun isUsable(): Boolean = !destroyed && masterKey.any { it != 0.toByte() }

        internal fun destroy() {
            destroyed = true
            VaultCrypto.zeroize(masterKey)
        }
    }

    interface Listener {
        /** Called when the vault becomes available; runs on the caller's thread. */
        fun onUnlocked(keys: Keys) {}

        /** Called on every lock, after key material is already zeroized. */
        fun onLocked(reason: LockReason) {}

        /** Vault lost visibility: hide previews now. */
        fun onConcealed() {}

        /** Vault is visible again (unlock still valid). */
        fun onRevealed() {}
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile
    var state: State = State.LOCKED
        private set

    private var keys: Keys? = null
    private var autoLockMillis: Long = VaultAutoLock.DEFAULT.millis
    private var lockOnLeave: Boolean = false
    private var lastInteractionMs: Long = 0L
    private var concealedAtMs: Long = 0L

    val isUnlocked: Boolean get() = state != State.LOCKED

    /** Current key material, or null while locked. Never cache this. */
    fun currentKeys(): Keys? = if (state == State.LOCKED) null else keys

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Applies the user's auto-lock preference. Takes effect immediately. */
    fun configure(autoLock: VaultAutoLock, lockOnLeave: Boolean) {
        autoLockMillis = autoLock.millis
        this.lockOnLeave = lockOnLeave
    }

    fun autoLockMillis(): Long = autoLockMillis

    /**
     * Marks a successful authentication.
     *
     * The keys handed in become the session's property: callers must not keep or
     * reuse the array afterwards, because locking zeroizes it.
     */
    fun unlock(keys: Keys) {
        lockInternal(LockReason.PROCESS_DEATH, notify = false)
        this.keys = keys
        state = State.UNLOCKED
        lastInteractionMs = clock()
        listeners.forEach { it.onUnlocked(keys) }
    }

    fun lock(reason: LockReason = LockReason.USER) {
        if (state == State.LOCKED) return
        lockInternal(reason, notify = true)
    }

    private fun lockInternal(reason: LockReason, notify: Boolean) {
        val previous = keys
        keys = null
        state = State.LOCKED
        concealedAtMs = 0L
        previous?.destroy()
        if (notify) listeners.forEach { it.onLocked(reason) }
    }

    /** The vault lost foreground visibility. */
    fun conceal() {
        if (state != State.UNLOCKED) return
        state = State.UNLOCKED_CONCEALED
        concealedAtMs = clock()
        listeners.forEach { it.onConcealed() }
        if (lockOnLeave) lock(LockReason.LEFT_APP)
    }

    /** The vault is visible again. */
    fun reveal() {
        if (state != State.UNLOCKED_CONCEALED) return
        state = State.UNLOCKED
        lastInteractionMs = clock()
        listeners.forEach { it.onRevealed() }
    }

    /** Records user interaction, which is what the idle timer measures. */
    fun touch() {
        if (state == State.LOCKED) return
        lastInteractionMs = clock()
    }

    /** Screen turned off: locked unconditionally, no grace period. */
    fun onScreenOff() {
        lock(LockReason.SCREEN_OFF)
    }

    /** True when the idle timeout has elapsed and the session must be locked. */
    fun isIdleExpired(nowMs: Long = clock()): Boolean {
        if (state == State.LOCKED) return false
        val reference = if (state == State.UNLOCKED_CONCEALED) {
            maxOf(lastInteractionMs, concealedAtMs)
        } else {
            lastInteractionMs
        }
        return nowMs - reference >= autoLockMillis
    }

    /**
     * Called by the UI ticker. Locks when the idle threshold has passed, which is
     * the single mechanism behind the configurable inactivity timeout.
     */
    fun tick(nowMs: Long = clock()): Boolean {
        if (isIdleExpired(nowMs)) {
            lock(LockReason.TIMEOUT)
            return true
        }
        return false
    }

    /** Milliseconds until the idle timeout, for scheduling the next tick. */
    fun millisUntilIdle(): Long {
        if (state == State.LOCKED) return autoLockMillis
        val reference = if (state == State.UNLOCKED_CONCEALED) {
            maxOf(lastInteractionMs, concealedAtMs)
        } else {
            lastInteractionMs
        }
        return (reference + autoLockMillis - clock()).coerceAtLeast(0L)
    }

    /** Lock without notifying listeners, used when the process is going away. */
    fun onProcessDeath() {
        lockInternal(LockReason.PROCESS_DEATH, notify = false)
    }
}
