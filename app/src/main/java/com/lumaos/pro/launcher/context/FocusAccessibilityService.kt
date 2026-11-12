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
 * Layer 1 intercepts launches made inside LumaOS Pro. This service covers the
 * launches LumaOS Pro never sees — notification taps, Recents switches,
 * external apps' intents — by observing which package enters the foreground
 * and, when [FocusEnforcement] says the package is restricted in the active
 * Work/Study session, sending the user to the native restriction experience.
 *
 * Privacy and Play policy, by construction rather than by promise:
 *
 *  - the service config (`res/xml/focus_accessibility_config.xml`) subscribes
 *    to `typeWindowStateChanged` only, with `canRetrieveWindowContent=false`;
 *  - the only field ever read is `event.packageName` — node trees are never
 *    traversed, so no text, content or input is observable even in principle;
 *  - no accessibility data is logged, persisted, or transmitted; the single
 *    log line records that an intervention happened, not what was on screen;
 *  - the service is off until the user enables it in system settings after an
 *    in-app disclosure, and layer 1 keeps working without it.
 *
 * Reliability notes:
 *
 *  - event-driven, no polling: the service sleeps until Android delivers a
 *    window-state event, so idle battery cost is effectively zero;
 *  - every event snapshots enforcement state fresh from DataStore, so process
 *    recreation, reboot, revocation and rapid context switching cannot strand
 *    stale decisions — revocation simply stops the events and layer 1 remains;
 *  - [FocusInterventionGate] allows one intervention per package per cooldown
 *    window, so a blocked app can never stack restriction screens in a loop;
 *  - the intervention presses Home first and presents the restriction screen
 *    after a beat: that ordering is what keeps the presentation inside
 *    Android's background-activity-start rules when LumaOS Pro is the default
 *    launcher. If the start is refused, the user is still safely at Home and
 *    the failure is logged, never retried in a loop.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = FocusInterventionGate()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
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
        LumaLog.i("Focus accessibility enforcement disconnected; launcher interception remains")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
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
                // Typically a background-start refusal when LumaOS Pro is not
                // the default launcher: the user is already safely at Home.
                LumaLog.w("Restriction screen could not be presented after Home")
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
