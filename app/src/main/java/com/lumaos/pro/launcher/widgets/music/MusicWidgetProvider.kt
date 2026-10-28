package com.lumaos.pro.launcher.widgets.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

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

/** Only Android can bind this service. We do not inspect notification events or content. */
class MusicNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MusicBridge.refresh(applicationContext)
    }

    override fun onListenerDisconnected() {
        MusicBridge.listenerDisconnected(applicationContext)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        MusicBridge.listenerDisconnected(applicationContext)
        super.onDestroy()
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
