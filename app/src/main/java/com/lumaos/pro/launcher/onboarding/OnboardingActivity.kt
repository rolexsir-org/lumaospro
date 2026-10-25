package com.lumaos.pro.launcher.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.util.RoleHelper
import com.lumaos.pro.launcher.settings.LumaOSTheme
import com.lumaos.pro.launcher.ui.Palettes
import com.lumaos.pro.launcher.ui.ThemeMode
import kotlinx.coroutines.launch

/**
 * First-run onboarding.
 *
 * Its only job is the Home role: a launcher that is not the default launcher is
 * just an app, so this screen makes the one decision the user actually needs to
 * make, and then gets out of the way.
 *
 * Skipping is a first-class outcome. LumaOS Pro stays fully usable as an app, and
 * the default-launcher controls remain available in Settings.
 */
class OnboardingActivity : ComponentActivity() {

    private val prefs by lazy { (application as LumaApp).core.prefs }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Record the outcome whatever it was: the user has been asked, and
        // nagging them again on every launch would be worse than not asking.
        lifecycleScope.launch {
            // Completion means the user answered, not that the role was
            // granted. A denial must not re-open onboarding on every launch;
            // Home-role status continues to be read independently in Settings.
            prefs.setOnboardingDone(true)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by prefs.themeMode.collectAsState(ThemeMode.LIGHT)
            val palette = Palettes.forMode(themeMode, isSystemInDarkTheme())
            DisposableEffect(palette.isDark) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !palette.isDark
                    isAppearanceLightNavigationBars = !palette.isDark
                }
                onDispose { }
            }
            LumaOSTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
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
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.onboarding_contexts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = ::requestHomeRole,
                            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.onboarding_set_default))
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                lifecycleScope.launch {
                                    prefs.setOnboardingDone(true)
                                    finish()
                                }
                            },
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

    private fun requestHomeRole() {
        val intent = RoleHelper.homeRoleRequestIntent(this)
        if (intent != null) {
            runCatching { roleLauncher.launch(intent) }
                .onFailure { fallbackToHomeSettings() }
        } else {
            fallbackToHomeSettings()
        }
    }

    /** Older Android, or an OEM that has not implemented the Home role. */
    private fun fallbackToHomeSettings() {
        RoleHelper.openHomeSettings(this)
        lifecycleScope.launch {
            prefs.setOnboardingDone(true)
            finish()
        }
    }
}
