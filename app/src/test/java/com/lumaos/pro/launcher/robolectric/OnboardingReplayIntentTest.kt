package com.lumaos.pro.launcher.robolectric

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.onboarding.OnboardingActivity
import com.lumaos.pro.launcher.onboarding.OnboardingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two entry points into the walkthrough:
 *
 *  - the launcher starts it with no extras (first run only, gated in
 *    `HomeActivity.maybeLaunchOnboarding`);
 *  - Settings → Getting started starts it with [OnboardingActivity.EXTRA_MODE]
 *    set to [OnboardingMode.REPLAY].
 *
 * The intent is the only thing that distinguishes a replay, and it never
 * carries a "reset" instruction — replay walks the same screens over the
 * user's current preferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingReplayIntentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `replay intent targets onboarding and carries the replay mode`() {
        val intent = OnboardingActivity.replayIntent(context)
        assertEquals(OnboardingActivity::class.java.name, intent.component?.className)
        assertEquals(OnboardingMode.REPLAY.name, intent.getStringExtra(OnboardingActivity.EXTRA_MODE))
        assertEquals(OnboardingMode.REPLAY, OnboardingMode.fromExtra(intent.getStringExtra(OnboardingActivity.EXTRA_MODE)))
    }

    @Test
    fun `a launcher-started intent has no mode and reads as first run`() {
        val intent = Intent(context, OnboardingActivity::class.java)
        assertNull(intent.getStringExtra(OnboardingActivity.EXTRA_MODE))
        val mode = OnboardingMode.fromExtra(intent.getStringExtra(OnboardingActivity.EXTRA_MODE))
            ?: OnboardingMode.FIRST_RUN
        assertEquals(OnboardingMode.FIRST_RUN, mode)
    }
}
