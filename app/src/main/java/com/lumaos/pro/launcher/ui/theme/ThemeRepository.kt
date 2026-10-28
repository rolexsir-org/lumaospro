package com.lumaos.pro.launcher.ui.theme

import android.app.Application
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.wallpaper.WallpaperCatalog
import com.lumaos.pro.launcher.wallpaper.WallpaperEntry
import com.lumaos.pro.launcher.wallpaper.WallpaperImageStore
import com.lumaos.pro.launcher.widgets.battery.BatteryWidgetProvider
import com.lumaos.pro.launcher.widgets.music.MusicBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Wallpaper colours and change tracking from [WallpaperManager].
 *
 * Uses `WallpaperColors` + `OnColorsChangedListener` on API 27+ (zero storage
 * permissions required), `ACTION_WALLPAPER_CHANGED` broadcasts on API 26+, and
 * an opportunistic non-privileged bitmap sample when accessible so API 26 or
 * low-hint devices still receive accurate luminance and chromaticity seeds.
 */
class WallpaperColorSource(private val app: Application) {

    private val _seeds = MutableStateFlow<WallpaperSeeds?>(null)
    val seeds: StateFlow<WallpaperSeeds?> = _seeds

    private val _systemWallpaperId = MutableStateFlow<Int?>(null)
    val systemWallpaperId: StateFlow<Int?> = _systemWallpaperId

    /** True when [seeds] lightness came from an optical heuristic rather than the API 31+ hint. */
    @Volatile
    var hintEstimated: Boolean = false
        private set

    private var listener: Any? = null
    private var receiverRegistered = false

    @Suppress("DEPRECATION")
    private val wallpaperChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                Thread({ refresh() }, "lumaos-wp-refresh").apply { isDaemon = true }.start()
            }
        }
    }

    fun start() {
        val wm = runCatching { WallpaperManager.getInstance(app) }.getOrNull() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && listener == null) {
            val l = WallpaperManager.OnColorsChangedListener { colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                    val sysId = readSystemWallpaperId(wm)
                    if (sysId != null) _systemWallpaperId.value = sysId
                    publishPlatformColors(wm, colors)
                }
            }
            runCatching { wm.addOnColorsChangedListener(l, Handler(Looper.getMainLooper())) }
                .onFailure { LumaLog.w("Wallpaper colour listener unavailable") }
            listener = l
        }
        if (!receiverRegistered) {
            @Suppress("DEPRECATION")
            val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
            runCatching {
                ContextCompat.registerReceiver(
                    app,
                    wallpaperChangedReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                receiverRegistered = true
            }.onFailure { LumaLog.w("Wallpaper broadcast receiver unavailable") }
        }
        refresh()
    }

    /** Re-reads system wallpaper ID and colours; safe to call from any thread. */
    fun refresh() {
        val wm = runCatching { WallpaperManager.getInstance(app) }.getOrNull() ?: return
        val sysId = readSystemWallpaperId(wm)
        if (sysId != null) _systemWallpaperId.value = sysId
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val colors = runCatching {
                wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            }.getOrNull()
            publishPlatformColors(wm, colors)
        } else {
            val optical = sampleAccessibleWallpaperBitmap(wm)
            if (optical != null) {
                hintEstimated = true
                _seeds.value = optical
            }
        }
    }

    private fun readSystemWallpaperId(wm: WallpaperManager): Int? =
        runCatching { wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }
            .getOrNull()
            ?.takeIf { it > 0 }

    private fun publishPlatformColors(wm: WallpaperManager, colors: WallpaperColors?) {
        val platformPair = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && colors != null) {
            WallpaperPaletteExtractor.fromPlatformColors(colors)
        } else null
        val platform = platformPair?.first
        val optical = if (platform == null || platform.topLuminance == null) {
            sampleAccessibleWallpaperBitmap(wm)
        } else null
        val merged = WallpaperPaletteExtractor.merge(platform, optical) ?: return
        hintEstimated = optical == null && (platformPair?.second ?: true)
        _seeds.value = merged
    }

    /**
     * Best-effort non-privileged sample. On Android 13+ external system wallpapers
     * `peekDrawable` may throw [SecurityException] without storage access; that is
     * expected and silently ignored in favour of [WallpaperColors].
     */
    private fun sampleAccessibleWallpaperBitmap(wm: WallpaperManager): WallpaperSeeds? =
        runCatching {
            val drawable = wm.peekDrawable() as? BitmapDrawable ?: return@runCatching null
            val bmp = drawable.bitmap ?: return@runCatching null
            if (bmp.isRecycled) null else WallpaperPaletteExtractor.extractFromBitmap(bmp)
        }.getOrNull()
}

