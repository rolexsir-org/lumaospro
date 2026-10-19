package com.rolexm.launcher.core

import android.app.Application
import com.rolexm.launcher.core.util.RolexLog
import com.rolexm.launcher.intelligence.StartupTracker

/**
 * ROLEXM's application object.
 *
 * Kept deliberately thin. Everything expensive (app list loading, package
 * queries, database opening) is deferred until the home screen actually needs
 * it, because this code runs on every single process start - including the
 * starts triggered by a package install broadcast, where the user sees nothing.
 */
class RolexApp : Application() {

    lateinit var core: RolexCore
        private set

    override fun onCreate() {
        // First statement: the startup measurement is anchored here.
        StartupTracker.markProcessInit()
        super.onCreate()
        core = RolexCore(this)
        core.start()
        RolexLog.d("Application created")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::core.isInitialized) core.onTrimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::core.isInitialized) core.onLowMemory()
    }
}
