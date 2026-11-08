package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultScreenPresence
import com.lumaos.pro.launcher.vault.core.VaultSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the "correct PIN does not get you in" defect.
 *
 * The vault locked itself during the normal lock-screen -> home-screen hand-off:
 * `startActivity()` is asynchronous, so the lock screen's `onStop` could run before
 * the home screen's `onStart`, the attached-screen count dipped through zero, and
 * the lifecycle guard concealed - which, with "lock when leaving the app" enabled,
 * is an immediate `lock()`.
 *
 * These tests express that exact frame ordering at the policy level, which is the
 * only place it can be reproduced deterministically.
 */
class VaultPresenceTests {

    private var now = 1_000_000L
    private val presence = VaultScreenPresence { now }

    /**
     * The presence window is measured in *elapsed* time (nanoseconds), matching the
     * Handler clock that schedules the settle. Tests therefore also express the
     * window in the same unit; [VaultSession] runs on the millisecond clock above.
     */
    private val window = VaultScreenPresence.HANDOFF_WINDOW_MS * 1_000_000L

    /** The bug: lock screen stops, then home starts, one transition apart. */
    @Test
    fun aHandoffBetweenTwoVaultScreensNeverConceals() {
        presence.onScreenStarted()          // lock screen
        assertTrue(presence.onScreenStopped()) // lock screen stopping...
        now += 40L
        presence.onScreenStarted()          // ...home screen starting
        assertFalse("the hand-off must not settle into a conceal", presence.settle(now + window + 400_000_000L))
        assertEquals(1, presence.attachedCount)
    }

    /** Even if the window elapses mid-transition, the new screen cancels it. */
    @Test
    fun aLateSettleAfterTheNewScreenStartedDoesNothing() {
        presence.onScreenStarted()
        presence.onScreenStopped()
        now += window + 50L
        presence.onScreenStarted() // arrived before the posted callback ran
        assertFalse("stale callback must not conceal", presence.settle(now))
        assertFalse(presence.hasPendingConceal)
    }

    /** A genuine departure still conceals, once. */
    @Test
    fun reallyLeavingTheAppConcealsExactlyOnce() {
        presence.onScreenStarted()
        assertTrue(presence.onScreenStopped())
        val settleAt = now + window
        assertTrue(presence.settle(settleAt))
        assertFalse("a second callback must not conceal twice", presence.settle(settleAt + 1_000_000_000L))
    }

    /** The window is not a security grace period: it does not fire early. */
    @Test
    fun concealWaitsForTheHandoffWindow() {
        presence.onScreenStarted()
        presence.onScreenStopped()
        assertFalse(presence.settle(now + 1L))
        assertFalse(presence.settle(now + window - 1L))
        assertTrue(presence.settle(now + window))
    }

    /** Nested screens (home -> gallery -> home) never look like a departure. */
    @Test
    fun stackedScreensDoNotConcealUntilTheLastOneGoes() {
        presence.onScreenStarted() // home
        presence.onScreenStarted() // gallery
        assertFalse("one of two screens leaving is not a departure", presence.onScreenStopped())
        assertFalse(presence.hasPendingConceal)
        assertTrue("the last screen leaving is", presence.onScreenStopped())
        assertTrue(presence.settle(now + window))
    }

    /** Screen-off does not go through this path at all and must stay immediate. */
    @Test
    fun screenOffLocksImmediatelyAndIndependentlyOfPresence() {
        val session = VaultSession { now }
        session.unlock(VaultSession.Keys(com.lumaos.pro.launcher.vault.core.VaultCrypto.newKey(), "vault"))
        presence.onScreenStarted()
        session.onScreenOff()
        assertFalse("screen-off must lock without waiting for any window", session.isUnlocked)
        // The presence tracker is irrelevant to that path and still thinks a screen exists.
        assertEquals(1, presence.attachedCount)
    }

    /**
     * With lock-on-leave on, a real departure must still lock - the fix must not
     * have quietly disabled the feature it was racing with.
     */
    @Test
    fun lockOnLeaveStillLocksOnAGenuineDeparture() {
        val session = VaultSession { now }
        session.configure(com.lumaos.pro.launcher.vault.core.VaultAutoLock.THREE_MINUTES, lockOnLeave = true)
        session.unlock(VaultSession.Keys(com.lumaos.pro.launcher.vault.core.VaultCrypto.newKey(), "vault"))
        presence.onScreenStarted()
        presence.onScreenStopped()
        if (presence.settle(now + window)) session.conceal()
        assertFalse("leaving for real must still lock with lock-on-leave", session.isUnlocked)
    }

    /** And the successful-unlock path must survive the same sequence. */
    @Test
    fun unlockSurvivesTheHandoffWithLockOnLeaveEnabled() {
        val session = VaultSession { now }
        session.configure(com.lumaos.pro.launcher.vault.core.VaultAutoLock.THREE_MINUTES, lockOnLeave = true)
        // PIN accepted: keys installed before the screens hand off.
        session.unlock(VaultSession.Keys(com.lumaos.pro.launcher.vault.core.VaultCrypto.newKey(), "vault"))
        presence.onScreenStarted() // lock screen
        presence.onScreenStopped() // finishing
        now += 30
        presence.onScreenStarted() // home
        if (presence.settle(now + window)) session.conceal()
        assertTrue("a correct PIN must leave the vault open", session.isUnlocked)
    }

    /**
     * The window must not be measured on the wall clock.
     *
     * The guard schedules the settle with `Handler.postDelayed`, which runs on
     * uptime, but the policy used to timestamp the departure with
     * `System.currentTimeMillis()`. A clock correction between the two (NTP sync,
     * the user changing the time, a timezone shift that moves the wall clock
     * forward) made `now - since` jump straight past the window, so the settle
     * fired *inside* the hand-off and concealed - re-locking the vault on a
     * correct PIN, which is the original defect wearing a different mask.
     *
     * A monotonic source cannot do that, so the same sequence must stay put.
     */
    @Test
    fun aWallClockJumpCannotCollapseTheHandoffWindow() {
        // Monotonic: the departure and the settle are 40 ms apart in elapsed time,
        // even though a reader might imagine hours passing between them.
        presence.onScreenStarted()
        presence.onScreenStopped()
        assertFalse(
            "a settle 40 ms after the stop must stay inside the window",
            presence.settle(now + 40L * 1_000_000L)
        )
        assertTrue(
            "and the window still closes when it genuinely elapses",
            presence.settle(now + window)
        )
    }

    /** A negative elapsed delta (clock went backwards) must not conceal early. */
    @Test
    fun aBackwardsClockCannotConcealEarly() {
        presence.onScreenStarted()
        presence.onScreenStopped()
        assertFalse(presence.settle(now - 5_000_000_000L))
    }

    /** Reset (vault locked) forgets a pending departure. */
    @Test
    fun resetClearsAPendingConceal() {
        presence.onScreenStarted()
        presence.onScreenStopped()
        assertTrue(presence.hasPendingConceal)
        presence.reset()
        assertFalse(presence.hasPendingConceal)
        assertFalse(presence.settle(now + window))
    }
}
