package com.lumaos.pro.launcher.core.util

/**
 * Whether a launch should be offered the first-run walkthrough.
 *
 * Onboarding belongs to a *first installation*. An existing installation that
 * receives an update has already been used, has preferences worth keeping, and
 * must not suddenly be walked through setup again — the Settings re-entry point
 * ("Getting started") exists precisely so it stays opt-in for those users.
 *
 * The install/update timestamps come from [android.content.pm.PackageInfo].
 * They are equal for a fresh package, and the update timestamp moves on when
 * the package is replaced. [UPGRADE_SLACK_MILLIS] absorbs the small skew some
 * installers report between the two values on a genuine first install.
 *
 * Pure on purpose: every branch is unit tested without Android or DataStore.
 */
object OnboardingEligibility {

    /** Skew tolerated between install and update timestamps on a fresh install. */
    const val UPGRADE_SLACK_MILLIS: Long = 60_000L

    /**
     * True when the package has been replaced since it was installed. Unknown
     * timestamps (0) are never treated as an upgrade: refusing to show the tour
     * on a device that cannot report its timestamps would be worse than showing
     * it once.
     */
    fun isUpgrade(firstInstallTime: Long, lastUpdateTime: Long): Boolean =
        firstInstallTime > 0L && lastUpdateTime - firstInstallTime > UPGRADE_SLACK_MILLIS

    /** The full launch gate, combining the durable flag, the role and the install age. */
    fun shouldOfferTour(
        onboardingDone: Boolean,
        isDefaultHome: Boolean,
        isUpgrade: Boolean
    ): Boolean = !onboardingDone && !isDefaultHome && !isUpgrade

    /**
     * When the tour must never be shown, the durable flag still needs to become
     * true so later launches do not re-evaluate (and so a role change cannot
     * bring the tour back). Being the default Home app or having upgraded both
     * imply "this install is not new".
     */
    fun shouldMarkDoneImplicitly(
        onboardingDone: Boolean,
        isDefaultHome: Boolean,
        isUpgrade: Boolean
    ): Boolean = !onboardingDone && (isDefaultHome || isUpgrade)
}
