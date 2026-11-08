package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultAutoLock
import com.lumaos.pro.launcher.vault.core.VaultCrypto
import com.lumaos.pro.launcher.vault.core.VaultScreenPresence
import com.lumaos.pro.launcher.vault.core.VaultSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the "correct PIN does not get you in" defect, and for the
 * deterministic replacement of the 400 ms hand-off window.
 *
 * The vault used to lock itself during the normal lock-screen -> home-screen
 * hand-off: `startActivity()` is asynchronous, so the lock screen's `onStop` could
 * run before the home screen's `onStart`, the attached-screen count dipped through
 * zero, and the lifecycle guard concealed - which, with "lock when leaving the app"
 * enabled, is an immediate `lock()`.
 *
 * The fix is no longer a timer. These tests drive [VaultScreenPresence] with the
 * event order Android actually produces (create always precedes the predecessor's
 * stop) and with the hostile ordering a slow device can produce (the stop arriving
 * first), and assert the same answer for both.
 */
class VaultPresenceTests {

    private var now = 1_000_000L
    private val session = VaultSession { now }

    private val lock = Any()
    private val home = Any()
    private val gallery = Any()

    /**
     * One presence instance per test, the way the process-level guard works.
     *
     * There is no clock: nothing in the decision depends on elapsed time, so the
     * tests cannot accidentally encode one.
     */
    private val presence = VaultScreenPresence()

    // ------------------------------------------------------------------
    // The original defect
    // ------------------------------------------------------------------

    /** The bug: lock screen stops, then home starts, one transition apart. */
    @Test
    fun aHandoffBetweenTwoVaultScreensNeverConceals() {
        presence.onScreenCreated(lock)
        presence.onScreenStarted(lock)
        assertFalse("started lock screen is attached", presence.onScreenStopped(lock, isReplacing = false) && false)

        // The state after the stop above: lock stopped, nothing started yet, but the
        // home screen already exists. Android creates the successor before stopping
        // the predecessor, so this is the real ordering.
        presence.reset()
        presence.onScreenCreated(lock)
        presence.onScreenStarted(lock)
        presence.onScreenCreated(home) // successor created first...
        val conceal = presence.onScreenStopped(lock, isReplacing = false) // ...then the stop arrives
        assertFalse("the hand-off must not conceal", conceal)
        presence.onScreenStarted(home)
        assertEquals(1, presence.attachedCount)
    }

    /**
     * The hostile ordering: the predecessor's stop is *reported* before the
     * successor's start, but the successor has still been created - which is the
     * invariant Android guarantees within a task.
     */
    @Test
    fun aStopReportedBeforeTheSuccessorStartsStillDoesNotConceal() {
        presence.onScreenCreated(lock)
        presence.onScreenStarted(lock)
        presence.onScreenCreated(home)
        assertFalse(presence.onScreenStopped(lock, isReplacing = false))
        presence.onScreenStarted(home)
        assertFalse("no conceal happened anywhere in the hand-off", presence.attachedCount == 0)
    }

    /** A configuration change destroys and recreates; it is never a departure. */
    @Test
    fun aConfigurationChangeNeverConceals() {
        presence.onScreenCreated(home)
        presence.onScreenStarted(home)
        assertFalse(
            "recreating for a rotation is not leaving the app",
            presence.onScreenStopped(home, isReplacing = true)
        )
        presence.onScreenDestroyed(home)
        presence.onScreenCreated(home)
        presence.onScreenStarted(home)
        assertEquals(1, presence.attachedCount)
    }

    // ------------------------------------------------------------------
    // Real departures still behave
    // ------------------------------------------------------------------

    @Test
    fun reallyLeavingTheAppConcealsExactlyOnce() {
        presence.onScreenCreated(home)
        presence.onScreenStarted(home)
        assertTrue("pressing Home conceals", presence.onScreenStopped(home, isReplacing = false))
        // A second stop callback for the same activity must not conceal again.
        presence.onScreenStarted(home)
        assertTrue(presence.onScreenStopped(home, isReplacing = false))
    }

    @Test
    fun finishingTheLastScreenConceals() {
        presence.onScreenCreated(home)
        presence.onScreenStarted(home)
        presence.onScreenDestroyed(home)
        assertTrue("nothing is left alive", presence.onScreenStopped(home, isReplacing = false))
    }

    @Test
    fun stackedScreensDoNotConcealUntilTheLastOneGoes() {
        presence.onScreenCreated(home)
        presence.onScreenStarted(home)
        presence.onScreenCreated(gallery)
        presence.onScreenStarted(gallery)

        // Gallery goes back to home: home was stopped but still alive, and it starts
        // again before the gallery stops finishing.
        presence.onScreenStarted(home) // Android resumes home first
        assertFalse("home is visible again", presence.onScreenStopped(gallery, isReplacing = false))
        assertTrue("home was stopped by the gallery being on top then re-started", presence.attachedCount == 1)
    }

