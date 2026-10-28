package com.lumaos.pro.launcher.widgets.music

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.settings.LumaOSTheme
import com.lumaos.pro.launcher.ui.glass.LumaGlassCard
import com.lumaos.pro.launcher.ui.glass.LumaGlassChoice
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(theme.colors.background))
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        LumaGlassTopBar(
                            theme = theme,
                            title = stringResource(R.string.music_access_title),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            navigationIcon = {
                                LumaGlassChoice(
                                    theme = theme,
                                    label = stringResource(R.string.wallpaper_back),
                                    selected = false,
                                    onClick = { finish() }
                                )
                            }
                        )
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LumaGlassCard(
                                theme = theme,
                                title = stringResource(R.string.music_widget_name),
                                subtitle = stringResource(
                                    if (access) R.string.music_access_granted
                                    else R.string.music_access_needed
                                )
                            ) {
                                Text(
                                    stringResource(R.string.music_access_disclosure),
                                    color = Color(theme.colors.onSurface),
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    stringResource(R.string.music_access_restricted),
                                    color = Color(theme.colors.onSurfaceMuted),
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(16.dp))
                                LumaGlassChoice(
                                    theme = theme,
                                    label = stringResource(R.string.music_access_open),
                                    selected = true,
                                    onClick = {
                                        runCatching {
                                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        }.onFailure {
                                            Toast.makeText(
                                                this@MusicAccessActivity,
                                                R.string.music_access_unavailable,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
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
