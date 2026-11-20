package com.lumaos.pro.launcher.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.context.FocusAccess
import com.lumaos.pro.launcher.context.FocusAccessConsent
import com.lumaos.pro.launcher.context.FocusAccessConsentFlow
import com.lumaos.pro.launcher.context.FocusAccessConsentRecord
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.prefs.GestureKind
import com.lumaos.pro.launcher.core.prefs.PrefsFocusAccessConsentStore
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.core.util.RoleHelper
import com.lumaos.pro.launcher.icons.LivingIconId
import com.lumaos.pro.launcher.settings.LumaOSTheme
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First-run walkthrough and the replayable "Getting started" tour.
 *
 * Seven steps, defined once in [OnboardingFlow]: welcome, gestures,
 * personalisation, Focus Protection, the accessibility disclosure, the Home
 * role, and a completion screen. Steps 2–5 are optional and can be skipped;
 * the Home role is always offered, and the last screen always asks the user to
 * enter the launcher.
 *
 * What this activity deliberately does *not* do:
 *
 *  - It never requests a runtime permission or an accessibility grant by
 *    itself. The accessibility step renders the same disclosure as Settings and
 *    only reaches Android's accessibility screen after an explicit Agree that
 *    has been written to DataStore.
 *  - It never resets anything. Playing a profile writes appearance preferences
 *    through the same `applyExperienceProfile` call Settings uses; layouts,
 *    widgets, focus sessions and consent are untouched. A replay starts from
 *    the user's current settings, not from defaults.
 *  - It never invents data. The gesture list is read from the live bindings,
 *    the Living Icons come from this install's icon renderer, and the Home-role
 *    state is re-read from Android on every resume.
 *
 * Completion is the existing durable fact, [com.lumaos.pro.launcher.core.prefs.LumaPrefs.onboardingDone]:
 * reaching the final step, pressing back out of the first step, choosing a Home
 * role, or finishing all persist the same flag. There is no second onboarding
 * flag.
 */
class OnboardingActivity : ComponentActivity() {

    private val prefs by lazy { (application as LumaApp).core.prefs }
    private val consentFlow by lazy { FocusAccessConsentFlow(PrefsFocusAccessConsentStore(prefs)) }

    private val mode: OnboardingMode by lazy {
        OnboardingMode.fromExtra(intent?.getStringExtra(EXTRA_MODE)) ?: OnboardingMode.FIRST_RUN
    }

    /** Current step; an activity field (not rememberSaveable) so callbacks that
     *  arrive outside composition — role result, consent result — can navigate. */
    private var step by mutableStateOf(OnboardingStep.WELCOME)

    private var consent by mutableStateOf<FocusAccessConsentRecord>(FocusAccessConsent.NOT_ASKED)
    private var permissionGranted by mutableStateOf(false)
    private var isDefaultHome by mutableStateOf(false)

