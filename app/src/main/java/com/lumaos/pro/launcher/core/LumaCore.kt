package com.lumaos.pro.launcher.core

import android.app.Application
import android.content.ComponentCallbacks2
import com.lumaos.pro.launcher.core.apps.AppRepository
import com.lumaos.pro.launcher.core.apps.IconCache
import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.context.ContextScheduleManager
import com.lumaos.pro.launcher.context.ContextAutomation
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.diagnostics.DiagnosticsRepository
import com.lumaos.pro.launcher.diagnostics.DiagnosticsStore
import com.lumaos.pro.launcher.intelligence.DeviceCapability
import com.lumaos.pro.launcher.intelligence.DeviceCapabilityProvider
import com.lumaos.pro.launcher.intelligence.IntelligenceEngine
import com.lumaos.pro.launcher.intelligence.Profile
import com.lumaos.pro.launcher.intelligence.Profiles
import com.lumaos.pro.launcher.optimizer.Optimizer
import com.lumaos.pro.launcher.core.apps.ShortcutRepository
import com.lumaos.pro.launcher.core.usage.LaunchHistoryStore
import com.lumaos.pro.launcher.effects.EffectsController
import com.lumaos.pro.launcher.icons.IconPackManager
import com.lumaos.pro.launcher.icons.LivingIconManager
import com.lumaos.pro.launcher.launcher.home.WallpaperBitmaps
import com.lumaos.pro.launcher.ui.theme.LumaThemeManager
import com.lumaos.pro.launcher.ui.theme.ThemeRepository
import com.lumaos.pro.launcher.wallpaper.WallpaperImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The single place LumaOS Pro's long-lived collaborators are constructed and held.
 *
 * A hand-written holder rather than a DI framework: the graph is eight objects
 * with no scoping rules, and a DI library would add an annotation processor to
 * the build plus runtime reflection to an app whose entire premise is being
 * small.
 *
 * Everything here lives for the process. Nothing here starts a service.
 */
class LumaCore(private val application: Application) {

    val prefs: LumaPrefs = LumaPrefs.create(application)
    val appRepository: AppRepository = AppRepository(application)
    val iconCache: IconCache = IconCache(Profiles.BALANCED.maxCachedIcons)

    val diagnosticsStore: DiagnosticsStore = DiagnosticsStore(application)
    val diagnostics: DiagnosticsRepository = DiagnosticsRepository(diagnosticsStore)

    val optimizer: Optimizer = Optimizer(application, prefs, iconCache, diagnostics)

    /** Measured once at startup; hardware does not change during a process. */
    val capability: DeviceCapability = DeviceCapabilityProvider.snapshot(application)

    val intelligence: IntelligenceEngine = IntelligenceEngine(
        context = application,
        prefs = prefs,
        iconCache = iconCache,
        diagnostics = diagnostics,
        optimizer = optimizer,
        capability = capability
    )

    private val coreScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Theme engine runtime: settings + system dark + schedule + context + wallpaper. */
    val themes: ThemeRepository = ThemeRepository(application, prefs, coreScope)

    /** Adaptive wallpaper luminance, tint, accent & warmth state across Views and Compose. */
    val themeManager: LumaThemeManager = LumaThemeManager(application, prefs, coreScope)

    /** Effects tiering from live device signals. */
    val effects: EffectsController = EffectsController(application, capability, prefs, intelligence.state, coreScope)

    /** Launches made through LumaOS Pro (on-device only). */
    val launchHistory: LaunchHistoryStore = LaunchHistoryStore(application, coreScope)

    val shortcuts: ShortcutRepository = ShortcutRepository(application)
    val iconPacks: IconPackManager = IconPackManager(application)
    val livingIcons: LivingIconManager = LivingIconManager(application)

