package com.lumaos.pro.launcher.notifications

/**
 * Pure notification model for the LumaOS Pro shade.
 *
 * The platform layer ([NotificationHub] and the shared notification listener)
 * extracts real `StatusBarNotification` data into [RawNotification]; everything
 * below that is plain Kotlin: which text wins, which action is a reply, how
 * grouped notifications collapse, and what the card list looks like. Keeping the
 * decisions here means they are covered by JVM unit tests instead of being
 * discovered on a device.
 *
 * Nothing in this file is persisted. LumaOS Pro renders notifications while they
 * exist and forgets them when Android removes them; no content is written to
 * disk, logged, or sent anywhere.
 */

/** One action the originating app exposed on its own notification. */
data class NotificationActionModel(
    /** Index into the platform notification's action array; needed to send the reply. */
    val index: Int,
    val label: String,
    /**
     * The `RemoteInput` result key when this action accepts typed text, else
     * null. A non-null key is the *only* evidence that an inline reply is real.
     */
    val replyKey: String? = null,
    /** Suggested replies the app supplied, when it supplied any. */
    val choices: List<String> = emptyList(),
    /** True when the app marked this action as semantically a reply/mark-read. */
    val isSemanticReply: Boolean = false
) {
    val isReply: Boolean get() = !replyKey.isNullOrBlank()
}

/** One line of a `MessagingStyle` notification. */
data class MessageLine(
    /** Null for lines the app posted as "me" (the user's own replies). */
    val sender: String?,
    val text: String,
    val fromMe: Boolean = false
)

/**
 * A faithful, content-only projection of one posted notification.
 *
 * Deliberately no `PendingIntent`, `Bitmap` or `Notification` reference: those
 * stay in the platform layer keyed by [key], so this model is JVM-testable and
 * cannot leak a framework object into a view that outlives it.
 */
data class RawNotification(
    /** Android's unique key (`StatusBarNotification.getKey()`). */
    val key: String,
    val packageName: String,
    val appLabel: String,
    val groupKey: String?,
    val isGroupSummary: Boolean,
    val title: String?,
    val text: String?,
    val subText: String?,
    val bigText: String?,
    val inboxLines: List<String>,
    val messages: List<MessageLine>,
    val conversationTitle: String?,
    val postedAtMillis: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val hasContentIntent: Boolean,
    val hasDeleteIntent: Boolean,
    val actions: List<NotificationActionModel>,
    val category: String?,
    /** True when the posting app asked Android to hide its content. */
    val contentIsSecret: Boolean,
    /** Number of notifications this one stands for (Android's `number` extra). */
    val count: Int
) {
    val isConversational: Boolean get() = messages.isNotEmpty() || !conversationTitle.isNullOrBlank()
}

/** A card the shade can render, already resolved to its final text. */
data class NotificationCardModel(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val body: String?,
    /** Set when a group summary stands in for hidden children. */
    val collapsedCount: Int,
    val postedAtMillis: Long,
    val ongoing: Boolean,
    val clearable: Boolean,
    val canOpen: Boolean,
    val contentHidden: Boolean,
    val actions: List<NotificationActionModel>,
    val reply: NotificationActionModel?,
    val conversation: Boolean
)

/**
 * Decisions about what a card shows and which notifications appear at all.
 *
 * The two rules that matter most for a launcher shade are honesty and absence of
 * duplication: never invent a reply action, and never render a group summary
 * *and* the children it summarises.
 */
object NotificationPolicy {

    /** Cards beyond this are reachable in the originating app, not in a launcher. */
    const val MAX_CARDS = 24

    /** Actions shown inline; the rest stay with the app that posted them. */
    const val MAX_INLINE_ACTIONS = 3

    /** Body lines before ellipsis; long messages stay readable, never clipped mid-glyph. */
    const val MAX_BODY_LINES = 4

