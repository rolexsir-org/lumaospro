package com.lumaos.pro.launcher.core.prefs

import com.lumaos.pro.launcher.backup.BackupCodec
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.context.FocusAccessConsent
import com.lumaos.pro.launcher.context.FocusAccessConsentRecord
import com.lumaos.pro.launcher.context.FocusAccessConsentState
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Disclosure consent persistence, against a real DataStore file.
 *
 * This is the "relaunch" and "revoke" evidence at the storage layer: a fresh
 * [LumaPrefs] over the same file (what happens after process death) reads the
 * same answer, and consent is stored under keys that are independent of every
 * Focus Protection setting and of Android's grant.
 *
 * Each store is closed (scope cancelled and joined) before the next one opens
 * the same file: DataStore refuses two live instances for one file.
 */
class FocusAccessConsentPrefsTest {

    private val now = 1_700_000_000_000L

    @Test fun `first run has no answer and no consent`() = withDir { dir ->
        withStore(dir) { prefs ->
            val record = prefs.focusAccessConsent.first()
            assertEquals(FocusAccessConsentState.NOT_ASKED, record.state)
            assertEquals(0, record.disclosureVersion)
            assertEquals(0L, record.decidedAtMillis)
            assertTrue(FocusAccessConsent.requiresDisclosure(record))
            assertFalse(FocusAccessConsent.grantsAccess(record))
        }
    }

    @Test fun `agreement survives relaunch`() = withDir { dir ->
        withStore(dir) { it.setFocusAccessConsent(FocusAccessConsent.agree(now)) }

        // Process death: a new DataStore instance over the same file.
        withStore(dir) { relaunched ->
            val record = relaunched.focusAccessConsent.first()
            assertEquals(FocusAccessConsentState.GRANTED, record.state)
            assertEquals(FocusAccessConsent.CURRENT_DISCLOSURE_VERSION, record.disclosureVersion)
            assertEquals(now, record.decidedAtMillis)
            assertFalse(FocusAccessConsent.requiresDisclosure(record))
            assertTrue(FocusAccessConsent.mayEnforce(record, permissionGranted = true))
        }
    }

    @Test fun `refusal survives relaunch and is not overwritten by other writes`() = withDir { dir ->
        withStore(dir) { prefs ->
            prefs.setFocusAccessConsent(FocusAccessConsent.decline(now))
            // Unrelated settings churn must not touch the consent answer.
            prefs.setFocusEnabled(ContextProfile.WORK, true)
            prefs.setFocusUnlockMinutes(15)
        }
        withStore(dir) { relaunched ->
            val record = relaunched.focusAccessConsent.first()
            assertEquals(FocusAccessConsentState.DECLINED, record.state)
            assertFalse(FocusAccessConsent.mayEnforce(record, permissionGranted = true))
        }
    }

    @Test fun `withdrawal replaces an earlier agreement`() = withDir { dir ->
        withStore(dir) { prefs ->
            prefs.setFocusAccessConsent(FocusAccessConsent.agree(now))
            assertTrue(FocusAccessConsent.grantsAccess(prefs.focusAccessConsent.first()))

            prefs.setFocusAccessConsent(FocusAccessConsent.decline(now + 1_000L))
            val withdrawn = prefs.focusAccessConsent.first()
            assertEquals(FocusAccessConsentState.DECLINED, withdrawn.state)
            assertEquals(now + 1_000L, withdrawn.decidedAtMillis)
            assertFalse(FocusAccessConsent.mayEnforce(withdrawn, permissionGranted = true))
        }
    }

    @Test fun `a stale recorded version is stored and read back verbatim`() = withDir { dir ->
        withStore(dir) { prefs ->
            prefs.setFocusAccessConsent(
                FocusAccessConsentRecord(
                    state = FocusAccessConsentState.GRANTED,
                    disclosureVersion = FocusAccessConsent.CURRENT_DISCLOSURE_VERSION - 1,
                    decidedAtMillis = now
                )
            )
        }
        withStore(dir) { relaunched ->
            val stale = relaunched.focusAccessConsent.first()
            assertEquals(
                FocusAccessConsent.CURRENT_DISCLOSURE_VERSION - 1,
                stale.disclosureVersion
            )
            assertFalse(FocusAccessConsent.grantsAccess(stale))
            assertTrue(FocusAccessConsent.requiresDisclosure(stale))
        }
    }

    @Test fun `consent keys stay out of the in app backup`() {
        // LumaOS Pro's own export/restore is a whitelist: consent is not in it.
        // (Android's system backup copies the settings DataStore as a whole; that
        // is documented in docs/ACCESSIBILITY_DISCLOSURE.md.)
        assertNull(BackupCodec.expectedType("focus_access_consent_state"))
        assertNull(BackupCodec.expectedType("focus_access_consent_version"))
        assertNull(BackupCodec.expectedType("focus_access_consent_at"))
    }

    @Test fun `consent is separate from the per context focus setting`() = withDir { dir ->
        withStore(dir) { prefs ->
            prefs.setFocusAccessConsent(FocusAccessConsent.agree(now))
            // Agreeing is not "turning Focus Protection on": the session toggle and
            // the restriction list stay exactly as the user configured them.
            assertFalse(prefs.focusEnabled(ContextProfile.WORK).first())
            assertTrue(prefs.focusRestricted(ContextProfile.WORK).first().isEmpty())
            assertTrue(FocusAccessConsent.grantsAccess(prefs.focusAccessConsent.first()))

            prefs.setFocusEnabled(ContextProfile.WORK, true)
            prefs.setFocusEnabled(ContextProfile.WORK, false)
            assertTrue(FocusAccessConsent.grantsAccess(prefs.focusAccessConsent.first()))
        }
    }
}
