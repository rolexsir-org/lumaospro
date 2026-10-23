package com.rolexm.launcher.core

import android.app.Application
import android.content.ComponentCallbacks2
import com.rolexm.launcher.core.apps.AppRepository
import com.rolexm.launcher.core.apps.IconCache
import com.rolexm.launcher.core.prefs.RolexPrefs
import com.rolexm.launcher.core.util.RolexLog
import com.rolexm.launcher.context.ContextScheduleManager
import com.rolexm.launcher.context.ContextAutomation
import com.rolexm.launcher.context.ContextProfile
import com.rolexm.launcher.diagnostics.DiagnosticsRepository
import com.rolexm.launcher.diagnostics.DiagnosticsStore
import com.rolexm.launcher.intelligence.DeviceCapability
import com.rolexm.launcher.intelligence.DeviceCapabilityProvider
import com.rolexm.launcher.intelligence.IntelligenceEngine
import com.rolexm.launcher.intelligence.Profile
import com.rolexm.launcher.intelligence.Profiles
import com.rolexm.launcher.optimizer.Optimizer
import com.rolexm.launcher.core.apps.ShortcutRepository
import com.rolexm.launcher.core.usage.LaunchHistoryStore
import com.rolexm.launcher.effects.EffectsController
import com.rolexm.launcher.icons.IconPackManager
import com.rolexm.launcher.ui.theme.ThemeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The single place ROLEXM's long-lived collaborators are constructed and held.
 *
 * A hand-written holder rather than a DI framework: the graph is eight objects
 * with no scoping rules, and a DI library would add an annotation processor to
 * the build plus runtime reflection to an app whose entire premise is being
 * small.
 *
 * Everything here lives for the process. Nothing here starts a service.
 */
class RolexCore(private val application: Application) {

    val prefs: RolexPrefs = RolexPrefs.create(application)
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

    /** Effects tiering from live device signals. */
    val effects: EffectsController = EffectsController(application, capability, prefs, intelligence.state, coreScope)

    /** Launches made through ROLEXM (on-device only). */
    val launchHistory: LaunchHistoryStore = LaunchHistoryStore(application, coreScope)

    val shortcuts: ShortcutRepository = ShortcutRepository(application)
    val iconPacks: IconPackManager = IconPackManager(application)

    fun start() {
        RolexLog.i(
            "ROLEXM starting: ${capability.totalMemoryMb} MB RAM, " +
                "${capability.cpuCoreCount} cores, tier ${capability.tier}"
        )

        intelligence.attach()
        themes.start()
        effects.start()

        // One-shot wall-clock alarms drive context changes; there is no periodic poll.
        coreScope.launch {
            prefs.profileSchedules.distinctUntilChanged().collect {
                runCatching { ContextScheduleManager.refresh(application, prefs) }
                    .onFailure { RolexLog.e("Could not refresh context schedule", it) }
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
     * ROLEXM is a launcher: it is the process the system keeps alive longest,
     * so it is also the process the system will ask first. Dropping caches here
     * is cheap and immediate, and is the only correct response - there is
     * nothing else a launcher can legitimately release under pressure.
     */
    fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                RolexLog.w("Severe memory pressure (level $level): clearing icon cache")
                iconCache.clear()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                RolexLog.i("Memory pressure (level $level): trimming icon cache")
                iconCache.trim(fraction = 0.5f, keep = Optimizer.ICON_CACHE_KEEP)
            }
        }
    }

    fun onLowMemory() {
        RolexLog.w("onLowMemory: clearing icon cache")
        iconCache.clear()
    }

    fun shutdown() {
        intelligence.shutdown()
    }
}
