package com.lumaos.pro.launcher.widgets.music

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.settings.LumaOSTheme
import com.lumaos.pro.launcher.ui.glass.LumaGlassTopBar

/** Informed, optional permission entry point; never appears over Android keyguard. */
class MusicAccessActivity : ComponentActivity() {
    private var access by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val core = (application as LumaApp).core
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
                Scaffold(topBar = {
                    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        LumaGlassTopBar(theme, stringResource(R.string.music_access_title),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            navigationIcon = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.wallpaper_back)) } })
                    }
                }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.music_access_disclosure), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(if (access) R.string.music_access_granted else R.string.music_access_needed),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.music_access_restricted), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(modifier = Modifier.fillMaxWidth(), onClick = {
                            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                                .onFailure { Toast.makeText(this@MusicAccessActivity, R.string.music_access_unavailable, Toast.LENGTH_LONG).show() }
                        }) { Text(stringResource(R.string.music_access_open)) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        access = MusicBridge.hasAccess(this)
        MusicBridge.refresh(applicationContext)
    }
}
