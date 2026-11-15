package com.lumaos.pro.launcher.context

import com.lumaos.pro.launcher.core.model.ComponentKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The consent lifecycle behind the accessibility disclosure, on the JVM:
 * first run, acceptance, refusal, relaunch, revocation by Android settings,
 * service disabled, withdrawal, and Focus Protection without the permission.
 *
 * The permission itself is supplied as a boolean here because
 * [FocusAccess.isGranted] reads `Settings.Secure` (covered by the Robolectric
 * test). What matters at this level is that no combination of stored consent
 * and permission can enforce without an explicit, current agreement.
 */
class FocusAccessConsentTest {

    private val now = 1_700_000_000_000L

    private class FakeStore(
        var record: FocusAccessConsentRecord = FocusAccessConsent.NOT_ASKED
    ) : FocusAccessConsentStore {
        var writes = 0
        override suspend fun readConsent(): FocusAccessConsentRecord = record
        override suspend fun writeConsent(record: FocusAccessConsentRecord) {
            this.record = record
            writes++
        }
    }

    private fun flow(store: FocusAccessConsentStore, clock: () -> Long = { now }) =
        FocusAccessConsentFlow(store, clock)

    @Test fun `first launch asks for the disclosure and enforces nothing`() {
        val store = FakeStore()
        val record = store.record
        assertTrue(FocusAccessConsent.requiresDisclosure(record))
        assertEquals(
            FocusAccessUiState.NEEDS_DISCLOSURE,
            FocusAccessConsent.uiState(record, permissionGranted = false)
        )
        // Even if the service were already enabled, consent has not been given.
        assertEquals(
            FocusAccessUiState.NEEDS_DISCLOSURE,
            FocusAccessConsent.uiState(record, permissionGranted = true)
        )
        assertFalse(FocusAccessConsent.mayEnforce(record, permissionGranted = true))
    }

    @Test fun `accepting records consent at the current disclosure version before returning`() = runBlocking {
        val store = FakeStore()
        val decision = flow(store).agree()

        assertEquals(FocusAccessConsentState.GRANTED, decision.state)
        assertEquals(FocusAccessConsent.CURRENT_DISCLOSURE_VERSION, decision.disclosureVersion)
        assertEquals(now, decision.decidedAtMillis)
        // The write happened as part of agree(): a process death right after the
        // system screen opens still leaves the agreement on disk.
        assertEquals(1, store.writes)
        assertEquals(decision, store.record)
        assertFalse(FocusAccessConsent.requiresDisclosure(store.record))
    }

    @Test fun `accepting alone does not enforce while Android withholds the grant`() = runBlocking {
        val store = FakeStore()
        flow(store).agree()
        assertFalse(FocusAccessConsent.mayEnforce(store.record, permissionGranted = false))
        assertEquals(
            FocusAccessUiState.AWAITING_PERMISSION,
            FocusAccessConsent.uiState(store.record, permissionGranted = false)
        )
    }

    @Test fun `refusing is persisted and never enforces even when Android has the grant`() = runBlocking {
        val store = FakeStore()
        val decision = flow(store).decline()

        assertEquals(FocusAccessConsentState.DECLINED, decision.state)
        assertEquals(1, store.writes)
        assertFalse(FocusAccessConsent.grantsAccess(store.record))
        assertFalse(FocusAccessConsent.mayEnforce(store.record, permissionGranted = true))
        assertEquals(
            FocusAccessUiState.DECLINED,
            FocusAccessConsent.uiState(store.record, permissionGranted = true)
        )
        // A refusal at the current version is an answer: the app must not
        // re-present the disclosure by itself.
        assertFalse(FocusAccessConsent.requiresDisclosure(store.record))
    }

    @Test fun `relaunch after consent keeps the recorded agreement`() = runBlocking {
        val store = FakeStore()
        flow(store).agree()

        // A new process builds a new flow over the same stored record.
        val relaunched = flow(store).read()
        assertEquals(FocusAccessConsentState.GRANTED, relaunched.state)
        assertFalse(FocusAccessConsent.requiresDisclosure(relaunched))
        assertTrue(FocusAccessConsent.mayEnforce(relaunched, permissionGranted = true))
        assertEquals(
            FocusAccessUiState.ACTIVE,
            FocusAccessConsent.uiState(relaunched, permissionGranted = true)
        )
    }

    @Test fun `revoking the grant in Android settings stops enforcement and keeps consent`() = runBlocking {
        val store = FakeStore()
        flow(store).agree()

        val revoked = store.record
        assertFalse(FocusAccessConsent.mayEnforce(revoked, permissionGranted = false))
        assertEquals(
            FocusAccessUiState.AWAITING_PERMISSION,
            FocusAccessConsent.uiState(revoked, permissionGranted = false)
        )
        // The user does not have to answer the disclosure again to re-enable it.
        assertFalse(FocusAccessConsent.requiresDisclosure(revoked))
    }

