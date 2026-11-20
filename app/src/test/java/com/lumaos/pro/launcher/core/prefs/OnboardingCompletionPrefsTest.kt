package com.lumaos.pro.launcher.core.prefs

import com.lumaos.pro.launcher.context.FocusAccessConsent
import com.lumaos.pro.launcher.context.FocusAccessConsentState
import com.lumaos.pro.launcher.core.util.OnboardingEligibility
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Completion persistence for the walkthrough, against a real DataStore file.
 *
 * These are the "relaunch after completion" and "replay does not reset
 * preferences" cases at the storage layer: a fresh [LumaPrefs] over the same
 * file (what process death produces) reads the same completion flag, and writing
 * that flag touches nothing else — not the experience profile, not the recorded
 * accessibility consent.
 *
 * Completion deliberately reuses the pre-existing `onboardingDone` key rather
 * than adding a second flag, so there is one durable answer to "has this
 * install seen the walkthrough?".
 */
class OnboardingCompletionPrefsTest {

    @Test
    fun `a fresh install has not completed the walkthrough`() = withDir { dir ->
        withStore(dir) { prefs ->
            assertFalse(prefs.isOnboardingDoneOnce())
            assertTrue(prefs.onboardingDone.first().not())
            // Therefore a first launch that is not the home app qualifies.
            assertTrue(
                OnboardingEligibility.shouldOfferTour(
                    onboardingDone = prefs.isOnboardingDoneOnce(),
                    isDefaultHome = false,
                    isUpgrade = false
                )
            )
        }
    }

    @Test
    fun `completion survives relaunch`() = withDir { dir ->
        withStore(dir) { it.setOnboardingDone(true) }

        // Process death: a new DataStore instance over the same file.
        withStore(dir) { relaunched ->
            assertTrue(relaunched.isOnboardingDoneOnce())
            assertFalse(
                OnboardingEligibility.shouldOfferTour(
                    onboardingDone = relaunched.isOnboardingDoneOnce(),
                    isDefaultHome = false,
                    isUpgrade = false
                )
            )
        }
    }

    @Test
    fun `writing completion does not reset the experience profile`() = withDir { dir ->
        withStore(dir) { prefs ->
            prefs.setExperienceProfile(ExperienceProfile.MIDNIGHT)
            prefs.setOnboardingDone(true)
            assertEquals(ExperienceProfile.MIDNIGHT, prefs.experienceProfile.first())
        }

        withStore(dir) { relaunched ->
            assertEquals(ExperienceProfile.MIDNIGHT, relaunched.experienceProfile.first())
            assertTrue(relaunched.isOnboardingDoneOnce())
        }
    }

    @Test
    fun `completion and a replay leave recorded accessibility consent alone`() = withDir { dir ->
        withStore(dir) { prefs ->
            prefs.setFocusAccessConsent(FocusAccessConsent.agree(1_700_000_000_000L))
            prefs.setOnboardingDone(true)
        }

        withStore(dir) { relaunched ->
            val consent = relaunched.focusAccessConsent.first()
            assertEquals(FocusAccessConsentState.GRANTED, consent.state)
            assertTrue(FocusAccessConsent.grantsAccess(consent))
        }
    }
}
