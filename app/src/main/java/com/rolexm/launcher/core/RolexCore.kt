package com.rolexm.launcher.core

import android.app.Application
import android.content.ComponentCallbacks2
import com.rolexm.launcher.core.apps.AppRepository
import com.rolexm.launcher.core.apps.IconCache
import com.rolexm.launcher.core.prefs.RolexPrefs
import com.rolexm.launcher.core.util.RolexLog
import com.rolexm.launcher.diagnostics.DiagnosticsRepository
import com.rolexm.launcher.diagnostics.DiagnosticsStore
import com.rolexm.launcher.intelligence.DeviceCapability
import com.rolexm.launcher.intelligence.DeviceCapabilityProvider
import com.rolexm.launcher.intelligence.IntelligenceEngine
import com.rolexm.launcher.intelligence.Profile
import com.rolexm.launcher.intelligence.Profiles
import com.rolexm.launcher.optimizer.Optimizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
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

    fun start() {
        RolexLog.i(
            "ROLEXM starting: ${capability.totalMemoryMb} MB RAM, " +
                "${capability.cpuCoreCount} cores, tier ${capability.tier}"
        )

        intelligence.attach()

        // Keep the icon cache ceiling in step with whichever profile is active.
        // The engine resizes on profile change; this catches the case where the
        // engine has not yet attached when a profile is resolved.
        coreScope.launch {
            intelligence.state
                .distinctUntilChanged { a, b -> a.profile.id == b.profile.id }
                .collect { applyProfile(it.profile) }
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