    private var roleRequestInFlight = false
    private var decisionInFlight = false

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        roleRequestInFlight = false
        // Never infer success from resultCode. Android's HOME selection is a
        // separate fact; re-read it and let the step re-render.
        refreshRole()
        LumaLog.i("Home-role request returned; LumaOS Pro default=$isDefaultHome")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        step = savedInstanceState?.getString(KEY_STEP)
            ?.let { name -> OnboardingStep.entries.firstOrNull { it.name == name } }
            ?: OnboardingStep.WELCOME
        refreshRole()
        refreshPermission()
        refreshConsent()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                back()
            }
        })

        val core = (application as LumaApp).core
        val themes = core.themes
        setContent {
            val theme by themes.theme.collectAsState()
            val textScale by prefs.textScale.collectAsState(1.0f)
            val selectedProfile by prefs.experienceProfile.collectAsState(ExperienceProfile.DEFAULT)
            // Live bindings, not a legend: the tour shows what this device will
            // actually do. collectAsState in a fixed-size loop is stable.
            val bindings = GestureKind.entries.map { kind ->
                val action by prefs.gesture(kind).collectAsState(kind.default)
                GestureBinding(kind, action)
            }
            val livingIcons by produceState<List<Pair<String, ImageBitmap>>>(emptyList()) {
                value = withContext(Dispatchers.IO) {
                    listOf(
                        LivingIconId.PHONE,
                        LivingIconId.MESSAGES,
                        LivingIconId.CAMERA,
                        LivingIconId.PHOTOS,
                        LivingIconId.SETTINGS
                    ).mapNotNull { id ->
                        core.livingIcons.bitmapForId(id, 144)?.let { id.displayName to it.asImageBitmap() }
                    }
                }
            }
            LumaOSTheme(theme = theme, textScale = textScale) {
                // A restored or previously customised appearance always wins;
                // the guard and default write are one DataStore transaction.
                LaunchedEffect(Unit) {
                    safelyUpdateAppearance { prefs.initializeDefaultExperienceIfUnconfigured() }
                }
                DisposableEffect(theme.isDark) {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !theme.isDark
                        isAppearanceLightNavigationBars = !theme.isDark
                    }
                    onDispose { }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state = OnboardingState(step, mode)
                    OnboardingShell(
                        theme = theme,
                        state = state,
                        onBack = ::back
                    ) {
                        when (step) {
                            OnboardingStep.WELCOME -> WelcomeStep(
                                theme = theme,
                                mode = mode,
                                onStart = { goTo(OnboardingFlow.next(state)?.step) },
                                onSkipTour = { goTo(OnboardingFlow.skipTour(state).step) },
                                onClose = ::completeOnboardingAndFinish
                            )
                            OnboardingStep.GESTURES -> GesturesStep(
                                theme = theme,
                                bindings = bindings,
                                onContinue = { goTo(OnboardingFlow.next(state)?.step) },
                                onSkipTour = { goTo(OnboardingFlow.skipTour(state).step) }
                            )
                            OnboardingStep.PERSONALIZE -> PersonalizeStep(
                                theme = theme,
                                selected = selectedProfile,
                                livingIcons = livingIcons,
                                onSelect = { profile ->
                                    lifecycleScope.launch {
                                        safelyUpdateAppearance { prefs.applyExperienceProfile(profile) }
                                    }
                                },
                                onOpenStudio = ::openThemeStudio,
                                onContinue = { goTo(OnboardingFlow.next(state)?.step) },
                                onSkipTour = { goTo(OnboardingFlow.skipTour(state).step) }
                            )
                            OnboardingStep.FOCUS_PROTECTION -> FocusProtectionStep(
                                theme = theme,
                                onContinue = { goTo(OnboardingFlow.next(state)?.step) },
                                onSkipTour = { goTo(OnboardingFlow.skipTour(state).step) }
                            )
                            OnboardingStep.ACCESSIBILITY -> AccessibilityStep(
                                theme = theme,
                                state = FocusAccessConsent.uiState(consent, permissionGranted),
                                permissionGranted = permissionGranted,
                                decidedAtMillis = consent.decidedAtMillis,
                                onAgree = ::agree,
                                onDecline = ::decline,
                                onManage = ::openAccessibilitySettings,
                                onWithdraw = ::withdraw,
                                onContinue = { goTo(OnboardingFlow.next(state)?.step) },
                                onSkipForNow = { goTo(OnboardingFlow.next(state)?.step) }
                            )
                            OnboardingStep.HOME_ROLE -> HomeRoleStep(
                                theme = theme,
                                isDefaultHome = isDefaultHome,
                                onSetDefault = ::requestHomeRole,
                                onContinue = { goTo(OnboardingFlow.next(state)?.step) }
                            )
                            OnboardingStep.COMPLETE -> CompleteStep(
                                theme = theme,
                                isDefaultHome = isDefaultHome,
                                onFinish = ::completeOnboardingAndFinish
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_STEP, step.name)
    }

    override fun onResume() {
        super.onResume()
        // Coming back from Android settings (Home role or accessibility) emits
        // nothing, so every fact this flow displays is re-read here.
        refreshRole()
        refreshPermission()
        refreshConsent()
    }

    // ------------------------------------------------------------------ state

    private fun refreshRole() {
        isDefaultHome = RoleHelper.isDefaultLauncher(this)
    }

    private fun refreshPermission() {
        permissionGranted = FocusAccess.isGranted(this)
    }

    private fun refreshConsent() {
        lifecycleScope.launch {
            consent = runCatching { consentFlow.read() }
                .getOrElse {
                    LumaLog.e("Could not read Focus access consent", it)
                    FocusAccessConsent.NOT_ASKED
                }
        }
    }

    // ------------------------------------------------------------- navigation

    private fun goTo(target: OnboardingStep?) {
        if (target == null || target == step) return
        step = target
        if (target == OnboardingStep.COMPLETE) {
            // Reaching the summary counts as finishing the walkthrough; the
            // user may sit on it, leave, or press Open. Persist now so a
            // later launch cannot re-run the flow.
            persistCompletionSilently()
        }
    }

    private fun back() {
        val previous = OnboardingFlow.previous(OnboardingState(step, mode))
        if (previous != null) {
            step = previous.step
        } else {
            // Leaving from the first step is a supported outcome: onboarding is
            // complete (the user has seen it), and the launcher stays usable.
            completeOnboardingAndFinish()
        }
    }

    // --------------------------------------------------------- consent actions

    private fun agree() {
        if (decisionInFlight || isFinishing) return
        decisionInFlight = true
        lifecycleScope.launch {
            val record = runCatching { consentFlow.agree() }.getOrNull()
            decisionInFlight = false
            if (record == null) {
                Toast.makeText(this@OnboardingActivity, R.string.focus_access_save_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            consent = record
            refreshPermission()
            if (permissionGranted) {
                // The grant already exists; consent is now on record and the
                // step re-renders as active. No detour through system settings.
                Toast.makeText(
                    this@OnboardingActivity,
                    R.string.focus_access_consent_recorded,
                    Toast.LENGTH_LONG
                ).show()
            } else {
                openAccessibilitySettings()
            }
        }
    }

    private fun decline() {
        if (decisionInFlight || isFinishing) return
        decisionInFlight = true
        lifecycleScope.launch {
            val record = runCatching { consentFlow.decline() }.getOrNull()
            decisionInFlight = false
            if (record == null) {
                Toast.makeText(this@OnboardingActivity, R.string.focus_access_save_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            consent = record
            Toast.makeText(
                this@OnboardingActivity,
                R.string.focus_access_declined_toast,
                Toast.LENGTH_LONG
            ).show()
            // Declining is a complete answer. Move on to the Home-role step so
            // the optional feature never blocks the required setup.
            goTo(OnboardingFlow.next(OnboardingState(step, mode))?.step)
        }
    }

    private fun withdraw() {
        if (decisionInFlight || isFinishing) return
        decisionInFlight = true
        lifecycleScope.launch {
            val record = runCatching { consentFlow.withdraw() }.getOrNull()
            decisionInFlight = false
            if (record == null) {
                Toast.makeText(this@OnboardingActivity, R.string.focus_access_save_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            consent = record
            Toast.makeText(
                this@OnboardingActivity,
                R.string.focus_access_withdrawn_toast,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** The only route to Android's accessibility screen, and only after Agree. */
    private fun openAccessibilitySettings() = FocusAccess.openSettings(this)

    // ----------------------------------------------------------- home role

    private fun requestHomeRole() {
        if (roleRequestInFlight || isFinishing || isDestroyed) return
        roleRequestInFlight = true
        lifecycleScope.launch {
            // Persist completion before crossing into Android settings: a
            // process death on that screen must not restart onboarding.
            if (!persistOnboardingCompletion()) {
                roleRequestInFlight = false
                return@launch
            }
            refreshRole()
            if (isDefaultHome) {
                roleRequestInFlight = false
                return@launch
            }
            val intent = RoleHelper.homeRoleRequestIntent(this@OnboardingActivity)
            if (intent != null) {
                runCatching { roleLauncher.launch(intent) }
                    .onFailure {
                        roleRequestInFlight = false
                        LumaLog.w("Home role request unavailable; opening Home settings")
                        fallbackToHomeSettings()
                    }
            } else {
                roleRequestInFlight = false
                fallbackToHomeSettings()
            }
        }
    }

    /**
     * Older Android, or an OEM without the Home-role intent. The system page is
     * opened and the tour stays here, so the user can come back and continue.
     */
    private fun fallbackToHomeSettings() {
        RoleHelper.openHomeSettings(this)
    }

    private fun openThemeStudio() {
        // Real navigation to the real editor; the tour is still here on return.
        startActivity(
            Intent(this, com.lumaos.pro.launcher.settings.SettingsActivity::class.java)
                .putExtra(
                    com.lumaos.pro.launcher.settings.SettingsActivity.EXTRA_TAB,
                    com.lumaos.pro.launcher.settings.SettingsActivity.TAB_STUDIO
                )
        )
    }

    // ------------------------------------------------------------ completion

    private fun completeOnboardingAndFinish() {
        lifecycleScope.launch {
            if (persistOnboardingCompletion()) finish()
        }
    }

    private fun persistCompletionSilently() {
        lifecycleScope.launch {
            try {
                prefs.setOnboardingDone(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LumaLog.e("Could not persist onboarding completion", e)
            }
        }
    }

    private suspend fun persistOnboardingCompletion(): Boolean = try {
        prefs.setOnboardingDone(true)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LumaLog.e("Could not persist onboarding completion", e)
        Toast.makeText(this, R.string.onboarding_save_failed, Toast.LENGTH_LONG).show()
        false
    }

    private suspend fun safelyUpdateAppearance(update: suspend () -> Unit) {
        try {
            update()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LumaLog.e("Could not apply experience", e)
            Toast.makeText(this, R.string.appearance_apply_failed, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        /** Optional entry mode; absent means first run. */
        const val EXTRA_MODE = "com.lumaos.pro.launcher.extra.ONBOARDING_MODE"

        private const val KEY_STEP = "onboarding_step"

        /**
         * The Settings → Getting started entry point. Replay never resets a
         * preference; it only lets the user walk the same screens again.
         */
        fun replayIntent(context: Context): Intent =
            Intent(context, OnboardingActivity::class.java)
                .putExtra(EXTRA_MODE, OnboardingMode.REPLAY.name)
    }
}
