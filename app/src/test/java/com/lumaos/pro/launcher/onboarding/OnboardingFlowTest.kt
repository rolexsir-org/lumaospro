package com.lumaos.pro.launcher.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation rules for the first-run walkthrough. Pure JVM: no Android, no
 * Compose, no DataStore, so the ordering and skip semantics can be verified
 * anywhere.
 *
 * The scenarios map to the requested test matrix: fresh install (the flow
 * starts at Welcome and ends at Complete), skipping optional tutorial steps
 * (every tutorial step can jump to the Home-role question and nothing else),
 * and replay (same steps, different mode).
 */
class OnboardingFlowTest {

    @Test
    fun `the walkthrough has exactly seven steps in the requested order`() {
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.GESTURES,
                OnboardingStep.PERSONALIZE,
                OnboardingStep.FOCUS_PROTECTION,
                OnboardingStep.ACCESSIBILITY,
                OnboardingStep.HOME_ROLE,
                OnboardingStep.COMPLETE
            ),
            OnboardingFlow.steps
        )
        assertEquals(7, OnboardingFlow.totalSteps)
    }

    @Test
    fun `a fresh install starts on the welcome step`() {
        val state = OnboardingFlow.initial(OnboardingMode.FIRST_RUN)
        assertEquals(OnboardingStep.WELCOME, state.step)
        assertEquals(OnboardingMode.FIRST_RUN, state.mode)
        assertTrue(OnboardingFlow.isFirstStep(state.step))
        assertFalse(OnboardingFlow.isLastStep(state.step))
    }

    @Test
    fun `next visits every step once and then stops`() {
        var state = OnboardingFlow.initial(OnboardingMode.FIRST_RUN)
        val visited = mutableListOf(state.step)
        while (true) {
            val next = OnboardingFlow.next(state) ?: break
            state = next
            visited += state.step
        }
        assertEquals(OnboardingFlow.steps, visited)
        assertTrue(OnboardingFlow.isLastStep(state.step))
        // Complete is terminal: the caller finishes instead of navigating on.
        assertNull(OnboardingFlow.next(state))
    }

    @Test
    fun `back walks the same steps in reverse and stops at the start`() {
        var state = OnboardingState(OnboardingStep.COMPLETE, OnboardingMode.FIRST_RUN)
        val visited = mutableListOf(state.step)
        while (true) {
            val previous = OnboardingFlow.previous(state) ?: break
            state = previous
            visited += state.step
        }
        assertEquals(OnboardingFlow.steps.reversed(), visited)
        assertNull(OnboardingFlow.previous(state))
    }

    @Test
    fun `only the tutorial steps between welcome and the home role are optional`() {
        assertEquals(
            listOf(
                OnboardingStep.GESTURES,
                OnboardingStep.PERSONALIZE,
                OnboardingStep.FOCUS_PROTECTION,
                OnboardingStep.ACCESSIBILITY
            ),
            OnboardingFlow.steps.filter { it.optionalTutorial }
        )
        OnboardingFlow.steps.forEach { step ->
            assertEquals(step.optionalTutorial, OnboardingFlow.canSkipTour(step))
        }
    }

    @Test
    fun `skipping the tour leaves every tutorial step for the home role question`() {
        val tutorial = OnboardingFlow.steps.filter { it.optionalTutorial }
        assertTrue(tutorial.isNotEmpty())
        tutorial.forEach { step ->
            val skipped = OnboardingFlow.skipTour(OnboardingState(step, OnboardingMode.FIRST_RUN))
            assertEquals(
                "skipping from $step must still ask about the Home role",
                OnboardingStep.HOME_ROLE,
                skipped.step
            )
            assertEquals(OnboardingMode.FIRST_RUN, skipped.mode)
        }
    }

    @Test
    fun `skipping outside the tutorial changes nothing`() {
        listOf(OnboardingStep.WELCOME, OnboardingStep.HOME_ROLE, OnboardingStep.COMPLETE).forEach { step ->
            val state = OnboardingState(step, OnboardingMode.FIRST_RUN)
            assertEquals(state, OnboardingFlow.skipTour(state))
        }
    }

    @Test
    fun `declining the optional accessibility step continues to the home role step`() {
        // The activity calls next() after persisting a decline; the optional
        // feature must not block the required default-launcher decision.
        val afterAccessibility = OnboardingFlow.next(
            OnboardingState(OnboardingStep.ACCESSIBILITY, OnboardingMode.FIRST_RUN)
        )
        assertEquals(OnboardingStep.HOME_ROLE, afterAccessibility?.step)
    }

    @Test
    fun `step numbers are one based and cover the whole flow`() {
        assertEquals(1, OnboardingFlow.stepNumber(OnboardingStep.WELCOME))
        assertEquals(5, OnboardingFlow.stepNumber(OnboardingStep.ACCESSIBILITY))
        assertEquals(7, OnboardingFlow.stepNumber(OnboardingStep.COMPLETE))
    }

    @Test
    fun `replay uses the same steps and is a distinct mode`() {
        val replay = OnboardingFlow.initial(OnboardingMode.REPLAY)
        assertEquals(OnboardingMode.REPLAY, replay.mode)
        assertEquals(OnboardingFlow.steps, listOf(replay.step) + generateSequence(replay) { OnboardingFlow.next(it) }
            .drop(1)
            .map { it.step }
            .toList())
        assertEquals(
            OnboardingStep.HOME_ROLE,
            OnboardingFlow.skipTour(OnboardingState(OnboardingStep.PERSONALIZE, OnboardingMode.REPLAY)).step
        )
    }

    @Test
    fun `mode parsing accepts the two known values and ignores anything else`() {
        assertEquals(OnboardingMode.FIRST_RUN, OnboardingMode.fromExtra("FIRST_RUN"))
        assertEquals(OnboardingMode.FIRST_RUN, OnboardingMode.fromExtra("first_run"))
        assertEquals(OnboardingMode.REPLAY, OnboardingMode.fromExtra("REPLAY"))
        assertEquals(OnboardingMode.REPLAY, OnboardingMode.fromExtra("Replay"))
        assertNull(OnboardingMode.fromExtra(null))
        assertNull(OnboardingMode.fromExtra(""))
        assertNull(OnboardingMode.fromExtra("SOMETHING_ELSE"))
    }

    @Test
    fun `an absent mode extra falls back to first run`() {
        // This is exactly what the activity does with the intent extra.
        val mode = OnboardingMode.fromExtra(null) ?: OnboardingMode.FIRST_RUN
        assertEquals(OnboardingMode.FIRST_RUN, mode)
    }
}
