package com.lumaos.pro.launcher.core.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import com.lumaos.pro.launcher.core.util.LumaLog

/** An app shortcut published by another app (static or dynamic). */
data class AppShortcut(
    val packageName: String,
    val id: String,
    val label: String,
    val rank: Int,
    val info: ShortcutInfo
)

/**
 * App shortcuts through LauncherApps (API 25+).
 *
 * Android only lets the *default* launcher read and start other apps'
 * shortcuts (LauncherApps.hasShortcutHostPermission). When LumaOS Pro is not the
 * default home app, shortcuts are simply absent - they are never faked.
 */
class ShortcutRepository(context: Context) {

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val user = Process.myUserHandle()

    fun hasAccess(): Boolean = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    /** Up to [limit] shortcuts for one app, in the app's own rank order. */
    fun shortcutsFor(packageName: String, limit: Int = 4): List<AppShortcut> =
        query(packageName).sortedWith(compareBy<AppShortcut> { if (it.info.isDynamic) 1 else 0 }.thenBy { it.rank })
            .take(limit)

    /** Every static and dynamic shortcut, for search. Off the main thread. */
    fun allShortcuts(): List<AppShortcut> = query(null)

    private fun query(packageName: String?): List<AppShortcut> {
        if (!hasAccess()) return emptyList()
        val q = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            )
            if (packageName != null) setPackage(packageName)
        }
        return runCatching { launcherApps.getShortcuts(q, user).orEmpty() }
            .onFailure { LumaLog.w("Shortcut query failed: ${it.javaClass.simpleName}") }
            .getOrDefault(emptyList())
            .filter { it.isEnabled }
            .map { info ->
                AppShortcut(
                    packageName = info.`package`,
                    id = info.id,
                    label = (info.shortLabel ?: info.longLabel ?: info.id).toString(),
                    rank = info.rank,
                    info = info
                )
            }
    }

    fun icon(shortcut: AppShortcut, density: Int): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(shortcut.info, density) }.getOrNull()

    fun launch(shortcut: AppShortcut, bounds: Rect?, options: Bundle?): Boolean = try {
        launcherApps.startShortcut(shortcut.packageName, shortcut.id, bounds, options, user)
        true
    } catch (e: RuntimeException) {
        LumaLog.w("Shortcut launch failed for ${shortcut.packageName}")
        false
    }
}
