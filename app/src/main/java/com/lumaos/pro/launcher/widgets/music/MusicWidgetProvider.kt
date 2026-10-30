package com.lumaos.pro.launcher.widgets.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lumaos.pro.launcher.notifications.NotificationHub

/** A normal Android widget, hosted by Luma's existing AppWidgetHost or other home apps. */
class MusicWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        MusicBridge.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        MusicBridge.refresh(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        // No per-ID data: Android remaps widget IDs and the next refresh rediscovers sessions.
        MusicBridge.refresh(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        MusicBridge.refresh(context)
    }

    override fun onDisabled(context: Context) {
        MusicBridge.stop()
    }
}

/**
 * Only Android can bind this service.
 *
 * One grant powers two features, and there is deliberately only one listener
 * component: the music widget reads active media sessions through it, and the
 * pull-down shade renders the device's notifications through it. Notification
 * events are handed to [NotificationHub], which keeps them in memory only -
 * nothing is persisted, uploaded or written to a log, and reply text goes
 * straight back to the posting app's own `PendingIntent`.
 *
 * The overrides take nullable parameters on purpose. Android declares these
 * arguments as platform types, and a non-null Kotlin parameter would insert an
 * intrinsic null check that turns an odd OEM delivery into a crash inside the
 * launcher's own process; the hub ignores null instead.
 */
class MusicNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MusicBridge.refresh(applicationContext)
        NotificationHub.onListenerConnected(this)
    }

    override fun onListenerDisconnected() {
        NotificationHub.onListenerDisconnected()
        MusicBridge.listenerDisconnected(applicationContext)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        NotificationHub.onListenerDestroyed()
        MusicBridge.listenerDisconnected(applicationContext)
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { super.onNotificationPosted(it) }
        NotificationHub.onNotificationPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { super.onNotificationRemoved(it) }
        NotificationHub.onNotificationRemoved(sbn)
    }
}

/** Non-exported: third-party apps cannot forge Luma's playback-control broadcasts. */
class MusicWidgetControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
            android.content.ComponentName(context, MusicWidgetProvider::class.java)
        )
        if (widgetId !in ids) return
        val command = MusicCommand.entries.firstOrNull { it.name == intent.action } ?: return
        MusicBridge.command(context, command)
    }
}
