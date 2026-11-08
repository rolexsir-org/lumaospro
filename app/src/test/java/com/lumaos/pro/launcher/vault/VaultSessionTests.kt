package com.lumaos.pro.launcher.vault

import com.lumaos.pro.launcher.vault.core.VaultAutoLock
import com.lumaos.pro.launcher.vault.core.VaultCrypto
import com.lumaos.pro.launcher.vault.core.VaultSession
import com.lumaos.pro.launcher.vault.core.VaultGuardAuthenticator
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

    /**
     * A present-but-unreadable record is damage or tampering, not a first run.
     *
     * It must never do the two things a malformed file could otherwise be used for:
     * lock the owner out permanently, or reset the counter to a clean slate.
     */
    @Test
    fun unreadableGuardFileStartsFromAPenaltyButNeverTrapsTheOwner() {
        val file = Files.createTempFile("vault-guard", ".json").toFile()
        file.writeText("this is not json")
        // Real wall-clock values: a file's mtime cannot precede the epoch.
        val t = 1_700_000_000_000L
        file.setLastModified(t - 60 * 60_000L)
        val guard = VaultUnlockGuard(file)

        val state = guard.state(t)
        assertTrue("a damaged record is marked", state.damaged)
        assertTrue("counter is seeded above the free attempts", state.failures > VaultUnlockGuard.FREE_ATTEMPTS)
        assertFalse("the one-off penalty has already run out", state.isThrottled(t))

        // The penalty is anchored to the file's own timestamp, so it cannot renew
        // itself: an hour later the record still reads the same.
        assertEquals(state.failures, guard.state(t + 60 * 60_000L).failures)
        file.delete()
    }

    /** A truncated or absent field is read defensively, never trusted as a clean state. */
    @Test
    fun malformedGuardJsonIsReadAsDamagedState() {
        val cases = listOf(
            "{}",
            "{\"failures\":\"lots\"}",
            "[]",
            "",
            "{\"failures\":-40,\"lockedUntil\":-1}",
            "{\"failures\":2147483647,\"lockedUntil\":0,\"lastFailure\":0}"
        )
        val t = 1_700_000_000_000L
        for (text in cases) {
            val file = Files.createTempFile("vault-guard", ".json").toFile()
            file.writeText(text)
            file.setLastModified(t - 60 * 60_000L)
            val guard = VaultUnlockGuard(file)
            val state = guard.state(t)
            assertTrue("'$text' is read as damaged", state.damaged)
            assertTrue("'$text' seeds failures", state.failures >= VaultUnlockGuard.FREE_ATTEMPTS)
            assertTrue("'$text' is not throttled forever", state.remainingDelayMs(t) <= VaultUnlockGuard.MAX_DELAY_MS)
            file.delete()
        }
    }

    /** A forged record can never impose more than the cap, nor a negative count. */
    @Test
    fun forgedGuardValuesAreClamped() {
        val file = Files.createTempFile("vault-guard", ".json").toFile()
        // A count inside the tracked range but a deadline far ahead: clamped, not honoured.
        file.writeText("{\"failures\":900,\"lockedUntil\":9223372036854775807,\"lastFailure\":9223372036854775807}")
        val guard = VaultUnlockGuard(file)
        val t = 1_700_000_000_000L
        val state = guard.state(t)
        assertEquals(900, state.failures)
        assertTrue(state.remainingDelayMs(t) <= VaultUnlockGuard.MAX_DELAY_MS)
        assertTrue(state.lastFailureMs <= t)
        file.delete()
    }

    /**
     * The guard has no key material of its own.
     *
     * Authentication lives behind [VaultGuardAuthenticator] in a Keystore key that is
     * never exported, so this class cannot hand out anything that would let a caller
     * forge a record.
     */
    @Test
    fun theGuardHoldsNoKeyMaterialItself() {
        assertFalse(
            "the guard must not expose key material",
            VaultUnlockGuard::class.java.declaredMethods.any { m ->
                m.name.lowercase().contains("key") && !m.name.contains("keyring")
            }
        )
    }

    /**
     * Deleting the record does **not** reset the delay once the guard is in use.
     *
     * This is the residual risk the previous revision documented as a limitation.
     * With a Keystore-backed authenticator the record can no longer be removed for a
     * clean slate: the signing entry outlives the file, so an absent record reads as
     * a deletion and starts above the free attempts.
     */
    @Test
    fun deletingTheRecordDoesNotResetTheDelay() {
        val file = guardFile()
        val signer = FakeGuardAuthenticator()
        val guard = VaultUnlockGuard(file, signer)
        repeat(8) { guard.recordFailure(now) }
        assertTrue(guard.state(now).isThrottled(now))

        // Attacker removes the record to get a clean counter.
        file.delete()

        val reloaded = VaultUnlockGuard(file, signer)
        val state = reloaded.state(now)
        assertTrue("deletion is reported", state.deleted)
        assertTrue("and it is not a clean slate", state.failures > VaultUnlockGuard.FREE_ATTEMPTS)
        assertFalse("and it is never a free attempt", VaultUnlockGuard.delayForFailures(state.failures) == 0L)
    }

    /**
     * Deleting the record must grant **zero** attempts, not one per deletion.
     *
     * Without this the previous fix would be cosmetic: an attacker who deletes the
     * file before every guess would never accumulate failures. The penalty is
     * persisted on detection, so the attempt that follows is refused.
     */
    @Test
    fun deletingTheRecordGrantsNoAttemptsAtAll() {
        val file = guardFile()
        val signer = FakeGuardAuthenticator()
        val guard = VaultUnlockGuard(file, signer, vaultId = { "v" })
        repeat(8) { guard.recordFailure(now) }

        // Three rounds of "delete, then try to guess".
        repeat(3) {
            file.delete()
            val state = VaultUnlockGuard(file, signer, vaultId = { "v" }).state(now)
            assertTrue("an attempt after a deletion is throttled", state.isThrottled(now))
            assertTrue(
                "and the wait is the full cap",
                state.remainingDelayMs(now) >= VaultUnlockGuard.MAX_DELAY_MS - 1L
            )
        }
        assertTrue("the penalty record is durable", file.isFile)
    }

    /**
     * The deletion penalty is written once, not renewed on every read.
     *
     * Otherwise rendering the lock screen in a loop would push the deadline forward
     * forever and trap the owner out of their own vault.
     */
    @Test
    fun theDeletionPenaltyDoesNotRenewItselfOnEveryRead() {
        val file = guardFile()
        val signer = FakeGuardAuthenticator()
        VaultUnlockGuard(file, signer, vaultId = { "v" }).recordFailure(now)
        file.delete()

        val guard = VaultUnlockGuard(file, signer, vaultId = { "v" })
        val first = guard.state(now)
        val second = guard.state(now + 60_000L)
        val third = guard.state(now + 120_000L)
        assertEquals("the deadline is fixed by the first detection", first.lockedUntilMs, second.lockedUntilMs)
        assertEquals(first.lockedUntilMs, third.lockedUntilMs)
        assertTrue("and it does eventually expire", third.stateAfterExpiry(now + VaultUnlockGuard.MAX_DELAY_MS + 1L))
    }

    /** Helper: true when the deadline has passed by [t]. */
    private fun VaultUnlockGuard.State.stateAfterExpiry(t: Long): Boolean = !isThrottled(t)

    /** A forged record with a wrong or missing tag is rejected, not obeyed. */
    @Test
    fun anEditedRecordIsDetectedAsTampering() {
        val file = guardFile()
        val signer = FakeGuardAuthenticator()
        val guard = VaultUnlockGuard(file, signer, vaultId = { "vault-1" })
        repeat(8) { guard.recordFailure(now) }
        val honest = file.readText()

        // Reset the count in the file, keeping the signature the guard wrote.
        file.writeText(honest.replace(Regex("\"failures\": \\d+"), "\"failures\": 0"))
        val state = VaultUnlockGuard(file, signer, vaultId = { "vault-1" }).state(now)
        assertTrue("an edited count is tampering", state.tampered || state.damaged)
        assertTrue("and it never reads as a clean slate", state.failures > VaultUnlockGuard.FREE_ATTEMPTS)

        // Removing the tag entirely is equally rejected.
        file.writeText(Regex(",\\s*\"tag\": \"[^\"]*\"").replace(honest, ""))
        val stripped = VaultUnlockGuard(file, signer, vaultId = { "vault-1" }).state(now)
        assertTrue(stripped.suspicious)
        assertTrue(stripped.failures > VaultUnlockGuard.FREE_ATTEMPTS)
    }

    /** A record authenticated for one vault cannot be replayed into another. */
    @Test
    fun aRecordFromAnotherVaultIsRejected() {
        val file = guardFile()
        val signer = FakeGuardAuthenticator()
        VaultUnlockGuard(file, signer, vaultId = { "vault-A" }).recordFailure(now)

        val state = VaultUnlockGuard(file, signer, vaultId = { "vault-B" }).state(now)
        assertTrue("the tag does not cover another vault id", state.tampered)
        assertTrue(state.failures > VaultUnlockGuard.FREE_ATTEMPTS)
    }

    /** A genuine record signed by this device still verifies and is obeyed. */
    @Test
    fun anAuthenticRecordIsObeyed() {
        val file = guardFile()
        val signer = FakeGuardAuthenticator()
        val guard = VaultUnlockGuard(file, signer, vaultId = { "vault-1" })
        repeat(5) { guard.recordFailure(now) }

        val reopened = VaultUnlockGuard(file, signer, vaultId = { "vault-1" }).state(now)
        assertEquals(5, reopened.failures)
        assertFalse("an untouched record is not suspicious", reopened.suspicious)
        assertTrue(reopened.isThrottled(now))
        assertEquals(30_000L, reopened.remainingDelayMs(now))
    }

    /** A successful unlock leaves a valid, clean record - not a missing file. */
    @Test
    fun successWritesAZeroedRecordRatherThanDeletingIt() {
        val file = guardFile()
        val signer = FakeGuardAuthenticator()
        val guard = VaultUnlockGuard(file, signer, vaultId = { "vault-1" })
        repeat(6) { guard.recordFailure(now) }
        guard.recordSuccess()

        assertTrue("the record still exists", file.isFile)
        val state = VaultUnlockGuard(file, signer, vaultId = { "vault-1" }).state(now)
        assertEquals(0, state.failures)
        assertFalse("and it is not misread as a deletion", state.deleted)
    }

    /** Only a new vault may establish a genuinely fresh state. */
    @Test
    fun onlyCreatingANewVaultResetsTheRecord() {
        val file = guardFile()
        val signer = FakeGuardAuthenticator()
        val guard = VaultUnlockGuard(file, signer, vaultId = { "vault-1" })
        repeat(8) { guard.recordFailure(now) }
        assertTrue(guard.state(now).isThrottled(now))

        guard.resetForNewVault()
        val state = VaultUnlockGuard(file, signer, vaultId = { "vault-1" }).state(now)
        assertEquals(0, state.failures)
        assertFalse(state.suspicious)
    }

    /**
     * Without a usable Keystore the guard degrades instead of locking the owner out.
     *
     * A device whose HMAC key cannot be created must still be able to record and read
     * its own records; otherwise a platform limitation would look like tampering on
     * every attempt.
     */
    @Test
    fun aDeviceWithoutASigningKeyFallsBackToTheUnprotectedRecord() {
        val file = guardFile()
        val unavailable = object : VaultGuardAuthenticator {
            override fun isInitialised(): Boolean = false
            override fun ensureInitialised() = Unit
            override fun sign(data: ByteArray): ByteArray? = null
            override fun verify(data: ByteArray, tag: ByteArray): Boolean = false
        }
        val guard = VaultUnlockGuard(file, unavailable)
        repeat(5) { guard.recordFailure(now) }

        val state = VaultUnlockGuard(file, unavailable).state(now)
        assertEquals("the record round-trips", 5, state.failures)
        assertFalse("and is not misreported as tampered", state.suspicious)
    }
}
