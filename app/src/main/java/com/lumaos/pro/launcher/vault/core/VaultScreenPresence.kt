package com.lumaos.pro.launcher.vault.core

/**
 * Decides *when* the vault should hide itself as the user moves between Vault
 * screens, without a race between Android activity transitions.
 *
 * ## The bug this exists to prevent
 *
 * Vault used to count attached screens and conceal the moment the count fell to
 * zero. Android does not perform `startActivity()` synchronously: when the lock
 * screen calls `startActivity(home)` and then `finish()`, the lock screen's
 * `onStop` can run **before** the new activity's `onStart`. The count therefore
 * dipped through zero during a perfectly normal hand-off, `conceal()` ran, and
 * with "lock when leaving the app" enabled that is a `lock()` - so the vault
 * re-locked itself the instant the correct PIN succeeded, and the user landed
 * back on the PIN pad (or, combined with the `finishAffinity()` defect, in the
 * system launcher). Correct PIN, no way in.
 *
 * ## The rule
 *
 * A drop to zero is *provisional*. It only becomes a real conceal once the
 * hand-off window has elapsed with no Vault screen attached. Any new Vault screen
 * cancels it. Genuine backgrounding is unaffected: the user is gone, no screen
 * attaches, the window elapses, and the vault conceals - the auto-lock timer
 * starts from that moment as before.
 *
 * This class is deliberately pure Kotlin: the activity wiring stays trivial and
 * the policy itself is covered by unit tests that can express "the lock screen
 * stopped one frame before the home screen started", which a device test cannot
 * reliably reproduce.
 */
class VaultScreenPresence(
    private val clock: () -> Long = System::currentTimeMillis
) {

    private var attached = 0
    private var hiddenAtMs = 0L
    private var pendingConcealSinceMs: Long? = null

    /** Screens currently started (between `onStart` and `onStop`). */
    val attachedCount: Int get() = attached

    /** True while a conceal is waiting for the hand-off window to elapse. */
    val hasPendingConceal: Boolean get() = pendingConcealSinceMs != null

    /**
     * A Vault screen became visible.
     *
     * Cancels a provisional conceal: this is the "home started before the lock
     * screen stopped" case, and the reason a successful unlock now stays unlocked.
     */
    fun onScreenStarted() {
        attached++
        pendingConcealSinceMs = null
    }

    /**
     * A Vault screen is no longer visible.
     *
     * Returns true when this was the last one - the caller should schedule the
     * conceal check for [HANDOFF_WINDOW_MS] later rather than concealing now.
     */
    fun onScreenStopped(): Boolean {
        if (attached > 0) attached--
        if (attached > 0) return false
        if (pendingConcealSinceMs == null) pendingConcealSinceMs = clock()
        return true
    }

    /**
     * Called when the scheduled window elapses.
     *
     * Returns true exactly once per departure, and only if no Vault screen
     * attached in the meantime. A stale callback (one from an earlier departure
     * that was already resolved) returns false and does nothing.
     */
    fun settle(nowMs: Long = clock()): Boolean {
        val since = pendingConcealSinceMs ?: return false
        if (attached > 0) {
            pendingConcealSinceMs = null
            return false
        }
        if (nowMs - since < HANDOFF_WINDOW_MS) return false
        pendingConcealSinceMs = null
        hiddenAtMs = nowMs
        return true
    }

    /** When the vault last became fully hidden, for diagnostics. */
    val lastHiddenAtMs: Long get() = hiddenAtMs

    /** The vault was locked or reset: forget any pending departure. */
    fun reset() {
        pendingConcealSinceMs = null
    }

    companion object {
        /**
         * How long a "no Vault screen attached" state must persist before it counts
         * as leaving the app.
         *
         * Long enough to cover an activity transition (one or two main-thread
         * frames, tens of milliseconds even on a slow device), short enough that a
         * real background cannot be observed by a shoulder-surfer. It is *not* a
         * security grace period: nothing is decrypted during it, and screen-off
         * locks immediately through a different path that does not wait at all.
         */
        const val HANDOFF_WINDOW_MS = 400L
    }
}
