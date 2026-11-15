package com.lumaos.pro.launcher.onboarding

/**
 * The ordered first-run experience. Navigation lives here (not in the activity)
 * so it can be unit-tested without Android, and so the step list has exactly one
 * definition shared by the UI, the progress indicator and the "skip the tour"
 * decision.
 *
 * The screen order follows the requested flow:
 *
 *  1. WELCOME          — what LumaOS Pro is, and how to leave the tour.
 *  2. GESTURES         — home-screen navigation, printing the user's own bindings.
 *  3. PERSONALIZE      — experience profiles plus a real route into Theme Studio.
 *  4. FOCUS_PROTECTION — what the optional blocking layer does, without asking
 *                        for anything yet.
 *  5. ACCESSIBILITY    — the in-app disclosure and explicit consent. Still no
 *                        system permission unless the user presses Agree.
 *  6. HOME_ROLE        — default-launcher setup, the one thing the launcher needs.
 *  7. COMPLETE         — enters the launcher.
 *
 * Steps 2–5 are the optional tutorial: they teach optional features. Steps 1, 6
 * and 7 are required so the user always meets the Home-role decision and always
 * finishes with an explicit "enter" action. Skipping the tour therefore jumps to
 * [OnboardingStep.HOME_ROLE] — it never skips launcher setup itself.
 */
enum class OnboardingStep(val optionalTutorial: Boolean) {
    WELCOME(optionalTutorial = false),
    GESTURES(optionalTutorial = true),
    PERSONALIZE(optionalTutorial = true),
    FOCUS_PROTECTION(optionalTutorial = true),
    ACCESSIBILITY(optionalTutorial = true),
    HOME_ROLE(optionalTutorial = false),
    COMPLETE(optionalTutorial = false)
}

/** First installation, or a replay requested from Settings → Getting started. */
enum class OnboardingMode {
    FIRST_RUN,
    REPLAY;

    companion object {
        /**
         * Parses the intent extra. Returns `null` for absent or unknown values;
         * the activity then falls back to [FIRST_RUN], the only mode the
         * launcher starts by itself.
         */
        fun fromExtra(raw: String?): OnboardingMode? = when {
            raw.equals(FIRST_RUN.name, ignoreCase = true) -> FIRST_RUN
            raw.equals(REPLAY.name, ignoreCase = true) -> REPLAY
            else -> null
        }
    }
}

data class OnboardingState(
    val step: OnboardingStep,
    val mode: OnboardingMode
)

object OnboardingFlow {

    val steps: List<OnboardingStep> = OnboardingStep.entries.toList()

    val totalSteps: Int get() = steps.size

    fun initial(mode: OnboardingMode): OnboardingState = OnboardingState(OnboardingStep.WELCOME, mode)

    /** 1-based position, for "Step 3 of 7". */
    fun stepNumber(step: OnboardingStep): Int = steps.indexOf(step) + 1

    fun isFirstStep(step: OnboardingStep): Boolean = step == steps.first()

    fun isLastStep(step: OnboardingStep): Boolean = step == steps.last()

    /** Forward navigation; `null` on the final step (the caller completes instead). */
    fun next(state: OnboardingState): OnboardingState? {
        val index = steps.indexOf(state.step)
        if (index < 0 || index >= steps.lastIndex) return null
        return state.copy(step = steps[index + 1])
    }

    /** Back navigation; `null` on the first step (the caller leaves onboarding). */
    fun previous(state: OnboardingState): OnboardingState? {
        val index = steps.indexOf(state.step)
        if (index <= 0) return null
        return state.copy(step = steps[index - 1])
    }

    /**
     * "Skip the rest of the tour". Everything before the Home-role question can
     * be left — the tutorial steps, and the welcome screen itself. Skipping goes
     * to the Home-role step rather than out of onboarding, so the user still
     * chooses whether LumaOS Pro becomes their home app.
     *
     * At or after the Home role there is no tour left to skip, so the state is
     * returned unchanged and the caller's navigation is a no-op.
     */
    fun skipTour(state: OnboardingState): OnboardingState =
        if (steps.indexOf(state.step) < steps.indexOf(OnboardingStep.HOME_ROLE)) {
            state.copy(step = OnboardingStep.HOME_ROLE)
        } else {
            state
        }

    fun canSkipTour(step: OnboardingStep): Boolean =
        steps.indexOf(step) < steps.indexOf(OnboardingStep.HOME_ROLE)
}
