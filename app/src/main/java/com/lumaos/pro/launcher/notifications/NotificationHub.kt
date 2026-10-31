package com.lumaos.pro.launcher.notifications

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lumaos.pro.launcher.core.util.LumaLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

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

    /**
     * Extraction runs here, not on the main thread.
     *
     * Android delivers listener callbacks on the main thread, and reading one
     * notification means a `PackageManager` label lookup plus a small-icon decode.
     * A connect or a resync can carry dozens of notifications at once, which on a
     * launcher process is exactly the kind of work that drops frames on the home
     * screen. One thread keeps submission order, so a post and the removal that
     * follows it can never be applied the wrong way round.
     */
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "luma-notifications").apply { isDaemon = true }
    }

    private val _state = MutableStateFlow(NotificationShadeState())
    val state: StateFlow<NotificationShadeState> = _state

    private val entries = LinkedHashMap<String, ExtractedNotification>()
    @Volatile
    private var listener: NotificationListenerService? = null
    // Guarded by entries. Invalidates work from an earlier listener connection.
    private var connectionGeneration = 0L

    @Volatile
    private var listenerConnected = false

    @Volatile
    private var application: Context? = null

    /** Set by the listener when Android binds it. */
    fun onListenerConnected(service: NotificationListenerService) {
        val generation = synchronized(entries) {
            application = service.applicationContext
            listener = service
            listenerConnected = true
            connectionGeneration++
            entries.clear()
            publish(access = NotificationAccessState.GRANTED)
            connectionGeneration
        }
        enqueueSnapshot(service, generation)
    }

    /** Drop old work and cards as soon as the listener disconnects. */
    fun onListenerDisconnected() = disconnect(NotificationAccessState.DISCONNECTED, false)

    /** The service is going away; drop every platform reference. */
    fun onListenerDestroyed() = disconnect(NotificationAccessState.NOT_GRANTED, true)

    private fun disconnect(access: NotificationAccessState, clearApplication: Boolean) {
        synchronized(entries) {
            connectionGeneration++
            listener = null
            listenerConnected = false
            if (clearApplication) application = null
            entries.clear()
            publish(access = access)
        }
    }

    fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val context: Context
        val generation: Long
        synchronized(entries) {
            if (!listenerConnected) return
            context = application ?: return
            generation = connectionGeneration
        }
        worker.execute {
            if (synchronized(entries) { !listenerConnected || generation != connectionGeneration }) return@execute
            val extracted = extractSafely(context, sbn) ?: return@execute
            synchronized(entries) {
                if (!listenerConnected || generation != connectionGeneration) return@synchronized
                entries[sbn.key] = extracted
                trimLocked()
                publish(replyClearedFor = sbn.key)
            }
        }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val key = sbn.key
        val generation = synchronized(entries) {
            if (!listenerConnected) return
            connectionGeneration
        }
        // Use the same ordered worker as posts; otherwise a queued post can
        // resurrect a notification already removed on the main thread.
        worker.execute {
            synchronized(entries) {
                if (!listenerConnected || generation != connectionGeneration) return@synchronized
                entries.remove(key)
                publish(replyClearedFor = key)
            }
        }
    }

    /** Re-reads the live snapshot without blocking the UI or action lookups. */
    fun resync() {
        val service: NotificationListenerService
        val generation: Long
        synchronized(entries) {
            service = listener ?: return
            if (!listenerConnected) return
            generation = connectionGeneration
        }
        enqueueSnapshot(service, generation)
    }

    private fun enqueueSnapshot(service: NotificationListenerService, generation: Long) {
        val context = service.applicationContext
        worker.execute {
            if (synchronized(entries) { !listenerConnected || generation != connectionGeneration }) return@execute
            val active = try {
                service.activeNotifications ?: run {
                    LumaLog.w("Active notifications unavailable; retaining previous snapshot")
                    return@execute
                }
            } catch (e: RuntimeException) {
                LumaLog.e("Active notifications could not be read", e)
                return@execute
            }
            // Package and icon extraction can be expensive. Never hold the
            // entries lock while calling into Android services.
            val extracted = LinkedHashMap<String, ExtractedNotification>()
            active.forEach { sbn ->
                extractSafely(context, sbn)?.let {
                    extracted[sbn.key] = it
                    if (extracted.size > MAX_ENTRIES) extracted.remove(extracted.keys.first())
                }
            }
            synchronized(entries) {
                if (!listenerConnected || generation != connectionGeneration) return@synchronized
                entries.clear()
                entries.putAll(extracted)
                trimLocked()
                publish()
            }
        }
    }

    private fun extractSafely(context: Context, sbn: StatusBarNotification): ExtractedNotification? =
        try {
            NotificationExtractor.extract(context, sbn)
        } catch (e: RuntimeException) {
            // Extras are supplied by other apps. Do not log their contents.
            LumaLog.w("Notification extraction failed: ${e.javaClass.simpleName}")
            null
        }

    private fun trimLocked() {
        while (entries.size > MAX_ENTRIES) {
            val oldest = entries.keys.firstOrNull() ?: return
            entries.remove(oldest)
        }
    }

    /** Called by the shade when it opens, so a revoked grant is shown at once. */
    fun refreshAccessState(context: Context) {
        val granted = NotificationAccess.isGranted(context)
        val access = synchronized(entries) {
            application = context.applicationContext
            val current = when {
                !granted -> NotificationAccessState.NOT_GRANTED
                !listenerConnected -> NotificationAccessState.DISCONNECTED
                else -> NotificationAccessState.GRANTED
            }
            publish(access = current)
            current
        }
        if (access == NotificationAccessState.GRANTED) resync()
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
        val clearable = synchronized(entries) { entries[key]?.raw?.isClearable == true }
        if (!clearable) return false
        val ok = runCatching { service.cancelNotification(key); true }.getOrElse {
            LumaLog.w("A notification could not be dismissed")
            return false
        }
        if (ok) enqueueLocalRemoval(key)
        return ok
    }

    /** Dismisses everything Android lets a listener dismiss. */
    fun dismissAll(): Boolean {
        val service = listener ?: return false
        val ok = runCatching { service.cancelAllNotifications(); true }.getOrElse {
            LumaLog.w("Notifications could not be dismissed")
            return false
        }
        if (ok) enqueueLocalRemoval(null)
        return ok
    }

    private fun enqueueLocalRemoval(key: String?) {
        val generation = synchronized(entries) { connectionGeneration }
        worker.execute {
            synchronized(entries) {
                if (!listenerConnected || generation != connectionGeneration) return@synchronized
                if (key == null) entries.entries.removeAll { it.value.raw.isClearable }
                else entries.remove(key)
                publish(replyClearedFor = key)
            }
        }
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
        synchronized(entries) {
            val effectiveAccess = access ?: _state.value.access
            val cards = if (effectiveAccess == NotificationAccessState.GRANTED) {
                NotificationPolicy.visibleCards(entries.values.map { it.raw })
            } else emptyList()
            val current = _state.value.awaitingReply
            val candidates = when {
                replySetFor != null -> current + replySetFor
                replyClearedFor != null -> current - replyClearedFor
                else -> current
            }
            val awaiting = candidates.filter { entries.containsKey(it) }.toSet()
            // StateFlow is thread-safe. Posting a precomputed value to the main
            // queue can otherwise overwrite a newer disconnect or removal.
            _state.value = NotificationShadeState(effectiveAccess, cards, awaiting)
        }
    }

    private const val MAX_ENTRIES = 60
}
