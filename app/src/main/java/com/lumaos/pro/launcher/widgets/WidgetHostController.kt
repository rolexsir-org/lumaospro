package com.lumaos.pro.launcher.widgets

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
import com.lumaos.pro.launcher.core.util.LumaLog

/** Result of querying the installed widget providers. */
sealed class ProviderLoad {
    data class Loaded(val providers: List<AppWidgetProviderInfo>) : ProviderLoad()
    data object Failed : ProviderLoad()
}

/**
 * LumaOS Pro's AppWidgetHost.
 *
 * Lifecycle: the host listens for widget updates only while the home screen
 * is started (startListening/stopListening from onStart/onStop) - a launcher
 * that keeps listening in the background wakes every widget provider for
 * nothing. Host views are cached per widget id so RecyclerView recycling never
 * re-inflates a widget.
 *
 * Binding uses the platform flow: bindAppWidgetIdIfAllowed, and if the user
 * has not yet allowed LumaOS Pro to bind widgets, the system ACTION_APPWIDGET_BIND
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
        runCatching { host.startListening() }.onFailure { LumaLog.w("Widget host could not start listening") }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
            .onFailure { LumaLog.e("Widget host could not stop listening", it) }
    }

    /** Queries the providers the current user can bind, distinguishing "none" from a failed query. */
    fun providersState(): ProviderLoad =
        runCatching { manager.getInstalledProvidersForProfile(Process.myUserHandle()) }
            .fold(
                onSuccess = { ProviderLoad.Loaded(it) },
                onFailure = {
                    LumaLog.e("Widget provider query failed", it)
                    ProviderLoad.Failed
                }
            )

    fun providers(): List<AppWidgetProviderInfo> =
        (providersState() as? ProviderLoad.Loaded)?.providers ?: emptyList()

    fun infoFor(appWidgetId: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(appWidgetId) }
            .onFailure { LumaLog.e("Widget information could not be read", it) }
            .getOrNull()

    /**
     * True when Android still holds a binding for [appWidgetId] and its provider
     * is installed. A removed app or a dead binding returns false so the layout
     * layer can reconcile the widget out without leaving an unreachable card.
     */
    fun isWidgetValid(appWidgetId: Int): Boolean = infoFor(appWidgetId) != null

    /** Host ids whose bindings are still alive. */
    fun validWidgetIds(): Set<Int> {
        val hosted = runCatching { host.appWidgetIds }.getOrNull() ?: return emptySet()
        return hosted.filter { isWidgetValid(it) }.toHashSet()
    }

    /** Host view for a bound widget, or null if its provider is gone. */
    fun viewFor(context: Context, appWidgetId: Int): AppWidgetHostView? {
        views[appWidgetId]?.let { return it }
        val info = infoFor(appWidgetId) ?: return null
        return runCatching { host.createView(context, appWidgetId, info) }
            .onFailure { LumaLog.w("Widget $appWidgetId could not be inflated") }
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
        LumaLog.e("Widget configuration could not start", it)
        false
    }

    /**
     * Re-opens the configuration activity of an already-bound widget when the
     * provider declares itself reconfigurable. The widget keeps its id and
     * placement regardless of the outcome.
     */
    fun startReconfiguration(activity: Activity, appWidgetId: Int, requestCode: Int): Boolean {
        val info = infoFor(appWidgetId) ?: return false
        if (info.configure == null) return false
        return runCatching {
            host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, requestCode, null)
            true
        }.getOrElse {
            LumaLog.e("Widget reconfiguration could not start", it)
            false
        }
    }

    fun delete(appWidgetId: Int) {
        views.remove(appWidgetId)
        runCatching { host.deleteAppWidgetId(appWidgetId) }
            .onFailure { LumaLog.e("Widget id could not be released", it) }
    }

    /**
     * Releases only host ids that no saved layout or in-flight add references.
     * An unavailable host is not an empty host; let the activity retry later.
     */
    fun releaseOrphans(referenced: Set<Int>): Boolean {
        val ids = try {
            host.appWidgetIds
        } catch (e: RuntimeException) {
            LumaLog.e("Widget host ids could not be read", e)
            return false
        }
        for (id in ids) if (id !in referenced) delete(id)
        return true
    }

    /** Translates platform provider metadata into the pure, row-based capability model. */
    fun capabilities(info: AppWidgetProviderInfo, rowHeightPx: Int): WidgetCapabilities {
        val minResizeHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.minResizeHeight else 0
        val maxResizeHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.maxResizeHeight else 0
        val targetCellHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.targetCellHeight else 0
        // AppWidgetProviderInfo has no public minCellHeight field. The provider's
        // minimum is already supplied as minResizeHeightPx; WidgetPlan converts
        // that pixel minimum into grid rows, so no unsupported platform field is
        // referenced here.
        val minCellHeight = 0
        val features = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.widgetFeatures else 0
        return WidgetPlan.capabilities(
            ProviderFacts(
                resizeMode = info.resizeMode,
                minHeightPx = info.minHeight,
                minResizeHeightPx = minResizeHeight,
                maxResizeHeightPx = maxResizeHeight,
                targetCellHeight = targetCellHeight,
                minCellHeight = minCellHeight,
                hasConfigure = info.configure != null,
                widgetFeatures = features,
                rowHeightPx = rowHeightPx
            )
        )
    }

    /** Rows a provider needs at minimum, given the grid row height in pixels. */
    fun defaultRows(info: AppWidgetProviderInfo, rowHeightPx: Int): Int {
        if (rowHeightPx <= 0) return 2
        return capabilities(info, rowHeightPx).initialRows
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
