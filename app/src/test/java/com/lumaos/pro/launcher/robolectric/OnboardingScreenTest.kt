package com.lumaos.pro.launcher.robolectric

import android.app.Application
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.context.FocusAccessConsent
import com.lumaos.pro.launcher.context.FocusAccessUiState
import com.lumaos.pro.launcher.core.prefs.GestureKind
import com.lumaos.pro.launcher.onboarding.AccessibilityStep
import com.lumaos.pro.launcher.onboarding.CompleteStep
import com.lumaos.pro.launcher.onboarding.FocusProtectionStep
import com.lumaos.pro.launcher.onboarding.GestureBinding
import com.lumaos.pro.launcher.onboarding.GesturesStep
import com.lumaos.pro.launcher.onboarding.HomeRoleStep
import com.lumaos.pro.launcher.onboarding.OnboardingFlow
import com.lumaos.pro.launcher.onboarding.OnboardingMode
import com.lumaos.pro.launcher.onboarding.OnboardingShell
import com.lumaos.pro.launcher.onboarding.OnboardingState
import com.lumaos.pro.launcher.onboarding.OnboardingStep
import com.lumaos.pro.launcher.onboarding.PersonalizeStep
import com.lumaos.pro.launcher.onboarding.WelcomeStep
import com.lumaos.pro.launcher.settings.LumaOSTheme
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real Compose measurement of the seven onboarding screens.
 *
 * Each step is rendered through the same [OnboardingShell] the activity uses,
 * with the same theme, so these tests see what a user sees: the progress
 * indicator, the action labels, and — for the accessibility step — the same
 * disclosure copy and the same two explicit choices as the standalone screen.
 *
 * The activity owns persistence and cross-activity navigation, so that part is
 * covered by [com.lumaos.pro.launcher.onboarding.OnboardingFlowTest] and
 * [com.lumaos.pro.launcher.core.prefs.OnboardingCompletionPrefsTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context.getString(id)

    private fun theme(): ResolvedTheme = ThemeEngine.resolve(
        ThemeSettings(),
        ThemeEnvironment(systemDark = true, minuteOfDay = 600)
    )

    private fun setStep(
        step: OnboardingStep,
        mode: OnboardingMode = OnboardingMode.FIRST_RUN,
        content: @Composable ColumnScope.() -> Unit
    ) {
        val resolved = theme()
        compose.setContent {
            LumaOSTheme(resolved, 1f) {
                OnboardingShell(
                    theme = resolved,
                    state = OnboardingState(step, mode),
                    onBack = {}
                ) {
                    content()
                }
            }
        }
    }

    @Test
    fun `welcome shows the product, its value points and a way out of the tour`() {
        var started = false
        var skipped = false
        setStep(OnboardingStep.WELCOME) {
            WelcomeStep(
                theme = theme(),
                mode = OnboardingMode.FIRST_RUN,
                onStart = { started = true },
                onSkipTour = { skipped = true },
                onClose = {}
            )
        }

        compose.onNodeWithContentDescription(context.getString(R.string.onboarding_step_progress, 1, 7))
            .assertExists()
        compose.onNodeWithText(text(R.string.onboarding_welcome)).assertExists()
        compose.onNodeWithText(text(R.string.onboarding_focus_point_short)).assertExists()

        compose.onNodeWithText(text(R.string.onboarding_start)).performScrollTo().performClick()
        assertTrue("Get started must advance the flow", started)

        compose.onNodeWithText(text(R.string.onboarding_skip_tour)).performScrollTo().performClick()
        assertTrue("The tour must be skippable", skipped)
    }

    @Test
    fun `gestures prints the live bindings and offers continue and skip`() {
        var continued = false
        var skipped = false
        val bindings = GestureKind.entries.map { GestureBinding(it, it.default) }
        setStep(OnboardingStep.GESTURES) {
            GesturesStep(
                theme = theme(),
                bindings = bindings,
                onContinue = { continued = true },
                onSkipTour = { skipped = true }
            )
        }

        compose.onNodeWithContentDescription("Step 2 of 7").assertExists()
        compose.onNodeWithText(text(R.string.gesture_swipe_up)).assertExists()
        compose.onNodeWithText(text(R.string.gesture_open_drawer)).assertExists()
        compose.onNodeWithText(text(R.string.gesture_swipe_down)).assertExists()
        compose.onNodeWithText(text(R.string.gesture_open_search)).assertExists()

        compose.onNodeWithText(text(R.string.onboarding_continue)).performScrollTo().performClick()
        assertTrue(continued)
        compose.onNodeWithText(text(R.string.onboarding_skip_tour)).performScrollTo().performClick()
        assertTrue(skipped)
    }

    @Test
    fun `personalize offers the real profiles and a route into Theme Studio`() {
        var selected: ExperienceProfile? = null
        var studio = false
        setStep(OnboardingStep.PERSONALIZE) {
            PersonalizeStep(
                theme = theme(),
                selected = ExperienceProfile.LIQUID,
                livingIcons = emptyList(),
                onSelect = { selected = it },
                onOpenStudio = { studio = true },
                onContinue = {},
                onSkipTour = {}
            )
        }

        compose.onNodeWithText(text(R.string.experience_pure)).assertExists()
        compose.onNodeWithText(text(R.string.experience_liquid)).assertExists()
        compose.onNodeWithText(text(R.string.experience_midnight)).assertExists()

        compose.onNode(hasText(text(R.string.experience_midnight)) and hasClickAction())
            .performScrollTo()
            .performClick()
        assertEquals(ExperienceProfile.MIDNIGHT, selected)

        compose.onNodeWithText(text(R.string.onboarding_open_studio)).performScrollTo().performClick()
        assertTrue("Theme Studio must be reachable", studio)
    }

    @Test
    fun `focus protection explains both layers without asking for anything`() {
        var continued = false
        setStep(OnboardingStep.FOCUS_PROTECTION) {
            FocusProtectionStep(theme = theme(), onContinue = { continued = true }, onSkipTour = {})
        }

        compose.onNodeWithText(text(R.string.onboarding_focus_layer1_title)).assertExists()
        compose.onNodeWithText(text(R.string.onboarding_focus_layer2_title)).assertExists()
        compose.onNodeWithText(text(R.string.onboarding_focus_optional_body)).assertExists()
        compose.onNodeWithText(text(R.string.onboarding_continue)).performScrollTo().performClick()
        assertTrue(continued)
    }

    @Test
    fun `the accessibility step shows the disclosure with Agree and Decline only`() {
        var agreed = false
        var declined = false
        var skipped = false
        setStep(OnboardingStep.ACCESSIBILITY) {
            AccessibilityStep(
                theme = theme(),
                state = FocusAccessUiState.NEEDS_DISCLOSURE,
                permissionGranted = false,
                decidedAtMillis = 0L,
                onAgree = { agreed = true },
                onDecline = { declined = true },
                onManage = {},
                onWithdraw = {},
                onContinue = {},
                onSkipForNow = { skipped = true }
            )
        }

        compose.onNodeWithText(text(R.string.onboarding_accessibility_title)).assertExists()
        compose.onNodeWithText(text(R.string.focus_access_agree)).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.focus_access_decline)).performScrollTo().assertExists()
        // Nothing is pre-decided: Continue is not offered before an answer.
        compose.onNodeWithText(text(R.string.onboarding_continue)).assertDoesNotExist()

        compose.onNodeWithText(text(R.string.onboarding_skip_for_now)).performScrollTo().performClick()
        assertTrue(skipped)

        compose.onNodeWithText(text(R.string.focus_access_agree)).performScrollTo().performClick()
        assertTrue(agreed)
        compose.onNodeWithText(text(R.string.focus_access_decline)).performScrollTo().performClick()
        assertTrue(declined)
    }

    @Test
    fun `a consented accessibility step offers manage, withdraw and continue`() {
        setStep(OnboardingStep.ACCESSIBILITY) {
            AccessibilityStep(
                theme = theme(),
                state = FocusAccessUiState.AWAITING_PERMISSION,
                permissionGranted = false,
                decidedAtMillis = 1_700_000_000_000L,
                onAgree = {},
                onDecline = {},
                onManage = {},
                onWithdraw = {},
                onContinue = {},
                onSkipForNow = {}
            )
        }

        compose.onNodeWithText(text(R.string.focus_access_manage)).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.focus_access_withdraw)).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.onboarding_continue)).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.focus_access_agree)).assertDoesNotExist()
    }

    @Test
    fun `home role step asks for the role when LumaOS Pro is not the home app`() {
        var requested = false
        var continued = false
        setStep(OnboardingStep.HOME_ROLE) {
            HomeRoleStep(
                theme = theme(),
                isDefaultHome = false,
                onSetDefault = { requested = true },
                onContinue = { continued = true }
            )
        }

        compose.onNodeWithText(text(R.string.onboarding_home_status_pending)).assertExists()
        compose.onNodeWithText(text(R.string.onboarding_set_default)).performScrollTo().performClick()
        assertTrue(requested)
        compose.onNodeWithText(text(R.string.onboarding_skip)).performScrollTo().performClick()
        assertTrue(continued)
    }

    @Test
    fun `home role step simply continues when the role is already held`() {
        var continued = false
        setStep(OnboardingStep.HOME_ROLE) {
            HomeRoleStep(
                theme = theme(),
                isDefaultHome = true,
                onSetDefault = {},
                onContinue = { continued = true }
            )
        }

        compose.onNodeWithText(text(R.string.role_status_default)).assertExists()
        compose.onNodeWithText(text(R.string.onboarding_set_default)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.onboarding_continue)).performScrollTo().performClick()
        assertTrue(continued)
    }

    @Test
    fun `the final step enters the launcher and points back at the tour`() {
        var finished = false
        setStep(OnboardingStep.COMPLETE) {
            CompleteStep(theme = theme(), isDefaultHome = true, onFinish = { finished = true })
        }

        compose.onNodeWithContentDescription("Step 7 of 7").assertExists()
        compose.onNodeWithText(text(R.string.onboarding_complete_title)).assertExists()
        compose.onNodeWithText(text(R.string.onboarding_complete_replay_hint)).assertExists()
        compose.onNodeWithText(text(R.string.onboarding_enter_home)).performScrollTo().performClick()
        assertTrue(finished)
    }

    @Test
    fun `a replay opens on the same first step and says nothing is reset`() {
        setStep(OnboardingStep.WELCOME, mode = OnboardingMode.REPLAY) {
            WelcomeStep(
                theme = theme(),
                mode = OnboardingMode.REPLAY,
                onStart = {},
                onSkipTour = {},
                onClose = {}
            )
        }

        compose.onNodeWithText(text(R.string.onboarding_replay_note)).performScrollTo().assertExists()
        compose.onNodeWithText(text(R.string.onboarding_close)).assertExists()
        // The first-run skip wording is replaced by Close in replay mode.
        compose.onNodeWithText(text(R.string.onboarding_skip_tour)).assertDoesNotExist()
    }
}
