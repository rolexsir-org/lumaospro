package com.lumaos.pro.launcher.context

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Accessibility grant behind Focus Protection's second enforcement layer.
 *
 * Android hands foreground-window events to exactly one kind of client — an
 * `AccessibilityService` the user enables by hand in system settings. It
 * cannot be requested at runtime, cannot be granted silently, and can be
 * revoked at any moment (or suspended by the system under battery savers and
 * OEM task managers). LumaOS Pro therefore:
 *
 *  - keeps layer 1 (launcher interception) working with no grant at all, so
 *    "not granted" is a supported configuration, never an error;
 *  - explains what the grant means, in plain language, before opening
 *    Android's screen (the disclosure lives in
 *    `R.string.focus_access_disclosure` and in the service's own
 *    `android:description`, which the system shows on its toggle screen);
 *  - reads only `event.packageName` from `TYPE_WINDOW_STATE_CHANGED` events:
 *    window content retrieval is disabled in the service config, node trees
 *    are never traversed, no text or input is collected, and nothing leaves
 *    the device;
 *  - degrades to layer 1 the moment the grant is gone: enforcement decisions
 *    are snapshotted per event, so there is no stale state to clear and no
 *    restart dance to perform.
 */
object FocusAccess {

    /** The one component the user grants. */
    fun component(context: Context): ComponentName =
        ComponentName(context.applicationContext, FocusAccessibilityService::class.java)

    /**
     * True while Android reports the grant: the accessibility framework is on
     * and this service is in the enabled-services list.
     */
    fun isGranted(context: Context): Boolean {
        val resolver = context.applicationContext.contentResolver
        val frameworkOn = runCatching {
            Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        }.getOrDefault(false)
        if (!frameworkOn) return false
        val list = runCatching {
            Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        }.getOrDefault("")
        val component = component(context)
        return isServiceListed(list, component.flattenToString()) ||
            isServiceListed(list, component.flattenToShortString())
    }

    /**
     * Pure parse of the colon-separated enabled-services list. Split out so
     * the matching (exact component, either flattened form) is unit tested.
     */
    fun isServiceListed(list: String, flattenedComponent: String): Boolean {
        if (list.isBlank() || flattenedComponent.isBlank()) return false
        return list.split(':').any { it.equals(flattenedComponent, ignoreCase = true) }
    }

    /** Android's accessibility screen. Resolved before it is offered. */
    fun settingsIntent(context: Context): Intent? {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        if (resolves(context, intent)) return intent
        // OEM builds occasionally move things; fall back to general settings
        // rather than offering a dead button.
        val fallback = Intent(Settings.ACTION_SETTINGS)
        return if (resolves(context, fallback)) fallback else null
    }

    private fun resolves(context: Context, intent: Intent): Boolean = runCatching {
        intent.resolveActivity(context.packageManager) != null
    }.getOrDefault(false)
}
