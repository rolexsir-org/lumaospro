package com.lumaos.pro.launcher.vault.core

/**
 * Decides when the user has actually left the Vault, from Android lifecycle
 * events alone.
 *
 * ## The heuristic this replaces
 *
 * A previous revision treated "the attached-screen count hit zero" as
 * *provisional*: it scheduled a check 400 ms later and concealed only if no Vault
 * screen had attached in the meantime. The reasoning was sound - `startActivity()`
 * is asynchronous, so the outgoing screen's `onStop` can be reported before the
 * incoming screen's `onStart`, and concealing on that first zero would lock the
 * vault during an ordinary hand-off - but the mechanism was a race with a timeout
 * bolted on, and it failed three ways: the window had to be long enough for the
 * slowest device, it depended on a clock agreeing with the `Handler`, and the
 * decision lived in a callback that the very transition it was protecting against
 * could cancel. A longer window is a wider hole; a shorter one brings back the
 * lock-out.
 *
 * ## The deterministic rule
 *
 * Android performs a same-task navigation in a fixed order: the outgoing screen is
 * **paused**, the incoming screen is **created, started and resumed**, and only
 * then is the outgoing screen **stopped**. Creation therefore always precedes the
 * predecessor's stop, even when the start of the new screen is reported later than
 * the stop of the old one. That single fact removes the ambiguity entirely: at the
 * moment a screen stops, "is another Vault screen taking over" is answerable by
 * looking at the *state of the other Vault activities*, not by waiting.
 *
 * So each Vault activity is tracked through its own lifecycle:
 *
 * ```
 *  CREATED -> STARTED -> STOPPED -> DESTROYED
 * ```
 *
 * and a stop is a **real departure** only when, after it, no other Vault activity
 * is `CREATED` (a hand-off is materialising) and none is `STARTED` (another screen
 * is visible). A configuration change is exempt explicitly, because the activity
 * is recreated rather than left.
 *
 * This is why the class holds a map instead of an integer. An integer cannot
 * distinguish "the screen behind me is still visible" from "the screen behind me is
 * stopped but not destroyed", and with `lockOnLeave` that difference is the
 * difference between concealing and not concealing:
 *
 *  - lock → home: home is `CREATED` when the lock screen stops, so no conceal;
 *  - home → gallery → back to home: the gallery stops while home is `STARTED`, so
 *    no conceal;
 *  - gallery → **press Home**: the gallery stops while home is `STOPPED` (it is in
 *    the back stack, not visible), so this *is* a departure;
 *  - last screen finished: nothing else is alive, so this *is* a departure.
 *
 * A stopped-but-alive entry cannot be "waiting to take over", because takeover
 * requires a create, and a create always precedes the stop being decided. Nothing
 * here consults a clock, so there is no window to tune and nothing to expire.
 *
 * ## What still locks immediately
 *
 * Screen-off does not go through this class at all:
 * [com.lumaos.pro.launcher.vault.platform.VaultLifecycleGuard] locks the session
 * directly on `ACTION_SCREEN_OFF`, with no grace period. The idle timeout is
 * evaluated by the session's own ticker and is likewise unaffected.
 */
class VaultScreenPresence {

    /** Where a Vault activity is in its lifecycle. */
    enum class Stage { CREATED, STARTED, STOPPED }

    private val stages = LinkedHashMap<Any, Stage>()

    /** Vault screens currently created and not destroyed. */
    val liveCount: Int get() = stages.size

    /** Vault screens currently visible (between `onStart` and `onStop`). */
    val attachedCount: Int get() = stages.values.count { it == Stage.STARTED }

    /**
     * A Vault activity was created.
     *
     * Recorded as soon as it exists so that a predecessor's stop, which may be
     * reported first, can see that a hand-off is in progress.
     */
    fun onScreenCreated(id: Any) {
        stages[id] = Stage.CREATED
    }

    /** A Vault activity became visible. */
    fun onScreenStarted(id: Any) {
        // A screen that reappears from the back stack is STARTED again; a screen
        // that was already tracked keeps its identity.
        stages[id] = Stage.STARTED
    }

    /**
     * A Vault activity is no longer visible.
     *
     * @param isReplacing true when the caller knows this activity is being
     *   recreated rather than left - `Activity.isChangingConfigurations`.
     * @return true when the Vault has left the foreground and the caller should
     *   conceal **now**. There is deliberately no "ask again later" answer: a
     *   deferred decision is exactly what made the previous implementation racy.
     */
    fun onScreenStopped(id: Any, isReplacing: Boolean): Boolean {
        if (stages.containsKey(id)) stages[id] = Stage.STOPPED
        if (isReplacing) return false
        // Another Vault screen created but not yet started is this screen's
        // successor; another one started is already on screen. Either way the user
        // has not left.
        return stages.entries.none { (other, stage) ->
            other != id && (stage == Stage.CREATED || stage == Stage.STARTED)
        }
    }

    /** A Vault activity was destroyed; it can no longer be a hand-off target. */
    fun onScreenDestroyed(id: Any) {
        stages.remove(id)
    }

    /** The process-level guard was torn down: forget every screen. */
    fun reset() {
        stages.clear()
    }
}