/**
 * Single source of truth for the theme every surface draws with.
 *
 * Combines the stored [ThemeSettings], the system dark setting, the time of day
 * (for scheduled switching), the active context profile, the selected Wallpaper
 * Studio entry (bundled or imported photo), and external system wallpaper colours
 * into one [ResolvedTheme] StateFlow observed by the home screen, widgets, and
 * Compose screens alike.
 */
class ThemeRepository(
    private val app: Application,
    private val prefs: LumaPrefs,
    private val scope: CoroutineScope
) {
    val wallpaper = WallpaperColorSource(app)
    private val imageStore = WallpaperImageStore(app)

    private val systemDark = MutableStateFlow(isNight(app.resources.configuration))
    private val minuteOfDay = MutableStateFlow(nowMinute())

    val settings: StateFlow<ThemeSettings> = prefs.themeSettings
        .stateIn(scope, SharingStarted.Eagerly, ThemeSettings())

    private val profile: StateFlow<ContextProfile?> = prefs.activeContextProfile
        .stateIn(scope, SharingStarted.Eagerly, null)

    val activeWallpaperId: StateFlow<String?> = prefs.activeWallpaperId
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val activeWallpaperSystemId: StateFlow<Int?> = prefs.activeWallpaperSystemId
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val cachedSeedsPair: StateFlow<Pair<String, WallpaperSeeds>?> = prefs.cachedWallpaperSeedsRaw
        .map(WallpaperSeeds::decode)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val resolvedCustomEntry = MutableStateFlow<WallpaperEntry?>(null)
    private val resolvedCustomSeeds = MutableStateFlow<Pair<String, WallpaperSeeds>?>(null)

    /** Currently active Wallpaper Studio entry (`art:...` or `photo:...`), or `null` when using an external wallpaper. */
    val activeWallpaper: StateFlow<WallpaperEntry?> = combine(
        activeWallpaperId,
        resolvedCustomEntry
    ) { id, custom ->
        when {
            id.isNullOrBlank() -> null
            id.startsWith("art:") -> WallpaperCatalog.findBundled(id)
            id.startsWith("photo:") -> custom?.takeIf { it.id == id }
            else -> null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Coordinated optical seeds from the active Studio wallpaper, or from the live system wallpaper. */
    val effectiveWallpaperSeeds: StateFlow<WallpaperSeeds?> = combine(
        activeWallpaper,
        resolvedCustomSeeds,
        wallpaper.seeds,
        cachedSeedsPair
    ) { entry, customPair, liveSystemSeeds, persistedPair ->
        when {
            entry != null -> entry.precomputedSeeds
                ?: customPair?.takeIf { it.first == entry.id }?.second
                ?: persistedPair?.takeIf { it.first == entry.id }?.second
                ?: liveSystemSeeds
            else -> liveSystemSeeds ?: persistedPair?.takeIf { it.first.startsWith("sys:") }?.second
        }
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    val environment: StateFlow<ThemeEnvironment> = combine(
        systemDark,
        minuteOfDay,
        profile,
        effectiveWallpaperSeeds,
        activeWallpaper
    ) { dark, minute, p, seeds, entry ->
        ThemeEnvironment(
            systemDark = dark,
            minuteOfDay = minute,
            profile = p,
            wallpaper = seeds,
            hasSelectedArtwork = entry != null
        )
    }.stateIn(scope, SharingStarted.Eagerly, ThemeEnvironment(systemDark.value, minuteOfDay.value))

    val theme: StateFlow<ResolvedTheme> = combine(settings, environment) { s, env -> ThemeEngine.resolve(s, env) }
        .distinctUntilChanged()
        .stateIn(
            scope, SharingStarted.Eagerly,
            ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(systemDark.value, minuteOfDay.value))
        )

    private var scheduleJob: Job? = null

    fun start() {
        scope.launch(Dispatchers.IO) { wallpaper.start() }

        // Resolve custom `photo:...` active entries and extract/cache their optical palette off the main thread.
        scope.launch(Dispatchers.IO) {
            activeWallpaperId.collect { id ->
                if (id == null) {
                    resolvedCustomEntry.value = null
                    return@collect
                }
                if (id.startsWith("art:")) {
                    resolvedCustomEntry.value = null
                    val bundled = WallpaperCatalog.findBundled(id)
                    if (bundled == null) {
                        prefs.clearActiveWallpaperIfMatches(id)
                    } else {
                        bundled.precomputedSeeds?.let { prefs.setCachedWallpaperSeedsRaw(it.encode(id)) }
                    }
                    return@collect
                }
                if (id.startsWith("photo:")) {
                    val entry = imageStore.findEntry(id)
                    if (entry == null) {
                        resolvedCustomEntry.value = null
                        prefs.clearActiveWallpaperIfMatches(id)
                    } else {
                        resolvedCustomEntry.value = entry
                        val seeds = imageStore.extractSeeds(entry)
                        if (seeds != null) {
                            resolvedCustomSeeds.value = id to seeds
                            prefs.setCachedWallpaperSeedsRaw(seeds.encode(id))
                        }
                    }
                }
            }
        }

        // Detect external system wallpaper changes (e.g. changed in Android Settings or another wallpaper app)
        // when the active Studio selection had been applied to the system home wallpaper.
        scope.launch(Dispatchers.IO) {
            combine(
                activeWallpaperId,
                activeWallpaperSystemId,
                wallpaper.systemWallpaperId
            ) { activeId, recordedSysId, liveSysId ->
                Triple(activeId, recordedSysId, liveSysId)
            }.collect { (activeId, recordedSysId, liveSysId) ->
                if (activeId != null && recordedSysId != null && liveSysId != null && liveSysId != recordedSysId) {
                    prefs.clearActiveWallpaperIfMatches(activeId)
                }
            }
        }

        // Persist external system wallpaper seeds so cold starts resolve without flash.
        scope.launch(Dispatchers.IO) {
            combine(activeWallpaperId, wallpaper.seeds, wallpaper.systemWallpaperId) { activeId, sysSeeds, sysId ->
                Triple(activeId, sysSeeds, sysId)
            }.collect { (activeId, sysSeeds, sysId) ->
                if (activeId == null && sysSeeds != null) {
                    prefs.setCachedWallpaperSeedsRaw(sysSeeds.encode("sys:${sysId ?: 0}"))
                }
            }
        }

        // Keep Home screen AppWidgets (Battery & Music) synchronized whenever resolved palette changes.
        scope.launch(Dispatchers.Main.immediate) {
            theme
                .map { Triple(it.colors, it.isDark, it.radius(RadiusRole.LARGE)) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    BatteryWidgetProvider.refresh(app)
                    MusicBridge.refreshIfActive(app)
                }
        }

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

    /** Re-reads time and wallpaper colours; called when the home screen or Studio resumes. */
    fun refresh() {
        minuteOfDay.value = nowMinute()
        scope.launch(Dispatchers.IO) { wallpaper.refresh() }
    }

    /** Resolves [draft] (e.g. a Studio or Wallpaper Studio preview) against the live environment. */
    fun preview(draft: ThemeSettings): ResolvedTheme = ThemeEngine.resolve(draft, environment.value)

    /** Resolves [draft] against custom [wallpaperSeeds] (e.g. live previewing an unapplied gallery wallpaper). */
    fun previewWithWallpaper(
        draft: ThemeSettings,
        wallpaperSeeds: WallpaperSeeds?,
        hasSelectedArtwork: Boolean = true
    ): ResolvedTheme = ThemeEngine.resolve(
        draft,
        environment.value.copy(
            wallpaper = wallpaperSeeds,
            hasSelectedArtwork = hasSelectedArtwork
        )
    )

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
