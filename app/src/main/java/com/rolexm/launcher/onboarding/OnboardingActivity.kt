package com.rolexm.launcher.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.rolexm.launcher.R
import com.rolexm.launcher.core.RolexApp
import com.rolexm.launcher.core.util.RoleHelper
import com.rolexm.launcher.settings.RolexmTheme
import com.rolexm.launcher.ui.ThemeMode
import kotlinx.coroutines.launch

/**
 * First-run onboarding.
 *
 * Its only job is the Home role: a launcher that is not the default launcher is
 * just an app, so this screen makes the one decision the user actually needs to
 * make, and then gets out of the way.
 *
 * Skipping is a first-class outcome. ROLEXM stays fully usable as an app, and
 * the default-launcher controls remain available in Settings.
 */
class OnboardingActivity : ComponentActivity() {

    private val prefs by lazy { (application as RolexApp).core.prefs }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Record the outcome whatever it was: the user has been asked, and
        // nagging them again on every launch would be worse than not asking.
        lifecycleScope.launch {
            prefs.setOnboardingDone(result.resultCode == RESULT_OK || isDefaultNow())
        }
        finish()
    }

    private fun isDefaultNow(): Boolean = RoleHelper.isDefaultLauncher(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by prefs.themeMode.collectAsState(ThemeMode.SYSTEM)
            RolexmTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_welcome),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.onboarding_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(28.dp))
                        Button(onClick = ::requestHomeRole) {
                            Text(stringResource(R.string.onboarding_set_default))
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = {
                            lifecycleScope.launch {
                                prefs.setOnboardingDone(true)
                                finish()
                            }
                        }) {
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
