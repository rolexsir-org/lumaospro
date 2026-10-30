package com.lumaos.pro.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a card shows, and which notifications are listed at all.
 *
 * These are the decisions that keep the shade honest: a reply exists only because
 * the posting app exposed a `RemoteInput`, a group is never listed twice, an app
 * that hid its content stays hidden, and nothing is invented to fill space. Plain
 * JVM tests over the model - no framework objects, no Robolectric.
 */
class NotificationPolicyTest {

    private fun action(
        index: Int,
        label: String,
        replyKey: String? = null,
        semanticReply: Boolean = false,
        choices: List<String> = emptyList()
    ) = NotificationActionModel(
        index = index, label = label, replyKey = replyKey,
        choices = choices, isSemanticReply = semanticReply
    )

    private fun raw(
        key: String = "0|com.app|1|null|1000",
        packageName: String = "com.app",
        appLabel: String = "App",
        groupKey: String? = null,
        isGroupSummary: Boolean = false,
        title: String? = "Title",
        text: String? = "Body",
        bigText: String? = null,
        inboxLines: List<String> = emptyList(),
        messages: List<MessageLine> = emptyList(),
        conversationTitle: String? = null,
        postedAtMillis: Long = 1_000L,
        isOngoing: Boolean = false,
        isClearable: Boolean = true,
        hasContentIntent: Boolean = true,
        actions: List<NotificationActionModel> = emptyList(),
        contentIsSecret: Boolean = false,
        count: Int = 0
    ) = RawNotification(
        key = key, packageName = packageName, appLabel = appLabel, groupKey = groupKey,
        isGroupSummary = isGroupSummary, title = title, text = text, subText = null,
        bigText = bigText, inboxLines = inboxLines, messages = messages,
        conversationTitle = conversationTitle, postedAtMillis = postedAtMillis,
        isOngoing = isOngoing, isClearable = isClearable, hasContentIntent = hasContentIntent,
        hasDeleteIntent = false, actions = actions, category = null,
        contentIsSecret = contentIsSecret, count = count
    )

    // ── Replies ────────────────────────────────────────────────────────────

    @Test
    fun `a reply exists only when the app exposed a RemoteInput`() {
        assertNull(NotificationPolicy.replyAction(emptyList()))
        assertNull(NotificationPolicy.replyAction(listOf(action(0, "Mark as read"))))
        // A blank label cannot be rendered as a button, so it is not offered.
        assertNull(NotificationPolicy.replyAction(listOf(action(0, "", replyKey = "reply"))))

        val reply = action(1, "Reply", replyKey = "remote_input_reply")
        assertEquals(reply, NotificationPolicy.replyAction(listOf(action(0, "Ok"), reply)))
    }

    @Test
    fun `the app's own semantic reply wins when several actions accept text`() {
        val compose = action(0, "Compose", replyKey = "text")
        val reply = action(1, "Reply", replyKey = "text", semanticReply = true)
        assertEquals(reply, NotificationPolicy.replyAction(listOf(compose, reply)))
        // With no semantic hint, the first real reply action is the one shown.
        assertEquals(compose, NotificationPolicy.replyAction(listOf(compose, reply.copy(isSemanticReply = false))))
    }

    @Test
    fun `the app's suggested replies are carried through to the card`() {
        val reply = action(0, "Reply", replyKey = "text", choices = listOf("On my way", "Thanks"))
        val card = NotificationPolicy.visibleCards(listOf(raw(actions = listOf(reply)))).single()
        assertEquals(listOf("On my way", "Thanks"), card.reply?.choices)
    }

    @Test
    fun `inline actions exclude the reply so it is not offered twice`() {
        val actions = listOf(
            action(0, "Reply", replyKey = "text"),
            action(1, "Mark as read"),
            action(2, ""),
            action(3, "Snooze"),
            action(4, "Delete"),
            action(5, "Extra")
        )
        val inline = NotificationPolicy.inlineActions(actions)
        assertEquals(listOf("Mark as read", "Snooze", "Delete"), inline.map { it.label })
        assertEquals(NotificationPolicy.MAX_INLINE_ACTIONS, inline.size)
    }

