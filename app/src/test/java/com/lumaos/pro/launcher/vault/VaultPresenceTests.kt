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

    /** The bug: lock screen stops, then home starts, one transition apart. */
    @Test
    fun aHandoffBetweenTwoVaultScreensNeverConceals() {
        presence.onScreenStarted()          // lock screen
        assertTrue(presence.onScreenStopped()) // lock screen stopping...
        now += 40L
        presence.onScreenStarted()          // ...home screen starting
        assertFalse("the hand-off must not settle into a conceal", presence.settle(now + 400L))
        assertEquals(1, presence.attachedCount)
    }

    /** Even if the window elapses mid-transition, the new screen cancels it. */
    @Test
    fun aLateSettleAfterTheNewScreenStartedDoesNothing() {
        presence.onScreenStarted()
        presence.onScreenStopped()
        now += VaultScreenPresence.HANDOFF_WINDOW_MS + 50
        presence.onScreenStarted() // arrived before the posted callback ran
        assertFalse("stale callback must not conceal", presence.settle(now))
        assertFalse(presence.hasPendingConceal)
    }

    /** A genuine departure still conceals, once. */
    @Test
    fun reallyLeavingTheAppConcealsExactlyOnce() {
        presence.onScreenStarted()
        assertTrue(presence.onScreenStopped())
        val settleAt = now + VaultScreenPresence.HANDOFF_WINDOW_MS
        assertTrue(presence.settle(settleAt))
        assertFalse("a second callback must not conceal twice", presence.settle(settleAt + 1000))
    }

    /** The window is not a security grace period: it does not fire early. */
    @Test
    fun concealWaitsForTheHandoffWindow() {
        presence.onScreenStarted()
        presence.onScreenStopped()
        assertFalse(presence.settle(now + 1))
        assertFalse(presence.settle(now + VaultScreenPresence.HANDOFF_WINDOW_MS - 1))
        assertTrue(presence.settle(now + VaultScreenPresence.HANDOFF_WINDOW_MS))
    }

    /** Nested screens (home -> gallery -> home) never look like a departure. */
    @Test
    fun stackedScreensDoNotConcealUntilTheLastOneGoes() {
        presence.onScreenStarted() // home
        presence.onScreenStarted() // gallery
        assertFalse("one of two screens leaving is not a departure", presence.onScreenStopped())
        assertFalse(presence.hasPendingConceal)
        assertTrue("the last screen leaving is", presence.onScreenStopped())
        assertTrue(presence.settle(now + VaultScreenPresence.HANDOFF_WINDOW_MS))
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
        if (presence.settle(now + VaultScreenPresence.HANDOFF_WINDOW_MS)) session.conceal()
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
        if (presence.settle(now + VaultScreenPresence.HANDOFF_WINDOW_MS)) session.conceal()
        assertTrue("a correct PIN must leave the vault open", session.isUnlocked)
    }

    /** Reset (vault locked) forgets a pending departure. */
    @Test
    fun resetClearsAPendingConceal() {
        presence.onScreenStarted()
        presence.onScreenStopped()
        assertTrue(presence.hasPendingConceal)
        presence.reset()
        assertFalse(presence.hasPendingConceal)
        assertFalse(presence.settle(now + VaultScreenPresence.HANDOFF_WINDOW_MS))
    }
}