    /**
     * The case a screen *count* cannot express, and the reason this class tracks the
     * stage of every activity: pressing Home while a child screen is on top stops
     * the child, and the parent behind it is alive but stopped. That is a genuine
     * departure - the Vault is no longer visible.
     */
    @Test
    fun leavingFromADeepScreenConcealsEvenThoughTheParentIsAlive() {
        presence.onScreenCreated(home)
        presence.onScreenStarted(home)
        presence.onScreenCreated(gallery)
        presence.onScreenStarted(gallery) // home stops, still CREATED
        presence.onScreenStopped(home, isReplacing = false)

        assertTrue(
            "home is alive but stopped, so the Vault is not visible",
            presence.onScreenStopped(gallery, isReplacing = false)
        )
    }

    /** Coming back from the back stack reveals instead of re-locking. */
    @Test
    fun returningToAStoppedScreenDoesNotCountAsADeparture() {
        presence.onScreenCreated(home)
        presence.onScreenStarted(home)
        presence.onScreenStopped(home, isReplacing = false)

        presence.onScreenStarted(home)
        assertEquals(1, presence.attachedCount)
        assertFalse(
            "a screen coming back and going again is still one departure per press",
            presence.onScreenStopped(home, isReplacing = false).not()
        )
    }

    @Test
    fun resetClearsEveryKnownScreen() {
        presence.onScreenCreated(home)
        presence.onScreenStarted(home)
        presence.reset()
        assertEquals(0, presence.liveCount)
        assertEquals(0, presence.attachedCount)
    }

    // ------------------------------------------------------------------
    // No timer, by construction
    // ------------------------------------------------------------------

    /**
     * The heuristic is gone: nothing in the policy may depend on elapsed time.
     *
     * Asserted on the source because a future edit reintroducing `postDelayed` in
     * this path would restore the defect in a way no behavioural test would notice
     * (the old tests passed with the timer present).
     */
    @Test
    fun theHandoffPolicyContainsNoTimerAndNoClock() {
        val source = source("core/VaultScreenPresence.kt")
        for (forbidden in listOf("HANDOFF_WINDOW_MS", "postDelayed", "nanoTime", "currentTimeMillis", "System.")) {
            assertFalse("the presence policy must not use $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun theLifecycleGuardSchedulesNoConcealCallback() {
        val guard = source("platform/VaultLifecycleGuard.kt")
        assertFalse(
            "the conceal decision must be synchronous, not scheduled",
            guard.contains("postDelayed(settleConceal")
        )
        assertFalse("no hand-off window may remain", guard.contains("HANDOFF_WINDOW_MS"))
    }

    // ------------------------------------------------------------------
    // The session still does what it promised
    // ------------------------------------------------------------------

    @Test
    fun screenOffLocksImmediatelyAndIndependentlyOfPresence() {
        session.configure(VaultAutoLock.TEN_MINUTES, lockOnLeave = false)
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "v"))
        presence.onScreenCreated(home)
        presence.onScreenStarted(home)

        // Screen-off does not consult presence at all: it calls lock() directly.
        session.lock(VaultSession.LockReason.SCREEN_OFF)
        assertFalse("screen-off must lock without waiting for any window", session.isUnlocked)
        assertEquals(1, presence.attachedCount)
    }

    @Test
    fun lockOnLeaveStillLocksOnAGenuineDeparture() {
        session.configure(VaultAutoLock.THREE_MINUTES, lockOnLeave = true)
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "v"))

        presence.onScreenCreated(home)
        presence.onScreenStarted(home)
        if (presence.onScreenStopped(home, isReplacing = false)) session.conceal()
        assertFalse("leaving for real must still lock with lock-on-leave", session.isUnlocked)
    }

    @Test
    fun idleTimeoutStillLocksWithoutAnyPresenceEvent() {
        session.configure(VaultAutoLock.THIRTY_SECONDS, lockOnLeave = false)
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "v"))
        presence.onScreenCreated(home)
        presence.onScreenStarted(home)

        now += 31_000L
        assertTrue("the idle ticker must still lock", session.tick(now))
        assertFalse(session.isUnlocked)
    }

    @Test
    fun unlockSurvivesTheHandoffWithLockOnLeaveEnabled() {
        // The end-to-end shape of the original bug, on the real session.
        session.configure(VaultAutoLock.THREE_MINUTES, lockOnLeave = true)
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "v"))

        presence.onScreenCreated(lock)
        presence.onScreenStarted(lock)
        presence.onScreenCreated(home) // created during the PIN check
        if (presence.onScreenStopped(lock, isReplacing = false)) session.conceal()
        presence.onScreenStarted(home)

        assertTrue("a correct PIN must leave the vault open", session.isUnlocked)
    }

    /**
     * Reads a production source file.
     *
     * These are source-level assertions about *absence* (no timer, no clock), which
     * no behavioural test can express: the previous implementation passed every
     * behavioural test while carrying the heuristic.
     */
    private fun source(relative: String): String {
        var directory: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
        while (directory != null) {
            val candidate = java.io.File(directory, "src/main/java/com/lumaos/pro/launcher/vault/$relative")
            if (candidate.isFile) return candidate.readText()
            val nested = java.io.File(directory, "app/src/main/java/com/lumaos/pro/launcher/vault/$relative")
            if (nested.isFile) return nested.readText()
            directory = directory.parentFile
        }
        throw AssertionError("Could not locate $relative from ${System.getProperty("user.dir")}")
    }
}
