package com.rolexm.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.SizeF
import com.rolexm.launcher.core.util.RolexLog
import kotlin.math.ceil

/**
 * ROLEXM's AppWidgetHost.
 *
 * Lifecycle: the host listens for widget updates only while the home screen
 * is started (startListening/stopListening from onStart/onStop) - a launcher
 * that keeps listening in the background wakes every widget provider for
 * nothing. Host views are cached per widget id so RecyclerView recycling never
 * re-inflates a widget.
 *
 * Binding uses the platform flow: bindAppWidgetIdIfAllowed, and if the user
 * has not yet allowed ROLEXM to bind widgets, the system ACTION_APPWIDGET_BIND
 * consent screen; then the provider's configuration activity when it has one.
 * No special permission is declared; BIND_APPWIDGET stays with the system.
 */
class WidgetHostController(context: Context) {

    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)
    private val views = HashMap<Int, AppWidgetHostView>()

    /** A widget that is being added: id allocated, waiting for consent/configuration. */
    data class Pending(val appWidgetId: Int, val info: AppWidgetProviderInfo)

    fun startListening() {
        runCatching { host.startListening() }.onFailure { RolexLog.w("Widget host could not start listening") }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    fun providers(): List<AppWidgetProviderInfo> = runCatching {
        manager.getInstalledProvidersForProfile(Process.myUserHandle())
    }.getOrDefault(emptyList())

    fun infoFor(appWidgetId: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    /** Host view for a bound widget, or null if its provider is gone. */
    fun viewFor(context: Context, appWidgetId: Int): AppWidgetHostView? {
        views[appWidgetId]?.let { return it }
        val info = infoFor(appWidgetId) ?: return null
        return runCatching { host.createView(context, appWidgetId, info) }
            .onFailure { RolexLog.w("Widget $appWidgetId could not be inflated") }
            .getOrNull()?.also { views[appWidgetId] = it }
    }

    /** Starts adding [info]. Returns an Intent to launch for consent, or null if bound already. */
    fun begin(info: AppWidgetProviderInfo): Pair<Pending, Intent?> {
        val id = host.allocateAppWidgetId()
        val pending = Pending(id, info)
        val bound = runCatching {
            manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        }.getOrDefault(false)
        if (bound) return pending to null
        val consent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        }
        return pending to consent
    }

    fun needsConfiguration(pending: Pending): Boolean = pending.info.configure != null

    /** Launches the provider's configuration screen; the result arrives in onActivityResult(requestCode). */
    fun startConfiguration(activity: Activity, pending: Pending, requestCode: Int): Boolean = runCatching {
        host.startAppWidgetConfigureActivityForResult(activity, pending.appWidgetId, 0, requestCode, null)
        true
    }.getOrElse {
        RolexLog.w("Widget configuration could not start")
        false
    }

    fun delete(appWidgetId: Int) {
        views.remove(appWidgetId)
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    /** Releases ids the host holds that no layout references (aborted adds, old installs). */
    fun releaseOrphans(referenced: Set<Int>) {
        val ids = runCatching { host.appWidgetIds }.getOrNull() ?: return
        for (id in ids) if (id !in referenced) delete(id)
    }

    /** Rows a provider needs at minimum, given the grid row height in pixels. */
    fun defaultRows(info: AppWidgetProviderInfo, rowHeightPx: Int): Int {
        if (rowHeightPx <= 0) return 2
        return ceil(info.minHeight / rowHeightPx.toDouble()).toInt().coerceIn(1, 4)
    }

    /** Tells the provider the size it is drawn at, so it can choose its layout. */
    fun reportSize(appWidgetId: Int, widthDp: Float, heightDp: Float) {
        val view = views[appWidgetId] ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
            } else {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
            }
        }
    }

    fun clearViews() {
        views.clear()
    }

    companion object {
        const val HOST_ID = 0x524D // "RM"
    }
}
