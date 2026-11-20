package com.lumaos.pro.launcher.context

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.prefs.PrefsFocusAccessConsentStore
import com.lumaos.pro.launcher.settings.LumaOSTheme
import kotlinx.coroutines.launch

/**
 * Prominent in-app disclosure and consent surface for the optional
 * accessibility service behind Focus Protection ("Stronger blocking").
 *
 * Play policy needs the disclosure to appear *in the app*, before the system
 * permission screen, with an affirmative choice. This activity is that
 * surface: it explains why the service exists, what it observes, and what is
 * stored, collected or shared, and it offers explicit AGREE and DECLINE
 * actions. Android's accessibility screen is opened only from the AGREE path,
 * and only after the agreement is written to DataStore.
 *
 * Facts this screen keeps straight:
 *
 *  - Android's permission and the user's consent are separate. The activity
 *    reads the grant through [FocusAccess.isGranted] and the consent record
 *    through `LumaPrefs`; it never writes the permission (an app cannot), and
 *    a granted permission with no consent is shown as "not answered", not as
 *    enabled.
 *  - Declining is a first-class outcome: the record is persisted so the app
 *    does not ask again by itself, layer 2 never runs, and layer 1 (launcher
 *    interception) is untouched.
 *  - The screen is also the revisit/revoke entry point. When consent is
 *    recorded it offers "Open Android accessibility settings" (the only place
 *    the grant can actually be turned off) and "Withdraw consent", which stops
 *    enforcement immediately even if the system grant is still on.
 *
 * The visible copy is static resource text, and the disclosure version is
 * stored with the answer, so a wording change that alters what is agreed to
 * can invalidate older consent (see [FocusAccessConsent.CURRENT_DISCLOSURE_VERSION]).
 */
class FocusAccessDisclosureActivity : ComponentActivity() {

    private var consent by mutableStateOf(FocusAccessConsent.NOT_ASKED)
    private var permissionGranted by mutableStateOf(false)
    private var decisionInFlight by mutableStateOf(false)

    private val prefs by lazy { (application as LumaApp).core.prefs }
    private val consentFlow by lazy { FocusAccessConsentFlow(PrefsFocusAccessConsentStore(prefs)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val core = (application as LumaApp).core
        refresh()
        setContent {
            val theme by core.themes.theme.collectAsState()
            val textScale by core.prefs.textScale.collectAsState(1f)
            LumaOSTheme(theme, textScale) {
                LaunchedEffect(theme.isDark) {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !theme.isDark
                        isAppearanceLightNavigationBars = !theme.isDark
                    }
                }
                val state = FocusAccessConsent.uiState(consent, permissionGranted)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(theme.colors.background))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        FocusAccessDisclosureScreen(
                            theme = theme,
                            state = state,
                            permissionGranted = permissionGranted,
                            decidedAtMillis = consent.decidedAtMillis,
                            onAgree = ::agree,
                            onDecline = ::decline,
                            onManage = ::openAccessibilitySettings,
                            onWithdraw = ::withdraw,
                            onClose = { finish() },
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The system emits nothing when the accessibility toggle changes, so the
        // grant is re-read every time this screen comes back from Settings. A
        // revocation therefore reads as AWAITING_PERMISSION, never as active.
        refresh()
    }

    private fun refresh() {
        permissionGranted = FocusAccess.isGranted(this)
        lifecycleScope.launch {
            consent = consentFlow.read()
        }
    }

    /**
     * AGREE. The consent record is persisted before anything else happens; the
     * system screen is opened only when this coroutine resumes with the write
     * complete, so a process death cannot leave a permission request with no
     * recorded agreement behind it.
     */
    private fun agree() {
        if (decisionInFlight) return
        decisionInFlight = true
        lifecycleScope.launch {
            val record = runCatching { consentFlow.agree() }.getOrNull()
            decisionInFlight = false
            if (record == null) {
                Toast.makeText(this@FocusAccessDisclosureActivity, R.string.focus_access_save_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            consent = record
            refresh()
            if (FocusAccess.isGranted(this@FocusAccessDisclosureActivity)) {
                // The grant already exists (for example the user enabled the service
                // in system settings themselves). Consent is now on record and layer 2
                // becomes active; opening Settings again would be a pointless detour.
                Toast.makeText(
                    this@FocusAccessDisclosureActivity,
                    R.string.focus_access_consent_recorded,
                    Toast.LENGTH_LONG
                ).show()
            } else {
                openAccessibilitySettings()
            }
        }
    }

    /** DECLINE. Persisted, and deliberately no route to system settings. */
    private fun decline() {
        if (decisionInFlight) return
        decisionInFlight = true
        lifecycleScope.launch {
            val record = runCatching { consentFlow.decline() }.getOrNull()
            decisionInFlight = false
            if (record == null) {
                Toast.makeText(this@FocusAccessDisclosureActivity, R.string.focus_access_save_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            consent = record
            Toast.makeText(
                this@FocusAccessDisclosureActivity,
                R.string.focus_access_declined_toast,
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    /**
     * Withdraws consent while the screen shows the granted state. The system
     * grant may still be on until the user turns it off; the service stops
     * enforcing immediately because it requires a current GRANTED record.
     */
    private fun withdraw() {
        if (decisionInFlight) return
        decisionInFlight = true
        lifecycleScope.launch {
            val record = runCatching { consentFlow.withdraw() }.getOrNull()
            decisionInFlight = false
            if (record == null) {
                Toast.makeText(this@FocusAccessDisclosureActivity, R.string.focus_access_save_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            consent = record
            Toast.makeText(
                this@FocusAccessDisclosureActivity,
                R.string.focus_access_withdrawn_toast,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * The only route to Android's screen. Resolved first: an OEM build that
     * cannot answer either intent gets an explanation instead of a dead tap.
     */
    private fun openAccessibilitySettings() = FocusAccess.openSettings(this)
}
