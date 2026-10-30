package com.lumaos.pro.launcher.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.lumaos.pro.launcher.core.util.LumaLog

/**
 * Platform handles the shade needs in order to act on a notification.
 *
 * Kept apart from [RawNotification] on purpose: the pure model can be tested and
 * reasoned about without framework objects, while these are the *real* intents
 * Android gave the posting app. Indexed by the platform's own action position so
 * a reply is always sent through the action the user tapped.
 */
internal class NotificationHandles(
    val contentIntent: PendingIntent?,
    val deleteIntent: PendingIntent?,
    val actionIntents: List<PendingIntent?>,
    val actionRemoteInputs: List<Array<android.app.RemoteInput>?>,
    val smallIcon: android.graphics.drawable.Icon?
)

/** One card's platform data: the pure model plus what acting on it requires. */
internal class ExtractedNotification(
    val raw: RawNotification,
    val handles: NotificationHandles,
    /** The app's own small icon, used when the launcher has no icon for it. */
    val fallbackIcon: Drawable?
)

/**
 * Turns Android's `StatusBarNotification` into the shade's own model.
 *
 * Extraction is defensive by necessity: notifications come from every app on the
 * device, and OEM builds post extras that do not match AOSP. A malformed extra is
 * skipped, never fatal - a launcher that crashes because another app posted an
 * unusual notification is worse than one that shows a little less text.
 *
 * Privacy: content is read only to render it, is never written to disk, and is
 * never logged. The only strings that reach the log are counts and package-free
 * failure descriptions.
 */
internal object NotificationExtractor {

    fun extract(context: Context, sbn: StatusBarNotification): ExtractedNotification? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: Bundle.EMPTY
        val packageName = sbn.packageName ?: return null

        val secret = notification.visibility == Notification.VISIBILITY_SECRET

        val messages = readMessages(extras)
        val inboxLines = readInboxLines(extras)

        val actions = ArrayList<NotificationActionModel>(notification.actions?.size ?: 0)
        val actionIntents = ArrayList<PendingIntent?>(notification.actions?.size ?: 0)
        val actionInputs = ArrayList<Array<android.app.RemoteInput>?>(notification.actions?.size ?: 0)

        notification.actions?.forEachIndexed { index, action ->
            val label = runCatching { action.title?.toString().orEmpty() }.getOrDefault("")
            val remoteInputs = runCatching { action.remoteInputs }.getOrNull()
            // A reply is only real when the app attached a RemoteInput with a key.
            val replyKey = remoteInputs
                ?.firstOrNull { !it.resultKey.isNullOrBlank() }
                ?.resultKey
                ?.takeIf { replyIsAllowed(action) }
            val choices = remoteInputs?.firstOrNull { !it.choices.isNullOrEmpty() }
                ?.choices?.map { it.toString() }?.filter { it.isNotBlank() }.orEmpty()
            actions += NotificationActionModel(
                index = index,
                label = label,
                replyKey = replyKey,
                choices = choices.take(MAX_CHOICES),
                isSemanticReply = semanticReply(action)
            )
            actionIntents += runCatching { action.actionIntent }.getOrNull()
            actionInputs += remoteInputs
        }

        val raw = RawNotification(
            key = sbn.key,
            packageName = packageName,
            appLabel = readAppLabel(context, packageName),
            groupKey = runCatching { sbn.groupKey }.getOrNull(),
            isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            title = charSequence(extras, Notification.EXTRA_TITLE)?.let { collapse(it) },
            text = charSequence(extras, Notification.EXTRA_TEXT)?.let { collapse(it) },
            subText = charSequence(extras, Notification.EXTRA_SUB_TEXT)?.let { collapse(it) },
            bigText = charSequence(extras, Notification.EXTRA_BIG_TEXT)?.let { collapse(it) },
            inboxLines = inboxLines,
            messages = messages,
            conversationTitle = charSequence(extras, Notification.EXTRA_CONVERSATION_TITLE)?.let { collapse(it) },
            postedAtMillis = sbn.postTime,
            isOngoing = runCatching { sbn.isOngoing }.getOrDefault(false),
            isClearable = runCatching { sbn.isClearable }.getOrDefault(true),
            hasContentIntent = notification.contentIntent != null,
            hasDeleteIntent = notification.deleteIntent != null,
            actions = actions,
            category = runCatching { notification.category }.getOrNull(),
            contentIsSecret = secret,
            count = notification.number.coerceAtLeast(0)
        )

