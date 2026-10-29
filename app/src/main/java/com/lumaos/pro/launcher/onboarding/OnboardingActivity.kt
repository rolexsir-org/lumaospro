package com.lumaos.pro.launcher.onboarding

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.core.util.RoleHelper
import com.lumaos.pro.launcher.settings.ExperienceProfileController
import com.lumaos.pro.launcher.settings.LumaOSTheme
import com.lumaos.pro.launcher.ui.glass.LumaGlassSurface
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.ExperienceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * First-run onboarding.
 *
 * Two decisions, and nothing else: the coordinated experience profile (Pure,
 * Liquid or Midnight) and the Home role. A launcher that is not the default
 * launcher is just an app, so the role request is the only blocking question.
 *
 * Skipping is a first-class outcome. LumaOS Pro stays fully usable as an app,
 * and the default-launcher controls remain available in Settings. Choosing a
 * profile only writes appearance preferences - layouts and widgets are untouched.
 */
class OnboardingActivity : ComponentActivity() {

    private val prefs by lazy { (application as LumaApp).core.prefs }
    private var roleRequestInFlight = false

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        roleRequestInFlight = false
        // Do not infer success from resultCode. Re-read Android's current HOME
        // selection; onboarding completion is a separate, already-persisted fact.
        val isDefault = RoleHelper.isDefaultLauncher(this)
        LumaLog.i("Home-role request returned; LumaOS Pro default=$isDefault")
        finish()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                completeOnboardingAndFinish()
            }
        })

        val themes = (application as LumaApp).core.themes
        setContent {
            val theme by themes.theme.collectAsState()
            val textScale by prefs.textScale.collectAsState(1.0f)
            val selectedProfile by prefs.experienceProfile.collectAsState(ExperienceProfile.DEFAULT)
            LumaOSTheme(theme = theme, textScale = textScale) {
                // A restored or previously customised appearance always wins.
                // The guard and default write are one DataStore transaction.
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_welcome),
                            modifier = Modifier.widthIn(max = 440.dp),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.onboarding_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.onboarding_profiles_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.onboarding_profiles_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                        ExperienceProfile.entries.forEach { profile ->
                            ProfileCard(
                                theme = theme,
                                profile = profile,
                                selected = profile == selectedProfile,
                                onSelected = {
                                    lifecycleScope.launch {
                                        safelyUpdateAppearance { ExperienceProfileController.apply(prefs, profile) }
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.onboarding_home_role_explainer),
                            modifier = Modifier.widthIn(max = 440.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = ::requestHomeRole,
                            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.onboarding_set_default))
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = ::completeOnboardingAndFinish,
                            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.onboarding_skip))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.onboarding_privacy_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ProfileCard(
        theme: com.lumaos.pro.launcher.ui.theme.ResolvedTheme,
        profile: ExperienceProfile,
        selected: Boolean,
        onSelected: () -> Unit
    ) {
        val title = when (profile) {
            ExperienceProfile.PURE -> stringResource(R.string.experience_pure)
            ExperienceProfile.LIQUID -> stringResource(R.string.experience_liquid)
            ExperienceProfile.MIDNIGHT -> stringResource(R.string.experience_midnight)
        }
        val description = when (profile) {
            ExperienceProfile.PURE -> stringResource(R.string.experience_pure_desc)
            ExperienceProfile.LIQUID -> stringResource(R.string.experience_liquid_desc)
            ExperienceProfile.MIDNIGHT -> stringResource(R.string.experience_midnight_desc)
        }
        LumaGlassSurface(
            theme = theme,
            role = GlassRole.SETTINGS_CONTROL,
            selected = selected,
            onClick = onSelected,
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                }
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    private fun completeOnboardingAndFinish() {
        lifecycleScope.launch {
            if (persistOnboardingCompletion()) finish()
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

    private fun requestHomeRole() {
        if (roleRequestInFlight || isFinishing || isDestroyed) return
        roleRequestInFlight = true
        lifecycleScope.launch {
            // Persist the user's onboarding decision before crossing into Android
            // Settings. Process death there must not cause onboarding to restart.
            if (!persistOnboardingCompletion()) {
                roleRequestInFlight = false
                return@launch
            }
            if (RoleHelper.isDefaultLauncher(this@OnboardingActivity)) {
                roleRequestInFlight = false
                finish()
                return@launch
            }
            val intent = RoleHelper.homeRoleRequestIntent(this@OnboardingActivity)
            if (intent != null) {
                runCatching { roleLauncher.launch(intent) }
                    .onFailure {
                        LumaLog.w("Home role request unavailable; opening Home settings")
                        fallbackToHomeSettings()
                    }
            } else {
                fallbackToHomeSettings()
            }
        }
    }

    /** Older Android, or an OEM that has not implemented the Home role. */
    private fun fallbackToHomeSettings() {
        RoleHelper.openHomeSettings(this)
        finish()
    }
}