    /**
     * The action that genuinely accepts a typed reply, or null.
     *
     * Only an action carrying a `RemoteInput` key can be replied to. Android's
     * semantic-reply extra is used to prefer the app's own "Reply" when several
     * actions accept text (some apps expose both Reply and a quick-compose).
     */
    fun replyAction(actions: List<NotificationActionModel>): NotificationActionModel? {
        val candidates = actions.filter { it.isReply && it.label.isNotBlank() }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.isSemanticReply } ?: candidates.first()
    }

    /**
     * Non-reply actions worth showing inline.
     *
     * Replies are rendered as an input, not as a button, so they are excluded
     * here; otherwise a messaging notification would offer "Reply" twice.
     */
    fun inlineActions(actions: List<NotificationActionModel>): List<NotificationActionModel> =
        actions.filter { !it.isReply && it.label.isNotBlank() }.take(MAX_INLINE_ACTIONS)

    /** Title precedence: conversation title, then the app's own title. */
    fun displayTitle(raw: RawNotification): String? = when {
        raw.contentIsSecret -> null
        !raw.conversationTitle.isNullOrBlank() -> raw.conversationTitle
        !raw.title.isNullOrBlank() -> raw.title
        else -> null
    }

    /**
     * Body precedence for real notifications.
     *
     * Messaging lines win (they are what the user actually wants to read and
     * reply to), then expanded big text, then inbox lines, then the content text.
     * Returning null means "the app posted no readable body", which the card
     * renders as a single-line card rather than an empty gap.
     */
    fun displayBody(raw: RawNotification): String? {
        if (raw.contentIsSecret) return null
        if (raw.messages.isNotEmpty()) {
            val visible = raw.messages.filter { it.text.isNotBlank() }.takeLast(MAX_BODY_LINES)
            if (visible.isNotEmpty()) {
                return visible.joinToString("\n") { line ->
                    val sender = line.sender?.takeIf { it.isNotBlank() && !line.fromMe }
                    if (sender == null) line.text else "$sender: ${line.text}"
                }
            }
        }
        raw.bigText?.takeIf { it.isNotBlank() }?.let { return it }
        if (raw.inboxLines.isNotEmpty()) {
            val lines = raw.inboxLines.filter { it.isNotBlank() }.take(MAX_BODY_LINES)
            if (lines.isNotEmpty()) return lines.joinToString("\n")
        }
        return raw.text?.takeIf { it.isNotBlank() }
    }

    /**
     * The notifications the shade lists.
     *
     * Group handling: when a group's children are visible, the summary is not
     * listed as its own card (that is the classic "every notification twice"
     * defect). A summary whose children are hidden or already dismissed *is*
     * listed, because it is then the only real notification left.
     */
    fun visibleCards(raws: List<RawNotification>): List<NotificationCardModel> {
        if (raws.isEmpty()) return emptyList()
        val childrenByGroup = raws
            .filter { !it.isGroupSummary && !it.groupKey.isNullOrBlank() }
            .groupBy { it.groupKey }
            .mapValues { (_, items) -> items.size }

        val cards = ArrayList<NotificationCardModel>(raws.size)
        for (raw in raws) {
            if (raw.isGroupSummary) {
                val siblings = childrenByGroup[raw.groupKey] ?: 0
                // A summary with live children is chrome, not content.
                if (siblings > 0) continue
            }
            cards += card(raw, collapsedCount = childrenByGroup[raw.groupKey] ?: 0)
        }
        return cards
            .sortedWith(compareByDescending<NotificationCardModel> { it.ongoing }.thenByDescending { it.postedAtMillis })
            .take(MAX_CARDS)
    }

    private fun card(raw: RawNotification, collapsedCount: Int): NotificationCardModel {
        val hidden = raw.contentIsSecret
        val reply = replyAction(raw.actions)
        return NotificationCardModel(
            key = raw.key,
            packageName = raw.packageName,
            appLabel = raw.appLabel,
            title = displayTitle(raw),
            body = displayBody(raw),
            collapsedCount = if (raw.isGroupSummary) collapsedCount.coerceAtLeast(raw.count) else 0,
            postedAtMillis = raw.postedAtMillis,
            ongoing = raw.isOngoing,
            clearable = raw.isClearable,
            canOpen = raw.hasContentIntent,
            contentHidden = hidden,
            actions = inlineActions(raw.actions),
            reply = reply,
            conversation = raw.isConversational
        )
    }

    /** True when the shade has anything real to show. */
    fun hasContent(cards: List<NotificationCardModel>): Boolean = cards.isNotEmpty()

    /**
     * Relative time text bucket, resolved without a clock so it is testable.
     * The panel formats it with `DateUtils`; this only picks the honest unit.
     */
    fun ageBucket(ageMillis: Long): AgeBucket = when {
        ageMillis < 0 -> AgeBucket.JUST_NOW
        ageMillis < MINUTE_MILLIS -> AgeBucket.JUST_NOW
        ageMillis < HOUR_MILLIS -> AgeBucket.MINUTES
        ageMillis < DAY_MILLIS -> AgeBucket.HOURS
        else -> AgeBucket.DAYS
    }

    enum class AgeBucket { JUST_NOW, MINUTES, HOURS, DAYS }

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 3_600_000L
    private const val DAY_MILLIS = 86_400_000L
}

/** Why the shade has no cards. Each state is real; none of them is a placeholder. */
enum class NotificationAccessState {
    /** Android has granted the listener; notifications flow. */
    GRANTED,

    /** The user has not enabled notification access for LumaOS Pro. */
    NOT_GRANTED,

    /**
     * Access is granted but Android has not (re)connected the listener - a
     * transient state after a reboot, an update, or a revocation in progress.
     */
    DISCONNECTED
}

/** Reply submission outcome. A success is only reported when Android accepted the send. */
sealed interface ReplyOutcome {
    /** `PendingIntent.send` returned; the originating app now owns the reply. */
    data object Submitted : ReplyOutcome

    /** The action's PendingIntent was cancelled: the notification is stale. */
    data object ActionExpired : ReplyOutcome

    /** Android refused the send (security, or a dead target). */
    data class Failed(val reason: String) : ReplyOutcome

    /** No reply action exists; the UI must never have offered one. */
    data object Unsupported : ReplyOutcome
}
