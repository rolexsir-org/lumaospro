package com.lumaos.pro.launcher.context

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.focus.FocusRestrictionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Optional second enforcement layer for Focus Protection.
 *
 * Layer 1 intercepts launches made through LumaOS Pro. This optional service
 * may react to supported foreground-package changes, such as some notification
 * taps, Recents switches, or app handoffs, when [FocusEnforcement] says the
 * package is restricted in the active Work/Study session. This is best-effort
 * foreground enforcement, not coverage of every way an app can be opened.
 *
 * Privacy and Play policy, by construction rather than by promise:
 *
 *  - the service config (`res/xml/focus_accessibility_config.xml`) subscribes
 *    to `typeWindowStateChanged` only, with `canRetrieveWindowContent=false`;
 *  - the only field ever read is `event.packageName` — node trees are never
 *    traversed, so no text, content or input is observable even in principle;
 *  - no package name, screen content, or user activity is logged, persisted,
 *    or transmitted; a generic log line may record that an intervention was
 *    attempted;
 *  - the service is off until the user enables it in system settings after an
 *    in-app disclosure, and layer 1 keeps working without it.
 *
 * Reliability notes:
 *
 *  - event-driven, no polling: the service sleeps until Android delivers a
 *    window-state event, so idle battery cost is effectively zero;
 *  - each delivered event snapshots enforcement state fresh from DataStore,
 *    avoiding cached verdicts across process recreation or rapid context
 *    switching; Android can still stop event delivery, and layer 1 remains;
 *  - [FocusInterventionGate] allows one intervention per package per cooldown
 *    window, so a blocked app can never stack restriction screens in a loop;
 *  - the intervention requests Home first and attempts the restriction screen
 *    after a beat. If another launcher owns Home, Android may open that launcher
 *    and may refuse the restriction screen; neither outcome is guaranteed.
 *    Failed presentation is logged and never retried in a loop.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = FocusInterventionGate()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        FocusAccess.updateServiceConnection(true)
        LumaLog.i("Focus accessibility enforcement connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val foreground = event.packageName?.toString() ?: return
        scope.launch { evaluate(foreground) }
    }

    override fun onInterrupt() {
        // Stateless by design: there is nothing to tear down mid-gesture.
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        // Revocation or system reclaim: layer 2 stops receiving events and
        // layer 1 (launcher interception) carries on. Nothing to migrate.
        FocusAccess.updateServiceConnection(false)
        LumaLog.i("Focus accessibility enforcement disconnected; launcher interception remains")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        FocusAccess.updateServiceConnection(false)
        mainHandler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun evaluate(foregroundPackage: String) {
        val app = applicationContext
        // Cheap pre-filter before touching DataStore: our own windows and
        // protected packages can never be interventions.
        if (FocusSafety.isProtected(foregroundPackage, app.packageName)) return
        val prefs = (app as LumaApp).core.prefs
        val active = prefs.activeContextProfile.first()
        if (active !in ContextEngine.FOCUS_PROFILES) return
        val snapshot = FocusEnforcement.Snapshot(
            active = active,
            focusEnabled = prefs.focusEnabled(active).first(),
            restrictedPackages = FocusEnforcement.restrictedPackages(prefs.focusRestricted(active).first()),
            unlockProfile = prefs.focusUnlockProfile().first(),
            unlockUntilMillis = prefs.focusUnlockUntil().first()
        )
        if (!FocusEnforcement.shouldBlock(snapshot, foregroundPackage, app.packageName, System.currentTimeMillis())) return
        if (!gate.shouldIntervene(foregroundPackage)) return
        intervene(foregroundPackage)
    }

    private fun intervene(foregroundPackage: String) {
        val app = applicationContext
        LumaLog.i("Focus enforcement intervened on a foreground restricted app")
        runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
        mainHandler.postDelayed({
            val intent = FocusRestrictionActivity.intent(app, foregroundPackage, labelFor(foregroundPackage))
            runCatching { app.startActivity(intent) }.onFailure {
                // A background-start refusal is possible, especially when
                // another launcher owns Home. Do not retry in a loop.
                LumaLog.w("Restriction screen could not be presented after requesting Home")
            }
        }, PRESENT_DELAY_MILLIS)
    }

    private fun labelFor(packageName: String): String = runCatching {
        val pm = applicationContext.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info)?.toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName

    private companion object {
        /** Beat between Home and presenting, so the presentation is foreground-legal. */
        const val PRESENT_DELAY_MILLIS = 350L
    }
}