    @Test fun `accessibility service disabled reads as awaiting permission`() = runBlocking {
        val store = FakeStore()
        flow(store).agree()
        // "Service disabled" and "service never enabled" are the same fact to the
        // app: Android reports no grant. Both must be a supported steady state
        // that leaves the recorded consent untouched and does not re-prompt.
        val whileDisabled = FocusAccessConsent.uiState(store.record, permissionGranted = false)
        val onceReEnabled = FocusAccessConsent.uiState(store.record, permissionGranted = true)
        assertEquals(FocusAccessUiState.AWAITING_PERMISSION, whileDisabled)
        assertEquals(FocusAccessUiState.ACTIVE, onceReEnabled)
        assertFalse(FocusAccessConsent.requiresDisclosure(store.record))
        assertFalse(FocusAccessConsent.mayEnforce(store.record, permissionGranted = false))
    }

    @Test fun `enabling the service in Android settings without the in-app disclosure does not enforce`() {
        val record = FocusAccessConsent.NOT_ASKED
        assertEquals(
            FocusAccessUiState.NEEDS_DISCLOSURE,
            FocusAccessConsent.uiState(record, permissionGranted = true)
        )
        assertFalse(FocusAccessConsent.mayEnforce(record, permissionGranted = true))
    }

    @Test fun `withdrawing consent stops enforcement while the system grant is still on`() = runBlocking {
        val store = FakeStore()
        flow(store).agree()
        assertTrue(FocusAccessConsent.mayEnforce(store.record, permissionGranted = true))

        val withdrawn = flow(store).withdraw()
        assertEquals(FocusAccessConsentState.DECLINED, withdrawn.state)
        assertFalse(FocusAccessConsent.mayEnforce(store.record, permissionGranted = true))
        assertEquals(
            FocusAccessUiState.DECLINED,
            FocusAccessConsent.uiState(store.record, permissionGranted = true)
        )
    }

    @Test fun `an older disclosure version requires a fresh answer`() {
        val stale = FocusAccessConsentRecord(
            state = FocusAccessConsentState.GRANTED,
            disclosureVersion = FocusAccessConsent.CURRENT_DISCLOSURE_VERSION - 1,
            decidedAtMillis = now
        )
        // A materially changed disclosure cannot be satisfied by an old agreement...
        assertTrue(FocusAccessConsent.requiresDisclosure(stale))
        assertFalse(FocusAccessConsent.grantsAccess(stale))
        assertFalse(FocusAccessConsent.mayEnforce(stale, permissionGranted = true))
        assertEquals(
            FocusAccessUiState.NEEDS_DISCLOSURE,
            FocusAccessConsent.uiState(stale, permissionGranted = true)
        )
        // ...and answering again restores enforcement without touching the grant.
        assertTrue(
            FocusAccessConsent.mayEnforce(FocusAccessConsent.agree(now), permissionGranted = true)
        )
    }

    @Test fun `unknown stored state degrades to not asked`() {
        assertEquals(FocusAccessConsentState.NOT_ASKED, FocusAccessConsent.parseState(null))
        assertEquals(FocusAccessConsentState.NOT_ASKED, FocusAccessConsent.parseState(""))
        assertEquals(FocusAccessConsentState.NOT_ASKED, FocusAccessConsent.parseState("ALLOW_EVERYTHING"))
        assertEquals(FocusAccessConsentState.GRANTED, FocusAccessConsent.parseState("GRANTED"))
        assertEquals(FocusAccessConsentState.DECLINED, FocusAccessConsent.parseState("DECLINED"))
    }

    @Test fun `consent decisions use the injected clock`() = runBlocking {
        val store = FakeStore()
        val decision = flow(store, clock = { 42L }).agree()
        assertEquals(42L, decision.decidedAtMillis)
    }

    /**
     * Requirement: declining must not damage anything else. Layer 1
     * ([FocusEnforcement.shouldBlock]) has no consent or permission input at
     * all, so a refused accessibility grant cannot change its verdict.
     */
    @Test fun `launcher enforcement is unaffected by declined consent`() {
        val game = "com.example.game"
        val own = "com.lumaos.pro.launcher"
        val snapshot = FocusEnforcement.Snapshot(
            active = ContextProfile.WORK,
            focusEnabled = true,
            restrictedPackages = setOf(game),
            unlockProfile = null,
            unlockUntilMillis = 0L
        )
        assertTrue(FocusEnforcement.shouldBlock(snapshot, game, own, now))
        assertTrue(
            FocusEnforcement.restrictedPackages(setOf(ComponentKey(game, "$game.Main")))
                .contains(game)
        )
        val declined = FocusAccessConsent.decline(now)
        assertFalse(FocusAccessConsent.mayEnforce(declined, permissionGranted = false))
        // The same decision the user got before the accessibility feature existed.
        assertTrue(FocusEnforcement.shouldBlock(snapshot, game, own, now))
    }
}
