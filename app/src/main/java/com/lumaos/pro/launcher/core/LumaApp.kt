package com.lumaos.pro.launcher.core

import android.app.Application
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.intelligence.StartupTracker

/**
 * LumaOS Pro's application object.
 *
 * Kept deliberately thin. Everything expensive (app list loading, package
 * queries, database opening) is deferred until the home screen actually needs
 * it, because this code runs on every single process start - including the
 * starts triggered by a package install broadcast, where the user sees nothing.
 */
class LumaApp : Application() {

    lateinit var core: LumaCore
        private set

    override fun onCreate() {
        // First statement: the startup measurement is anchored here.
        StartupTracker.markProcessInit()
        super.onCreate()
        core = LumaCore(this)
        core.start()
        LumaLog.d("Application created")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::core.isInitialized) core.themes.onConfiguration(newConfig)
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