        val handles = NotificationHandles(
            contentIntent = notification.contentIntent,
            deleteIntent = notification.deleteIntent,
            actionIntents = actionIntents,
            actionRemoteInputs = actionInputs,
            smallIcon = runCatching { notification.smallIcon }.getOrNull()
        )

        return ExtractedNotification(raw, handles, loadFallbackIcon(context, handles.smallIcon))
    }

    /**
     * Android lets an app require authentication before its reply action may be
     * used. Honoring that is the difference between a launcher that replies and
     * one that leaks a secure conversation onto a screen the user did not unlock
     * for it.
     */
    private fun replyIsAllowed(action: Notification.Action): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            !runCatching { action.isAuthenticationRequired }.getOrDefault(false)

    private fun semanticReply(action: Notification.Action): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            runCatching { action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }.getOrDefault(false)

    private fun readMessages(extras: Bundle): List<MessageLine> {
        val raw = runCatching { extras.getParcelableArray(Notification.EXTRA_MESSAGES) }.getOrNull()
            ?: return emptyList()
        val self = charSequence(extras, Notification.EXTRA_SELF_DISPLAY_NAME)
        val lines = ArrayList<MessageLine>(raw.size)
        for (parcel in raw) {
            val message = parcel as? Notification.MessagingStyle.Message ?: continue
            val text = runCatching { message.text?.toString().orEmpty() }.getOrDefault("")
            if (text.isBlank()) continue
            val sender = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) message.person?.name?.toString()
                else @Suppress("DEPRECATION") message.sender?.toString()
            }.getOrNull()?.takeIf { it.isNotBlank() }
            val fromMe = sender != null && self != null && sender == self
            lines += MessageLine(sender = if (fromMe) null else sender, text = text, fromMe = fromMe)
        }
        return lines.take(MAX_MESSAGE_LINES)
    }

    private fun readInboxLines(extras: Bundle): List<String> {
        val lines = runCatching { extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) }.getOrNull()
            ?: return emptyList()
        return lines.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.take(MAX_INBOX_LINES)
    }

    /**
     * The posting app's label, or its package tail when Android will not say.
     *
     * Package visibility can hide an app from `PackageManager` (Android 11+), and
     * notifications arrive from every app on the device - including ones with no
     * launcher entry. The shade therefore prefers the label the launcher already
     * resolved through `LauncherApps` for its own app list, and this value is the
     * honest fallback rather than a crash or a blank card.
     */
    private fun readAppLabel(context: Context, packageName: String): String {
        val manager = context.packageManager
        return runCatching {
            manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName.substringAfterLast('.')
    }

    private fun charSequence(extras: Bundle, key: String): CharSequence? = runCatching {
        extras.getCharSequence(key)
    }.getOrNull()

    /** Newlines inside a single-line field break the card's rhythm; spaces do not. */
    private fun collapse(value: CharSequence): String =
        value.toString().replace('\n', ' ').replace('\r', ' ').trim()

    private fun loadFallbackIcon(context: Context, icon: android.graphics.drawable.Icon?): Drawable? {
        if (icon == null) return null
        return runCatching { icon.loadDrawable(context) }.getOrElse {
            LumaLog.w("A notification small icon could not be loaded")
            null
        }
    }

    private const val MAX_CHOICES = 4
    private const val MAX_MESSAGE_LINES = 12
    private const val MAX_INBOX_LINES = 8
}
