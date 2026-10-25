package com.lumaos.pro.launcher.ui.theme

import android.app.Application
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import com.lumaos.pro.launcher.core.util.LumaLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Wallpaper colours from WallpaperManager (API 27+, no permission required).
 *
 * Below API 31 the platform does not expose the "supports dark text" hint, so
 * lightness is estimated from the reported colours and flagged as estimated.
 */
class WallpaperColorSource(private val app: Application) {

    private val _seeds = MutableStateFlow<WallpaperSeeds?>(null)
    val seeds: StateFlow<WallpaperSeeds?> = _seeds

    /** True when [seeds] lightness came from a heuristic rather than the platform hint. */
    @Volatile
    var hintEstimated: Boolean = false
        private set

    private var listener: Any? = null

    fun start() {
        if (Build.VERSION.SDK_INT < 27) return
        val wm = runCatching { WallpaperManager.getInstance(app) }.getOrNull() ?: return
        val l = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) publish(colors)
        }
        runCatching { wm.addOnColorsChangedListener(l, Handler(Looper.getMainLooper())) }
            .onFailure { LumaLog.w("Wallpaper colour listener unavailable") }
        listener = l
        refresh()
    }

    /** Re-reads colours (cheap: the system caches them). Safe to call from any thread. */
    fun refresh() {
        if (Build.VERSION.SDK_INT < 27) return
        val colors = runCatching {
            WallpaperManager.getInstance(app).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull()
        publish(colors)
    }

    private fun publish(colors: WallpaperColors?) {
        if (Build.VERSION.SDK_INT < 27 || colors == null) return
        val primary = colors.primaryColor.toArgb()
        val secondary = colors.secondaryColor?.toArgb()
        val tertiary = colors.tertiaryColor?.toArgb()
        val supportsDarkText: Boolean
        if (Build.VERSION.SDK_INT >= 31) {
            supportsDarkText = colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
            hintEstimated = false
        } else {
            // Weighted average luminance of the reported colours.
            val samples = listOfNotNull(primary, secondary, tertiary)
            val lum = samples.mapIndexed { i, c -> ColorMath.luminance(c) * (3 - i) }.sum() /
                samples.indices.sumOf { 3 - it }
            supportsDarkText = lum > 0.45
            hintEstimated = true
        }
        _seeds.value = WallpaperSeeds(primary, secondary, tertiary, supportsDarkText)
    }
}

/**
 * Single source of truth for the theme every surface draws with.
 *
 * Combines the stored [ThemeSettings], the system dark setting, the time of day
 * (for scheduled switching), the active context profile and wallpaper colours
 * into one [ResolvedTheme] StateFlow observed by the home screen and the
 * Compose screens alike.
 */
class ThemeRepository(
    private val app: Application,
    prefs: LumaPrefs,
    private val scope: CoroutineScope
) {
    val wallpaper = WallpaperColorSource(app)

    private val systemDark = MutableStateFlow(isNight(app.resources.configuration))
    private val minuteOfDay = MutableStateFlow(nowMinute())

    val settings: StateFlow<ThemeSettings> = prefs.themeSettings
        .stateIn(scope, SharingStarted.Eagerly, ThemeSettings())

    private val profile: StateFlow<ContextProfile?> = prefs.activeContextProfile
        .stateIn(scope, SharingStarted.Eagerly, null)

    val environment: StateFlow<ThemeEnvironment> = combine(
        systemDark, minuteOfDay, profile, wallpaper.seeds
    ) { dark, minute, p, seeds -> ThemeEnvironment(dark, minute, p, seeds) }
        .stateIn(scope, SharingStarted.Eagerly, ThemeEnvironment(systemDark.value, minuteOfDay.value))

    val theme: StateFlow<ResolvedTheme> = combine(settings, environment) { s, env -> ThemeEngine.resolve(s, env) }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(systemDark.value, minuteOfDay.value))
        )

    private var scheduleJob: Job? = null

    fun start() {
        scope.launch(Dispatchers.IO) { wallpaper.start() }
        // Wake exactly at the next light/dark boundary while scheduled
        // switching is selected; no polling otherwise.
        scope.launch {
            settings.collect { s ->
                scheduleJob?.cancel()
                if (s.switching == ThemeSwitching.SCHEDULE) {
                    scheduleJob = scope.launch {
                        while (isActive) {
                            val now = nowMinute()
                            minuteOfDay.value = now
                            val wait = s.darkWindow.minutesUntilNextChange(now)
                            delay(wait * 60_000L - secondsIntoMinute() * 1000L + 500L)
                        }
                    }
                }
            }
        }
    }

    /** Called from Activity.onCreate/onResume and Application.onConfigurationChanged. */
    fun onConfiguration(config: Configuration) {
        systemDark.value = isNight(config)
    }

    /** Re-reads time and wallpaper colours; called when the home screen resumes. */
    fun refresh() {
        minuteOfDay.value = nowMinute()
        scope.launch(Dispatchers.IO) { wallpaper.refresh() }
    }

    /** Resolves [settings] (e.g. a Studio draft) against the live environment. */
    fun preview(draft: ThemeSettings): ResolvedTheme = ThemeEngine.resolve(draft, environment.value)

    companion object {
        fun isNight(config: Configuration): Boolean =
            (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        fun nowMinute(): Int {
            val c = Calendar.getInstance()
            return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        }

        private fun secondsIntoMinute(): Int = Calendar.getInstance().get(Calendar.SECOND)
    }
}
