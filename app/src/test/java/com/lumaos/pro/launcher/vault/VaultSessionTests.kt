package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultAutoLock
import com.lumaos.pro.launcher.vault.core.VaultCrypto
import com.lumaos.pro.launcher.vault.core.VaultSession
import com.lumaos.pro.launcher.vault.core.VaultUnlockGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Lifecycle tests for the session and the unlock throttle.
 *
 * The clock is injected, so "the phone sat idle for three minutes" is a one-line
 * test rather than a three-minute one.
 */
class VaultSessionTests {

    private var now = 1_000_000L
    private val session = VaultSession { now }

    @Test
    fun startsLockedAndWithoutKeys() {
        assertEquals(VaultSession.State.LOCKED, session.state)
        assertFalse(session.isUnlocked)
        assertNull(session.currentKeys())
    }

    @Test
    fun unlockingExposesKeysAndNotifiesListeners() {
        var unlockedCount = 0
        session.addListener(object : VaultSession.Listener {
            override fun onUnlocked(keys: VaultSession.Keys) {
                unlockedCount++
            }
        })
        val key = VaultCrypto.newKey()
        session.unlock(VaultSession.Keys(key, "vault"))
        assertEquals(VaultSession.State.UNLOCKED, session.state)
        assertEquals(1, unlockedCount)
        assertTrue(session.currentKeys()!!.isUsable())
    }

    @Test
    fun lockingZeroizesTheMasterKey() {
        val key = VaultCrypto.newKey()
        val keys = VaultSession.Keys(key, "vault")
        session.unlock(keys)
        var lockedReason: VaultSession.LockReason? = null
        session.addListener(object : VaultSession.Listener {
            override fun onLocked(reason: VaultSession.LockReason) {
                lockedReason = reason
            }
        })
        session.lock(VaultSession.LockReason.USER)
        assertEquals(VaultSession.State.LOCKED, session.state)
        assertEquals(VaultSession.LockReason.USER, lockedReason)
        assertTrue("key bytes must be zeroized", key.all { it == 0.toByte() })
        assertFalse(keys.isUsable())
        assertNull(session.currentKeys())
    }

