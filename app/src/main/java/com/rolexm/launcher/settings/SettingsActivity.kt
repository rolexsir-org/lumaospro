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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.rolexm.launcher.R
import com.rolexm.launcher.core.RolexApp
import com.rolexm.launcher.core.util.RoleHelper
import com.rolexm.launcher.settings.studio.StudioRoot

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

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportBackup) }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.backup_confirm_title)
            .setMessage(R.string.backup_confirm_generic)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.backup_confirm_restore) { _, _ -> viewModel.importBackup(uri) }
            .show()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.TRANSPARENT

        val themes = (application as RolexApp).core.themes
        setContent {
            // The same resolved theme the home screen draws with.
            val theme by themes.theme.collectAsState()
            val textScale by viewModel.prefs.textScale.collectAsState(1.0f)

            RolexmTheme(theme = theme, textScale = textScale) {
                // Keep the status-bar icons legible against our own background.
                DisposableEffect(theme.isDark) {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !theme.isDark
                        isAppearanceLightNavigationBars = !theme.isDark
                    }
                    onDispose { }
                }

                val initialTab = if (intent.getStringExtra(EXTRA_TAB) == TAB_STUDIO) 1 else 0
                var tab by rememberSaveable { mutableIntStateOf(initialTab) }
                val tabs = listOf(
                    stringResource(R.string.title_settings_tab),
                    stringResource(R.string.tab_studio),
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
                                    onRequestNotifications = ::requestContextNotifications,
                                    onExportBackup = {
                                        exportBackupLauncher.launch("rolexm-backup.json")
                                    },
                                    onImportBackup = {
                                        importBackupLauncher.launch(
                                            arrayOf("application/json", "text/plain", "application/octet-stream")
                                        )
                                    }
                                )
                                1 -> StudioRoot(viewModel = viewModel)
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
        viewModel.refreshIconPacks()
        viewModel.refreshApps()
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

    companion object {
        /** Optional tab to open; the home screen's edit mode passes [TAB_STUDIO]. */
        const val EXTRA_TAB = "com.rolexm.launcher.extra.SETTINGS_TAB"
        const val TAB_STUDIO = "studio"
    }
}
