package com.lumaos.pro.launcher.context

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.core.prefs.LumaPrefs
import com.lumaos.pro.launcher.launcher.HomeActivity
import com.lumaos.pro.launcher.ui.theme.ColorMath
import kotlinx.coroutines.flow.first

/** Notifications and DND are opt-in and only affect LumaOS Pro's own context alerts / system filter. */
object ContextAutomation {
    private const val CHANNEL_ID = "context_changes"
    private const val NOTIFICATION_ID = 4100

    suspend fun notifyProfileActivated(context: Context, prefs: LumaPrefs, profile: ContextProfile) {
        if (!prefs.contextNotificationsEnabled.first()) return
        if (profile !in prefs.contextNotificationProfiles.first()) return
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.context_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.context_notification_summary)
                }
            )
            val homeIntent = Intent(context, HomeActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, NOTIFICATION_ID, homeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val accentColor = (context.applicationContext as? LumaApp)
                ?.core?.themes?.theme?.value?.colors?.accent
                ?.let(ColorMath::opaque)
            val builder = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.context_notification_title, profile.title))
                .setContentText(context.getString(R.string.context_notification_body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
            if (accentColor != null) {
                builder.setColor(accentColor)
            }
            val notification = builder.build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission may be denied or revoked between checks.
        }
    }

    /** Applies the selected profile's DND preference, restoring the previous filter on exit. */
    suspend fun applyDoNotDisturb(context: Context, prefs: LumaPrefs, profile: ContextProfile) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val desired = profile in prefs.contextDndProfiles.first()
        val managed = prefs.contextDndManaged.first()
        try {
            if (!manager.isNotificationPolicyAccessGranted) return
            if (desired && !managed) {
                prefs.setContextDndRestoreFilter(manager.currentInterruptionFilter)
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                prefs.setContextDndManaged(true)
            } else if (!desired && managed) {
                val previous = prefs.contextDndRestoreFilter.first()
                // Do not undo a change the user made directly in Android Settings.
                if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    manager.setInterruptionFilter(previous)
                }
                prefs.setContextDndManaged(false)
            }
        } catch (_: SecurityException) {
            // User can revoke Notification Policy Access at any time.
        } catch (_: IllegalArgumentException) {
            // OEMs may not support every interruption filter.
        }
    }
}
