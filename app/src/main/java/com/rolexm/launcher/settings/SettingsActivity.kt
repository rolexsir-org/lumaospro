package com.rolexm.launcher.settings

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.rolexm.launcher.R
import com.rolexm.launcher.core.util.RoleHelper
import com.rolexm.launcher.ui.Palettes
import com.rolexm.launcher.ui.ThemeMode

/**
 * ROLEXM settings and diagnostics.
 *
 * Compose is the right tool here: these screens are state-driven, change rarely,
 * and are opened deliberately by the user, so the flexibility is worth more than
 * the marginal cost of the runtime. The hot path - the home screen and drawer -
 * stays on Views.
 */
class SettingsActivity : ComponentActivity() {

    private val viewModel by lazy { ViewModelProvider(this)[SettingsViewModel::class.java] }

    /**
     * Result of the Home role request. The system grants or refuses the role in
     * its own dialog; ROLEXM only re-reads the outcome afterwards. A refusal is
     * not an error - ROLEXM continues to work as an ordinary app.
     */
    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshRoleStatus()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        lifecycleScope.launch { viewModel.prefs.setContextNotificationsEnabled(granted) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.TRANSPARENT

        setContent {
            val themeMode by viewModel.prefs.themeMode.collectAsState(ThemeMode.LIGHT)
            val textScale by viewModel.prefs.textScale.collectAsState(1.0f)

            RolexmTheme(themeMode = themeMode, textScale = textScale) {
                val palette = Palettes.forMode(themeMode, isSystemInDarkTheme())

                // Keep the status-bar icons legible against our own background.
                DisposableEffect(palette.isDark) {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !palette.isDark
                        isAppearanceLightNavigationBars = !palette.isDark
                    }
                    onDispose { }
                }

                var tab by remember { mutableIntStateOf(0) }
                val tabs = listOf(
                    stringResource(R.string.title_settings_tab),
                    stringResource(R.string.dash_title)
                )

                Scaffold(
                    topBar = {
                        Column {
                            TopAppBar(
                                title = { Text("ROLEXM") },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            PrimaryTabRow(selectedTabIndex = tab) {
                                tabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = tab == index,
                                        onClick = { tab = index },
                                        text = { Text(title) }
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box {
                            when (tab) {
                                0 -> SettingsRoot(
                                    viewModel = viewModel,
                                    onRequestHomeRole = ::requestHomeRole,
                                    onOpenHomeSettings = {
                                        RoleHelper.openHomeSettings(this@SettingsActivity)
                                    },
                                    onRequestNotifications = ::requestContextNotifications
                                )
                                else -> DashboardRoot(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The system emits nothing when the default launcher changes, so the
        // role is re-resolved every time this screen appears.
        viewModel.refreshRoleStatus()
        viewModel.refreshHistory()
        viewModel.refreshContextDnd()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch { viewModel.prefs.setContextNotificationsEnabled(false) }
        }
    }

    private fun requestContextNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            lifecycleScope.launch { viewModel.prefs.setContextNotificationsEnabled(true) }
        }
    }

    private fun requestHomeRole() {
        val intent = RoleHelper.homeRoleRequestIntent(this)
        if (intent != null) {
            runCatching { roleLauncher.launch(intent) }
                .onFailure { RoleHelper.openHomeSettings(this) }
        } else {
            RoleHelper.openHomeSettings(this)
        }
    }
}