    // ── Text ───────────────────────────────────────────────────────────────

    @Test
    fun `a conversation title wins over the app's own title`() {
        assertEquals("Sam", NotificationPolicy.displayTitle(raw(conversationTitle = "Sam", title = "2 messages")))
        assertEquals("2 messages", NotificationPolicy.displayTitle(raw(title = "2 messages")))
        assertNull(NotificationPolicy.displayTitle(raw(title = null)))
        assertNull(NotificationPolicy.displayTitle(raw(title = "   ")))
    }

    @Test
    fun `content the app marked secret is never rendered from another field`() {
        val secret = raw(
            contentIsSecret = true, title = "Sam", text = "On my way",
            bigText = "On my way", messages = listOf(MessageLine("Sam", "On my way"))
        )
        assertNull(NotificationPolicy.displayTitle(secret))
        assertNull(NotificationPolicy.displayBody(secret))
        assertTrue(NotificationPolicy.visibleCards(listOf(secret)).single().contentHidden)
    }

    @Test
    fun `messaging lines are the body, newest last, with the sender named`() {
        val card = NotificationPolicy.visibleCards(
            listOf(raw(messages = listOf(
                MessageLine("Sam", "Are you free?"),
                MessageLine(null, "In ten minutes", fromMe = true),
                MessageLine("Sam", "Bring the notes")
            )))
        ).single()
        assertEquals("Sam: Are you free?\nIn ten minutes\nSam: Bring the notes", card.body)
        assertTrue(card.conversation)
    }

    @Test
    fun `a long conversation keeps the last lines instead of the first`() {
        val lines = (1..8).map { MessageLine("Sam", "message $it") }
        val body = NotificationPolicy.displayBody(raw(messages = lines)).orEmpty()
        assertEquals(NotificationPolicy.MAX_BODY_LINES, body.lines().size)
        assertTrue(body.endsWith("message 8"))
        assertFalse(body.contains("message 1"))
    }

    @Test
    fun `body precedence falls back through the real extras only`() {
        assertEquals("Expanded", NotificationPolicy.displayBody(raw(bigText = "Expanded")))
        assertEquals("a\nb", NotificationPolicy.displayBody(raw(inboxLines = listOf("a", "b"))))
        assertEquals("Plain", NotificationPolicy.displayBody(raw(text = "Plain")))
        assertNull(NotificationPolicy.displayBody(raw(title = "Only a title", text = null)))
        assertNull(NotificationPolicy.displayBody(raw(text = "   ")))
    }

    @Test
    fun `blank messaging lines are skipped rather than drawn as empty rows`() {
        val body = NotificationPolicy.displayBody(
            raw(messages = listOf(MessageLine("Sam", "  "), MessageLine("Sam", "Here")))
        )
        assertEquals("Sam: Here", body)
    }

    // ── Grouping ───────────────────────────────────────────────────────────

    @Test
    fun `a group summary is chrome while its children are listed`() {
        val children = listOf(
            raw(key = "c1", groupKey = "g", title = "One"),
            raw(key = "c2", groupKey = "g", title = "Two")
        )
        val summary = raw(key = "s", groupKey = "g", isGroupSummary = true, title = "2 messages", count = 2)
        val cards = NotificationPolicy.visibleCards(children + summary)
        assertEquals(listOf("c1", "c2"), cards.map { it.key }.sorted())
        assertFalse(cards.any { it.key == "s" })
        // Children are not summaries, so they carry no collapsed count.
        assertTrue(cards.all { it.collapsedCount == 0 })
    }

    @Test
    fun `a summary whose children are gone is the only real notification left`() {
        val summary = raw(key = "s", groupKey = "g", isGroupSummary = true, title = "3 messages", count = 3)
        val card = NotificationPolicy.visibleCards(listOf(summary)).single()
        assertEquals(3, card.collapsedCount)
    }