    fun start() {
        LumaLog.i(
            "LumaOS Pro starting: ${capability.totalMemoryMb} MB RAM, " +
                "${capability.cpuCoreCount} cores, tier ${capability.tier}"
        )

        intelligence.attach()
        themes.start()
        themeManager.start()
        effects.start()

        coreScope.launch {
            combine(prefs.livingIconsEnabled, prefs.livingIconOverrides) { enabled, overrides ->
                enabled to overrides
            }.distinctUntilChanged().collect { (enabled, overrides) ->
                livingIcons.updateConfiguration(enabled, overrides)
            }
        }

        // One-shot wall-clock alarms drive context changes; there is no periodic poll.
        coreScope.launch {
            prefs.profileSchedules.distinctUntilChanged().collect {
                runCatching { ContextScheduleManager.refresh(application, prefs) }
                    .onFailure { LumaLog.e("Could not refresh context schedule", it) }
            }
        }

        coreScope.launch {
            var previous: ContextProfile? = null
            prefs.activeContextProfile.collect { active ->
                if (previous != null && previous != active) {
                    ContextAutomation.notifyProfileActivated(application, prefs, active)
                }
                previous = active
            }
        }

        coreScope.launch {
            combine(prefs.activeContextProfile, prefs.contextDndProfiles) { active, dndProfiles ->
                active to dndProfiles
            }.distinctUntilChanged().collect { (active, _) ->
                ContextAutomation.applyDoNotDisturb(application, prefs, active)
            }
        }

        // Keep the icon cache ceiling in step with whichever profile is active.
        // The engine resizes on profile change; this catches the case where the
        // engine has not yet attached when a profile is resolved.
        coreScope.launch {
            intelligence.state
                .distinctUntilChanged { a, b -> a.profile.id == b.profile.id }
                .collect { applyProfile(it.profile) }
        }
    }

    /** Re-checks notification-policy access when returning from Android Settings. */
    fun refreshContextDnd() {
        coreScope.launch {
            ContextAutomation.applyDoNotDisturb(application, prefs, prefs.activeContextProfile.first())
        }
    }

    private fun applyProfile(profile: Profile) {
        iconCache.resize(profile.maxCachedIcons)
    }

    /**
     * Memory pressure callback.
     *
     * LumaOS Pro is a launcher: it is the process the system keeps alive longest,
     * so it is also the process the system will ask first. Dropping caches here
     * is cheap and immediate, and is the only correct response - there is
     * nothing else a launcher can legitimately release under pressure.
     */
    fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                LumaLog.w("Severe memory pressure (level $level): clearing icon & wallpaper caches")
                iconCache.clear()
                livingIcons.trimMemory(clearAll = true)
                WallpaperImageStore.clearCache()
                WallpaperBitmaps.clear()
                themeManager.trimMemory()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                LumaLog.i("Memory pressure (level $level): trimming icon & wallpaper caches")
                iconCache.trim(fraction = 0.5f, keep = Optimizer.ICON_CACHE_KEEP)
                livingIcons.trimMemory(clearAll = false)
                WallpaperImageStore.trimCache(0.5f)
                WallpaperBitmaps.trim(0.5f)
                themeManager.trimMemory()
            }
        }
    }

    fun onLowMemory() {
        LumaLog.w("onLowMemory: clearing icon & wallpaper caches")
        iconCache.clear()
        livingIcons.trimMemory(clearAll = true)
        WallpaperImageStore.clearCache()
        WallpaperBitmaps.clear()
        themeManager.trimMemory()
    }

    private var idleCacheReleaseJob: kotlinx.coroutines.Job? = null

    /**
     * Schedules a 30-second background cache release when the launcher UI leaves
     * the foreground so idle PSS stays well below the <150MB memory ceiling.
     */
    fun scheduleBackgroundCacheRelease(
        delayMs: Long = com.lumaos.pro.launcher.ui.theme.LumaTokens.backgroundCacheReleaseDelayMs
    ) {
        idleCacheReleaseJob?.cancel()
        idleCacheReleaseJob = coreScope.launch {
            kotlinx.coroutines.delay(delayMs)
            LumaLog.i("30s background idle: releasing transient caches to stay well under <150MB budget")
            iconCache.trim(fraction = 0.35f, keep = Optimizer.ICON_CACHE_KEEP)
            livingIcons.trimMemory(clearAll = false)
            WallpaperImageStore.trimCache(0.25f)
            WallpaperBitmaps.trim(0.25f)
            themeManager.trimMemory()
        }
    }

    fun cancelBackgroundCacheRelease() {
        idleCacheReleaseJob?.cancel()
        idleCacheReleaseJob = null
    }

    fun shutdown() {
        idleCacheReleaseJob?.cancel()
        intelligence.shutdown()
    }
}