    @Test
    fun screenOffLocksImmediately() {
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "vault"))
        session.onScreenOff()
        assertEquals(VaultSession.State.LOCKED, session.state)
    }

    @Test
    fun idleTimeoutLocksAfterTheConfiguredDelay() {
        session.configure(VaultAutoLock.THIRTY_SECONDS, lockOnLeave = false)
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "vault"))
        now += 29_000
        assertFalse(session.tick(now))
        assertEquals(VaultSession.State.UNLOCKED, session.state)
        now += 2_000
        assertTrue(session.tick(now))
        assertEquals(VaultSession.State.LOCKED, session.state)
    }

    @Test
    fun interactionResetsTheIdleTimer() {
        session.configure(VaultAutoLock.ONE_MINUTE, lockOnLeave = false)
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "vault"))
        repeat(5) {
            now += 50_000
            session.touch()
            assertFalse(session.tick(now))
        }
        assertEquals(VaultSession.State.UNLOCKED, session.state)
    }

    @Test
    fun concealHidesContentButKeepsTheSessionAlive() {
        session.configure(VaultAutoLock.TEN_MINUTES, lockOnLeave = false)
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "vault"))
        var concealed = false
        var revealed = false
        session.addListener(object : VaultSession.Listener {
            override fun onConcealed() {
                concealed = true
            }

            override fun onRevealed() {
                revealed = true
            }
        })
        session.conceal()
        assertEquals(VaultSession.State.UNLOCKED_CONCEALED, session.state)
        assertTrue(concealed)
        session.reveal()
        assertEquals(VaultSession.State.UNLOCKED, session.state)
        assertTrue(revealed)
    }

    @Test
    fun lockOnLeaveLocksAsSoonAsTheVaultIsHidden() {
        session.configure(VaultAutoLock.TEN_MINUTES, lockOnLeave = true)
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "vault"))
        session.conceal()
        assertEquals(VaultSession.State.LOCKED, session.state)
    }

    @Test
    fun concealedSessionStillExpires() {
        session.configure(VaultAutoLock.ONE_MINUTE, lockOnLeave = false)
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "vault"))
        session.conceal()
        now += 61_000
        assertTrue(session.tick(now))
        assertEquals(VaultSession.State.LOCKED, session.state)
    }

    @Test
    fun processDeathLocksSilently() {
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "vault"))
        var notified = false
        session.addListener(object : VaultSession.Listener {
            override fun onLocked(reason: VaultSession.LockReason) {
                notified = true
            }
        })
        session.onProcessDeath()
        assertEquals(VaultSession.State.LOCKED, session.state)
        assertFalse("process death must not run UI callbacks", notified)
    }

    @Test
    fun reUnlockAfterLockWorks() {
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "vault"))
        session.lock(VaultSession.LockReason.TIMEOUT)
        val key = VaultCrypto.newKey()
        session.unlock(VaultSession.Keys(key, "vault"))
        assertEquals(VaultSession.State.UNLOCKED, session.state)
        assertNotNull(session.currentKeys())
    }

    @Test
    fun millisUntilIdleReportsTheRemainingWindow() {
        session.configure(VaultAutoLock.THREE_MINUTES, lockOnLeave = false)
        session.unlock(VaultSession.Keys(VaultCrypto.newKey(), "vault"))
        assertEquals(180_000L, session.millisUntilIdle())
        now += 60_000
        assertEquals(120_000L, session.millisUntilIdle())
    }

    // ------------------------------------------------------------------
    // Unlock throttle
    // ------------------------------------------------------------------

    private fun guardFile(): File = Files.createTempFile("vault-guard", ".json").toFile().also { it.delete() }

    @Test
    fun firstAttemptsAreFreeThenDelaysGrow() {
        val guard = VaultUnlockGuard(guardFile())
        assertEquals(0L, VaultUnlockGuard.delayForFailures(1))
        assertEquals(0L, VaultUnlockGuard.delayForFailures(2))
        assertEquals(0L, VaultUnlockGuard.delayForFailures(3))
        assertEquals(10_000L, VaultUnlockGuard.delayForFailures(4))
        assertEquals(30_000L, VaultUnlockGuard.delayForFailures(5))
        assertEquals(60_000L, VaultUnlockGuard.delayForFailures(6))
        assertEquals(5 * 60_000L, VaultUnlockGuard.delayForFailures(7))
        assertEquals(15 * 60_000L, VaultUnlockGuard.delayForFailures(8))
        assertEquals(15 * 60_000L, VaultUnlockGuard.delayForFailures(50))
        assertTrue(guard.state().failures == 0)
    }

    @Test
    fun failuresPersistAcrossProcessRestarts() {
        val file = guardFile()
        val first = VaultUnlockGuard(file)
        repeat(5) { first.recordFailure(now) }
        val second = VaultUnlockGuard(file)
        assertEquals(5, second.state().failures)
        assertTrue(second.state().isThrottled(now))
        assertEquals(30_000L, second.state().remainingDelayMs(now))
    }

    @Test
    fun successClearsTheCounters() {
        val file = guardFile()
        val guard = VaultUnlockGuard(file)
        repeat(4) { guard.recordFailure(now) }
        assertTrue(guard.state().isThrottled(now))
        guard.recordSuccess()
        assertEquals(0, guard.state().failures)
        assertFalse(guard.state().isThrottled(now))
    }

    @Test
    fun unreadableGuardFileDoesNotLockTheUserOut() {
        val file = Files.createTempFile("vault-guard", ".json").toFile()
        file.writeText("this is not json")
        val guard = VaultUnlockGuard(file)
        assertEquals(0, guard.state().failures)
        assertFalse(guard.state().isThrottled(now))
        file.delete()
    }
}