    @Test
    fun `a summary keeps the larger of Android's count and the children it can see`() {
        val child = raw(key = "c1", groupKey = "g")
        val summary = raw(key = "s", groupKey = "g", isGroupSummary = true, count = 0)
        // The child is live, so the summary is suppressed; with no count of its
        // own a lone summary would report nothing invented either.
        assertEquals(listOf("c1"), NotificationPolicy.visibleCards(listOf(child, summary)).map { it.key })
        assertEquals(0, NotificationPolicy.visibleCards(listOf(summary)).single().collapsedCount)
    }

    // ── Ordering and limits ────────────────────────────────────────────────

    @Test
    fun `ongoing notifications lead, then newest first`() {
        val old = raw(key = "old", postedAtMillis = 100L)
        val new = raw(key = "new", postedAtMillis = 300L)
        val ongoing = raw(key = "ongoing", postedAtMillis = 50L, isOngoing = true)
        assertEquals(
            listOf("ongoing", "new", "old"),
            NotificationPolicy.visibleCards(listOf(old, ongoing, new)).map { it.key }
        )
    }

    @Test
    fun `the shade lists a bounded number of cards`() {
        val many = (1..(NotificationPolicy.MAX_CARDS + 6)).map { raw(key = "k$it", postedAtMillis = it.toLong()) }
        val cards = NotificationPolicy.visibleCards(many)
        assertEquals(NotificationPolicy.MAX_CARDS, cards.size)
        // The bound keeps the newest, which is what a user scrolls to first.
        assertEquals((NotificationPolicy.MAX_CARDS + 6).toLong(), cards.first().postedAtMillis)
    }

    @Test
    fun `an empty list produces no cards and no filler`() {
        assertTrue(NotificationPolicy.visibleCards(emptyList()).isEmpty())
        assertFalse(NotificationPolicy.hasContent(emptyList()))
    }

    // ── Card fields ────────────────────────────────────────────────────────

    @Test
    fun `a card reports only the abilities the notification really has`() {
        val actionable = NotificationPolicy.visibleCards(listOf(raw(
            key = "a", hasContentIntent = true, isClearable = true,
            actions = listOf(action(0, "Open"))
        ))).single()
        assertTrue(actionable.canOpen)
        assertTrue(actionable.clearable)
        assertEquals(1, actionable.actions.size)

        val inert = NotificationPolicy.visibleCards(listOf(raw(
            key = "b", hasContentIntent = false, isClearable = false
        ))).single()
        assertFalse(inert.canOpen)
        assertFalse(inert.clearable)
        assertTrue(inert.actions.isEmpty())
        assertNull(inert.reply)
    }

    @Test
    fun `a notification whose app was removed still renders with what it carries`() {
        val card = NotificationPolicy.visibleCards(listOf(raw(
            key = "gone", packageName = "com.gone", appLabel = "com.gone", title = null, text = null
        ))).single()
        assertEquals("com.gone", card.appLabel)
        assertNull(card.title)
        assertNull(card.body)
        assertFalse(card.conversation)
    }

    // ── Age buckets ────────────────────────────────────────────────────────

    @Test
    fun `age picks the honest unit at every boundary`() {
        assertEquals(NotificationPolicy.AgeBucket.JUST_NOW, NotificationPolicy.ageBucket(-5L))
        assertEquals(NotificationPolicy.AgeBucket.JUST_NOW, NotificationPolicy.ageBucket(0L))
        assertEquals(NotificationPolicy.AgeBucket.JUST_NOW, NotificationPolicy.ageBucket(59_999L))
        assertEquals(NotificationPolicy.AgeBucket.MINUTES, NotificationPolicy.ageBucket(60_000L))
        assertEquals(NotificationPolicy.AgeBucket.MINUTES, NotificationPolicy.ageBucket(3_599_999L))
        assertEquals(NotificationPolicy.AgeBucket.HOURS, NotificationPolicy.ageBucket(3_600_000L))
        assertEquals(NotificationPolicy.AgeBucket.HOURS, NotificationPolicy.ageBucket(86_399_999L))
        assertEquals(NotificationPolicy.AgeBucket.DAYS, NotificationPolicy.ageBucket(86_400_000L))
    }
}
