package com.lumaos.pro.launcher.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.lumaos.pro.launcher.core.util.LumaLog
import com.lumaos.pro.launcher.widgets.music.MusicBridge
import com.lumaos.pro.launcher.widgets.music.MusicNotificationListener

/**
 * Notification access: the grant, its settings route, and its recovery.
 *
 * Android gives notification content to exactly one kind of client - a
 * `NotificationListenerService` the user enables by hand in system settings. It
 * cannot be requested at runtime, cannot be granted silently, and can be revoked
 * at any moment. LumaOS Pro therefore:
 *
 *  - reuses the listener component the music widget already declared, so a user
 *    who enabled it for media controls does not have to enable a second one;
 *  - explains what the grant means before opening Android's screen (the
 *    disclosure lives in `R.string.notification_access_disclosure`);
 *  - treats "not granted" as a normal, permanent-until-changed state, not an
 *    error, and never re-prompts on a timer;
 *  - asks Android to rebind the listener when access *is* granted but the service
 *    is not connected (after an app update or an OEM reclaim), which is the
 *    supported recovery path.
 */
object NotificationAccess {

    /** The one component the user grants. Shared with the music widget. */
    fun component(context: Context): ComponentName =
        ComponentName(context.applicationContext, MusicNotificationListener::class.java)

    /**
     * True while Android reports the grant.
     *
     * Delegates to the existing check in [MusicBridge] rather than adding a second
     * implementation that could disagree with the first.
     */
    fun isGranted(context: Context): Boolean = MusicBridge.hasAccess(context)

    /** Android's notification-access screen. Resolved before it is offered. */
    fun settingsIntent(context: Context): Intent? {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val resolvable = runCatching {
            intent.resolveActivity(context.packageManager) != null
        }.getOrDefault(false)
        if (resolvable) return intent
        // OEM builds occasionally ship without the screen; fall back to the
        // general security settings rather than offering a dead button.
        val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS)
        return if (runCatching { fallback.resolveActivity(context.packageManager) != null }.getOrDefault(false)) {
            fallback
        } else null
    }

    /**
     * Asks Android to reconnect a granted listener.
     *
     * Only called when the grant exists but the service is not bound; it is the
     * documented way to recover without asking the user to toggle the switch
     * twice. A refusal is logged, never thrown.
     */
    fun requestRebindIfGranted(context: Context): Boolean {
        if (!isGranted(context)) return false
        return runCatching {
            NotificationListenerService.requestRebind(component(context))
            true
        }.getOrElse {
            LumaLog.w("The notification listener could not be rebound")
            false
        }
    }

    /** The shade's own view of the grant, including the transient disconnected state. */
    fun state(context: Context, listenerConnected: Boolean): NotificationAccessState = when {
        !isGranted(context) -> NotificationAccessState.NOT_GRANTED
        !listenerConnected -> NotificationAccessState.DISCONNECTED
        else -> NotificationAccessState.GRANTED
    }
}
