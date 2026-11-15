package com.lumaos.pro.launcher.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launch gate for the first-run walkthrough.
 *
 * Covers the requested fresh-install, relaunch-after-completion and
 * upgrade-from-an-existing-install cases without Android: the launcher only
 * calls these functions with facts read from DataStore and PackageInfo.
 */
class OnboardingEligibilityTest {

    @Test
    fun `a fresh install that is not the home app gets the walkthrough`() {
        assertTrue(
            OnboardingEligibility.shouldOfferTour(
                onboardingDone = false,
                isDefaultHome = false,
                isUpgrade = false
            )
        )
    }

    @Test
    fun `a completed install never sees the walkthrough again`() {
        assertFalse(
            OnboardingEligibility.shouldOfferTour(
                onboardingDone = true,
                isDefaultHome = false,
                isUpgrade = false
            )
        )
        // Also while it happens to be the home app.
        assertFalse(
            OnboardingEligibility.shouldOfferTour(
                onboardingDone = true,
                isDefaultHome = true,
                isUpgrade = false
            )
        )
    }

    @Test
    fun `an install that is already the home app has nothing to onboard`() {
        assertFalse(
            OnboardingEligibility.shouldOfferTour(
                onboardingDone = false,
                isDefaultHome = true,
                isUpgrade = false
            )
        )
    }

    @Test
    fun `an updated existing install is not walked through setup again`() {
        assertFalse(
            OnboardingEligibility.shouldOfferTour(
                onboardingDone = false,
                isDefaultHome = false,
                isUpgrade = true
            )
        )
        // The flag is still recorded, so a later Home-role change cannot bring
        // onboarding back for this install.
        assertTrue(
            OnboardingEligibility.shouldMarkDoneImplicitly(
                onboardingDone = false,
                isDefaultHome = false,
                isUpgrade = true
            )
        )
    }

    @Test
    fun `an install that is the home app records completion without a tour`() {
        assertTrue(
            OnboardingEligibility.shouldMarkDoneImplicitly(
                onboardingDone = false,
                isDefaultHome = true,
                isUpgrade = false
            )
        )
        assertFalse(
            OnboardingEligibility.shouldMarkDoneImplicitly(
                onboardingDone = true,
                isDefaultHome = true,
                isUpgrade = true
            )
        )
    }

    @Test
    fun `a fresh install is not recognised as an upgrade`() {
        assertFalse(OnboardingEligibility.isUpgrade(firstInstallTime = 1_000L, lastUpdateTime = 1_000L))
        assertFalse(OnboardingEligibility.isUpgrade(firstInstallTime = 1_000L, lastUpdateTime = 61_000L))
        assertFalse(OnboardingEligibility.isUpgrade(firstInstallTime = 0L, lastUpdateTime = 0L))
    }

    @Test
    fun `an install replaced after the slack window is an upgrade`() {
        assertTrue(OnboardingEligibility.isUpgrade(firstInstallTime = 1_000L, lastUpdateTime = 61_001L))
        assertTrue(OnboardingEligibility.isUpgrade(firstInstallTime = 1_000L, lastUpdateTime = 9_000_000L))
    }

    @Test
    fun `unknown timestamps are never treated as an upgrade`() {
        // 0 means the platform did not tell us. Showing the tour once is a
        // better outcome than never showing it on such a device.
        assertFalse(OnboardingEligibility.isUpgrade(firstInstallTime = 0L, lastUpdateTime = 9_000_000L))
        assertFalse(OnboardingEligibility.isUpgrade(firstInstallTime = -1L, lastUpdateTime = 9_000_000L))
    }
}
