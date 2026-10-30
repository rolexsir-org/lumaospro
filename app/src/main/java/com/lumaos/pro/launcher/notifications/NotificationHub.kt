package com.lumaos.pro.launcher.notifications

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lumaos.pro.launcher.core.util.LumaLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Everything the shade renders: access state, cards, and in-flight replies. */
data class NotificationShadeState(
    val access: NotificationAccessState = NotificationAccessState.NOT_GRANTED,
    val cards: List<NotificationCardModel> = emptyList(),
    /**
     * Keys whose reply Android accepted but whose app has not yet updated or
     * removed the notification. The card shows "reply sent" for these; the message
     * itself is never fabricated or stored by LumaOS Pro.
     */
    val awaitingReply: Set<String> = emptySet()
)

/**
 * The single source of truth for the device's live notifications.
 *
 * Android delivers notifications to the bound [NotificationListenerService]
 * (`.widgets.music.MusicNotificationListener`, the component the music widget
 * already uses - one grant powers both features). That service forwards events
 * here; this object owns the projection into [NotificationCardModel] and every
 * action the shade can take: open, dismiss, reply.
 *
 * Guarantees:
 *  - **Event-driven.** Updates arrive from `onNotificationPosted` /
 *    `onNotificationRemoved` / `onListenerConnected`. There is no polling loop.
 *  - **Nothing is persisted.** Cards live in memory and disappear with the
 *    notification. Reply text is handed straight to the posting app's
 *    `PendingIntent` and is never written to disk or logged.
 *  - **No leaks.** The service reference is cleared on disconnect and destroy, and
 *    entries are bounded.
 *  - **Graceful degradation.** A revoked grant, a disconnected listener or a
 *    cancelled `PendingIntent` becomes a visible state, never a crash and never a
 *    fake success.
 */
object NotificationHub {

    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(NotificationShadeState())
    val state: StateFlow<NotificationShadeState> = _state

    private val entries = LinkedHashMap<String, ExtractedNotification>()
    private var listener: NotificationListenerService? = null

    @Volatile
    private var listenerConnected = false

    @Volatile
    private var application: Context? = null

    /** Set by the listener when Android binds it. */
    fun onListenerConnected(service: NotificationListenerService) {
        application = service.applicationContext
        listener = service
        listenerConnected = true
        val active = runCatching { service.activeNotifications }.getOrElse {
            LumaLog.w("Active notifications could not be read")
            null
        }
        synchronized(entries) {
            entries.clear()
            active?.forEach { sbn -> put(sbn) }
        }
        publish(access = NotificationAccessState.GRANTED)
    }

    /**
     * Android unbinds the listener when access is revoked, when the app is
     * updated, or when the system reclaims it. The shade must say so rather than
     * show stale cards.
     */
    fun onListenerDisconnected() {
        listener = null
        listenerConnected = false
        synchronized(entries) { entries.clear() }
        publish(access = NotificationAccessState.DISCONNECTED)
    }

    /** The service is going away; drop every platform reference. */
    fun onListenerDestroyed() {
        listener = null
        listenerConnected = false
        application = null
        synchronized(entries) { entries.clear() }
        publish(access = NotificationAccessState.NOT_GRANTED)
    }

    fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val context = application ?: return
        val extracted = NotificationExtractor.extract(context, sbn) ?: return
        synchronized(entries) {
            entries[sbn.key] = extracted
            trimLocked()
        }
        // A re-posted notification supersedes any "reply sent" hint for it.
        publish(replyClearedFor = sbn.key)
    }

    fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        synchronized(entries) { entries.remove(sbn.key) }
        publish(replyClearedFor = sbn.key)
    }

    /**
     * Re-reads everything Android currently holds.
     *
     * Used when the shade opens after the listener was already connected: the
     * notifications posted while no UI was listening are still there, and this is
     * the supported way to see them.
     */
    fun resync() {
        val service = listener ?: return
        val active = runCatching { service.activeNotifications }.getOrElse {
            LumaLog.w("Active notifications could not be re-read")
            return
        }
        synchronized(entries) {
            entries.clear()
            active.forEach { sbn -> put(sbn) }
        }
        publish(access = NotificationAccessState.GRANTED)
    }

    private fun put(sbn: StatusBarNotification) {
        val context = application ?: return
        NotificationExtractor.extract(context, sbn)?.let { entries[sbn.key] = it }
    }

    private fun trimLocked() {
        while (entries.size > MAX_ENTRIES) {
            val oldest = entries.keys.firstOrNull() ?: return
            entries.remove(oldest)
        }
    }

    /** Called by the shade when it opens, so a revoked grant is shown at once. */
    fun refreshAccessState(context: Context) {
        application = context.applicationContext
        val access = when {
            !NotificationAccess.isGranted(context) -> NotificationAccessState.NOT_GRANTED
            !listenerConnected -> NotificationAccessState.DISCONNECTED
            else -> NotificationAccessState.GRANTED
        }
        if (access == NotificationAccessState.GRANTED) resync()
        publish(access = access)
    }

    // ── Actions ────────────────────────────────────────────────────────────

    /**
     * Opens the notification through the posting app's own content intent.
     *
     * The intent belongs to that app, so Android applies its identity, task and
     * permissions - LumaOS Pro does not synthesise a launch intent.
     */
    fun open(context: Context, key: String): Boolean {
        val intent = entry(key)?.handles?.contentIntent ?: return false
        return send(context, intent, fillIn = null)
    }

    /**
     * Fires one of the app's own non-reply actions (mark as read, mute, snooze)
     * through its `PendingIntent`. Only actions the notification really carries can
     * be fired; the shade never invents one.
     */
    fun sendAction(context: Context, key: String, actionIndex: Int): Boolean {
        val extracted = entry(key) ?: return false
        val intent = extracted.handles.actionIntents.getOrNull(actionIndex) ?: return false
        return send(context, intent, fillIn = null)
    }

    /** Dismisses one notification with Android's own API. False when refused. */
    fun dismiss(key: String): Boolean {
        val service = listener ?: return false
        val exists = synchronized(entries) { entries.containsKey(key) }
        if (!exists) return false
        val ok = runCatching { service.cancelNotification(key) }.getOrElse {
            LumaLog.w("A notification could not be dismissed")
            return false
        }
        if (ok) {
            synchronized(entries) { entries.remove(key) }
            publish(replyClearedFor = key)
        }
        return ok
    }

    /** Dismisses everything Android lets a listener dismiss. */
    fun dismissAll(): Boolean {
        val service = listener ?: return false
        val ok = runCatching { service.cancelAllNotifications() }.getOrElse {
            LumaLog.w("Notifications could not be dismissed")
            return false
        }
        if (ok) {
            synchronized(entries) { entries.clear() }
            publish()
        }
        return ok
    }

    /**
     * Sends a real inline reply through the action the originating app exposed.
     *
     * The text is placed in the action's own `RemoteInput` results and delivered
     * with that action's `PendingIntent`, exactly as the system shade does. No
     * alternative channel is used and nothing is stored: if `send` throws, the
     * caller gets [ReplyOutcome.ActionExpired] or [ReplyOutcome.Failed] and the UI
     * keeps the draft so the user can retry.
     */
    fun reply(context: Context, key: String, actionIndex: Int, text: String): ReplyOutcome {
        if (text.isBlank()) return ReplyOutcome.Failed("empty")
        val extracted = entry(key) ?: return ReplyOutcome.ActionExpired
        val action = extracted.raw.actions.firstOrNull { it.index == actionIndex && it.isReply }
            ?: return ReplyOutcome.Unsupported
        val replyKey = action.replyKey ?: return ReplyOutcome.Unsupported
        val inputs = extracted.handles.actionRemoteInputs.getOrNull(actionIndex)
            ?: return ReplyOutcome.Unsupported
        val intent = extracted.handles.actionIntents.getOrNull(actionIndex)
            ?: return ReplyOutcome.ActionExpired

        val fillIn = android.content.Intent()
        val results = Bundle().apply { putString(replyKey, text) }
        val attached = runCatching { RemoteInput.addResultsToIntent(inputs, fillIn, results) }.isSuccess
        if (!attached) {
            LumaLog.w("Reply results could not be attached to the action")
            return ReplyOutcome.Failed("remote-input")
        }

        return if (send(context, intent, fillIn)) {
            publish(replySetFor = key)
            ReplyOutcome.Submitted
        } else {
            ReplyOutcome.ActionExpired
        }
    }

    /** The app's own small icon, for packages the launcher's app list does not cover. */
    fun fallbackIcon(key: String): Drawable? = entry(key)?.fallbackIcon

    private fun entry(key: String): ExtractedNotification? = synchronized(entries) { entries[key] }

    private fun send(context: Context, intent: PendingIntent, fillIn: android.content.Intent?): Boolean =
        runCatching {
            intent.send(context, 0, fillIn)
            true
        }.getOrElse {
            // CanceledException means the posting app withdrew the action; any
            // other failure is reported the same way - never as a success.
            LumaLog.w("A notification intent could not be sent")
            false
        }

    // ── State publication ──────────────────────────────────────────────────

    private fun publish(
        access: NotificationAccessState? = null,
        replySetFor: String? = null,
        replyClearedFor: String? = null
    ) {
        val cards: List<NotificationCardModel>
        val effectiveAccess: NotificationAccessState
        val awaiting: Set<String>
        synchronized(entries) {
            cards = NotificationPolicy.visibleCards(entries.values.map { it.raw })
            effectiveAccess = access ?: if (listenerConnected) {
                NotificationAccessState.GRANTED
            } else {
                _state.value.access
            }
            val current = _state.value.awaitingReply
            val candidates = when {
                replySetFor != null -> current + replySetFor
                replyClearedFor != null -> current - replyClearedFor
                else -> current
            }
            // A hint only survives while its notification does.
            awaiting = candidates.filter { entries.containsKey(it) }.toSet()
        }
        val next = NotificationShadeState(effectiveAccess, cards, awaiting)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _state.value = next
        } else {
            main.post { _state.value = next }
        }
    }

    private const val MAX_ENTRIES = 60
}
