package com.lumaos.pro.launcher.notifications

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.launcher.home.ThemedDrawables
import com.lumaos.pro.launcher.launcher.home.Typefaces
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassPalette
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme

/** What the shade asks its host to do; the host owns navigation and toasts. */
interface NotificationCardHost {
    fun theme(): ResolvedTheme
    fun glassPalette(): GlassPalette
    fun density(): Float
    /** Launcher-resolved label for a package, or null when it has no launcher entry. */
    fun appLabel(packageName: String): String?
    /** Launcher-resolved icon for a package, or null. */
    fun appIcon(packageName: String): Drawable?
    fun onOpen(card: NotificationCardModel)
    fun onDismiss(card: NotificationCardModel)
    fun onReply(card: NotificationCardModel, actionIndex: Int, text: String)
    fun onAction(card: NotificationCardModel, actionIndex: Int)
    fun onEnableAccess()
}

/**
 * Notification cards for the LumaOS Pro shade.
 *
 * Rendered from [NotificationCardModel], which is derived from real notifications
 * by [NotificationExtractor]. There are no sample cards, no placeholder content
 * and no invented actions: a card shows exactly what the posting app supplied, and
 * the reply field exists only when that app attached a `RemoteInput`.
 *
 * The views are built in code (the same approach as [com.lumaos.pro.launcher.widgets.WidgetPicker])
 * so every surface, radius and colour comes from the active Luma Glass material
 * rather than from a second, static theme.
 */
class NotificationCardAdapter(
    private val context: Context,
    private val host: NotificationCardHost
) : RecyclerView.Adapter<NotificationCardAdapter.CardHolder>() {

    private var cards: List<NotificationCardModel> = emptyList()
    private var awaiting: Set<String> = emptySet()
    /** Keys whose reply field the user has opened; survives a rebind. */
    private val expanded = HashSet<String>()
    /** Draft text per key, kept only while the shade is open. */
    private val drafts = HashMap<String, String>()

    fun submit(next: List<NotificationCardModel>, awaitingReply: Set<String>) {
        val previous = cards
        val previousAwaitingSnapshot = awaiting
        cards = next
        awaiting = awaitingReply
        expanded.retainAll(next.mapTo(HashSet()) { it.key })
        drafts.keys.retainAll(expanded)
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(o: Int, n: Int) = previous[o].key == next[n].key
            override fun areContentsTheSame(o: Int, n: Int) =
                previous[o] == next[n] &&
                    previousAwaitingSnapshot.contains(previous[o].key) == awaitingReply.contains(next[n].key)
        })
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = cards.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder =
        CardHolder(CardView(context, host))

    override fun onBindViewHolder(holder: CardHolder, position: Int) {
        val card = cards[position]
        holder.view.bind(
            card = card,
            replySent = awaiting.contains(card.key),
            expanded = expanded.contains(card.key),
            draft = drafts[card.key].orEmpty(),
            onToggleReply = { key, open ->
                if (open) expanded += key else expanded -= key
                notifyItemChanged(holder.bindingAdapterPosition)
            },
            onDraft = { key, text -> drafts[key] = text }
        )
    }

    override fun onViewRecycled(holder: CardHolder) {
        holder.view.release()
        super.onViewRecycled(holder)
    }

    class CardHolder(val view: CardView) : RecyclerView.ViewHolder(view)
}

/**
 * One notification card.
 *
 * Structure: an app line (icon, name, age, dismiss), the title and body, the
 * actions the app really exposed, and - only when the app supplied a
 * `RemoteInput` - the reply field with the app's own suggested choices.
 */
class CardView(context: Context, private val host: NotificationCardHost) : LinearLayout(context) {

    private val density = host.density()
    private fun dp(value: Float): Int = (value * density).toInt()

    private val appIcon = ImageView(context)
    private val appLabel = TextView(context)
    private val age = TextView(context)
    private val dismiss = ImageButton(context)
    private val title = TextView(context)
    private val body = TextView(context)
    private val actionsRow = LinearLayout(context)
    private val replyContainer = LinearLayout(context)
    private val replyField = EditText(context)
    private val replySend = ImageButton(context)
    private val replyStatus = TextView(context)
    private val choicesScroll = HorizontalScrollView(context)
    private val choicesRow = LinearLayout(context)

    private var bound: NotificationCardModel? = null
    private var onDraft: ((String, String) -> Unit)? = null
    private var watcher: android.text.TextWatcher? = null

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true
        setPadding(
            dp(LumaTokens.cardPadding), dp(LumaTokens.spaceSM),
            dp(LumaTokens.cardPadding), dp(LumaTokens.spaceSM)
        )
        buildHeader()
        buildBody()
        buildActions()
        buildReply()
    }

    private fun buildHeader() {
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        appIcon.layoutParams = LayoutParams(dp(LumaTokens.iconInline + 4f), dp(LumaTokens.iconInline + 4f))
        appIcon.scaleType = ImageView.ScaleType.FIT_CENTER
        appIcon.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        header.addView(appIcon)

        appLabel.layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(LumaTokens.spaceXS)
        }
        appLabel.maxLines = 1
        appLabel.ellipsize = android.text.TextUtils.TruncateAt.END
        appLabel.includeFontPadding = false
        header.addView(appLabel)

        age.layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(LumaTokens.spaceXS)
        }
        age.maxLines = 1
        age.includeFontPadding = false
        header.addView(age)

        dismiss.layoutParams = LayoutParams(dp(LumaTokens.minTouchTarget * 0.75f), dp(LumaTokens.minTouchTarget * 0.75f))
        dismiss.scaleType = ImageView.ScaleType.CENTER_INSIDE
        dismiss.setPadding(dp(LumaTokens.spaceXS), dp(LumaTokens.spaceXS), dp(LumaTokens.spaceXS), dp(LumaTokens.spaceXS))
        dismiss.background = ThemedDrawables.ripple(0x22FFFFFF, null, dp(LumaTokens.radiusSmall).toFloat())
        dismiss.visibility = View.GONE
        header.addView(dismiss)

        addView(header, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun buildBody() {
        title.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(LumaTokens.spaceXXS)
        }
        title.maxLines = 2
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.includeFontPadding = false
        addView(title)

        body.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2f)
        }
        // Long messages stay readable: bounded lines with an ellipsis, never a
        // clipped glyph and never an unbounded card.
        body.maxLines = NotificationPolicy.MAX_BODY_LINES
        body.ellipsize = android.text.TextUtils.TruncateAt.END
        body.includeFontPadding = false
        body.setLineSpacing(0f, 1.12f)
        addView(body)
    }

    private fun buildActions() {
        actionsRow.orientation = HORIZONTAL
        actionsRow.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(LumaTokens.spaceXS)
        }
        actionsRow.visibility = View.GONE
        addView(actionsRow)
    }

    private fun buildReply() {
        replyContainer.orientation = VERTICAL
        replyContainer.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(LumaTokens.spaceXS)
        }
        replyContainer.visibility = View.GONE

        val fieldRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        replyField.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        replyField.setPadding(dp(LumaTokens.spaceSM), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceSM))
        replyField.maxLines = 1
        replyField.minHeight = dp(LumaTokens.minTouchTarget)
        replyField.imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_FULLSCREEN
        replyField.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
        replyField.isSingleLine = true
        // A reply is typed for another person, never autofilled from this device.
        replyField.importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        replyField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitReply()
                true
            } else false
        }
        fieldRow.addView(replyField)

        replySend.layoutParams = LinearLayout.LayoutParams(dp(LumaTokens.minTouchTarget), dp(LumaTokens.minTouchTarget))
        replySend.scaleType = ImageView.ScaleType.CENTER_INSIDE
        replySend.setPadding(dp(LumaTokens.spaceSM), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceSM))
        replySend.setOnClickListener { submitReply() }
        fieldRow.addView(replySend)

        replyContainer.addView(fieldRow, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        choicesScroll.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(LumaTokens.spaceXXS)
        }
        choicesScroll.isHorizontalScrollBarEnabled = false
        choicesScroll.visibility = View.GONE
        choicesRow.orientation = HORIZONTAL
        choicesScroll.addView(choicesRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        replyContainer.addView(choicesScroll)

        replyStatus.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        replyStatus.visibility = View.GONE
        replyContainer.addView(replyStatus)

        addView(replyContainer)
    }

    fun bind(
        card: NotificationCardModel,
        replySent: Boolean,
        expanded: Boolean,
        draft: String,
        onToggleReply: (String, Boolean) -> Unit,
        onDraft: (String, String) -> Unit
    ) {
        bound = card
        this.onDraft = onDraft
        val theme = host.theme()
        val palette = host.glassPalette()
        val spec = palette[GlassRole.SECONDARY]
        val colors = spec.rest
        GlassViews.apply(this, spec, density, ripple = true, depth = LumaTokens.depthShade)

        val icon = host.appIcon(card.packageName) ?: NotificationHub.fallbackIcon(card.key)
        appIcon.setImageDrawable(icon)
        appIcon.contentDescription = null

        val label = host.appLabel(card.packageName) ?: card.appLabel
        appLabel.text = label
        appLabel.setTextColor(colors.muted)
        appLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeMicroSp)
        appLabel.typeface = Typefaces.label(theme.typography)

        age.text = ageText(card.postedAtMillis)
        age.setTextColor(ColorMath.withAlpha(colors.muted, LumaTokens.typeMicroAlpha))
        age.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeMicroSp)
        age.typeface = Typefaces.label(theme.typography)

        val dismissible = card.clearable && !card.ongoing
        dismiss.visibility = if (dismissible) View.VISIBLE else View.GONE
        dismiss.setImageResource(R.drawable.ic_rx_close)
        dismiss.imageTintList = android.content.res.ColorStateList.valueOf(colors.muted)
        dismiss.contentDescription = context.getString(R.string.notification_dismiss_description, label)
        dismiss.setOnClickListener {
            LumaHaptics.tap(this)
            host.onDismiss(card)
        }

        title.setTextColor(colors.content)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeBodySp)
        title.typeface = Typefaces.title(theme.typography)
        if (card.contentHidden) {
            title.text = context.getString(R.string.notification_content_hidden)
            title.visibility = View.VISIBLE
            body.visibility = View.GONE
        } else {
            title.text = card.title
            title.visibility = if (card.title.isNullOrBlank()) View.GONE else View.VISIBLE

            val text = card.body
            body.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
            body.text = text
            body.setTextColor(ColorMath.withAlpha(colors.content, LumaTokens.typeCaptionAlpha + 0.2f))
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeBodySp)
            body.typeface = Typefaces.body(theme.typography)
        }

        // A collapsed group summary says how much it stands for; a card never
        // silently represents notifications the user cannot see.
        if (card.collapsedCount > 1 && card.body.isNullOrBlank()) {
            body.visibility = View.VISIBLE
            body.text = context.resources.getQuantityString(
                R.plurals.notification_group_count, card.collapsedCount, card.collapsedCount
            )
        }

        setOnClickListener {
            if (card.canOpen) {
                LumaHaptics.tap(this)
                host.onOpen(card)
            }
        }
        contentDescription = describe(card, label)

        bindActions(card, colors, theme, onToggleReply, expanded, replySent)
        bindReplyField(card, colors, theme, draft, expanded, replySent)
    }

    private fun bindActions(
        card: NotificationCardModel,
        colors: com.lumaos.pro.launcher.ui.theme.GlassColors,
        theme: ResolvedTheme,
        onToggleReply: (String, Boolean) -> Unit,
        expanded: Boolean,
        replySent: Boolean
    ) {
        actionsRow.removeAllViews()
        val reply = card.reply
        val buttons = ArrayList<Pair<Int, String>>()
        if (reply != null && !replySent) buttons += reply.index to reply.label
        card.actions.forEach { action -> buttons += action.index to action.label }

        if (buttons.isEmpty()) {
            actionsRow.visibility = View.GONE
            return
        }
        actionsRow.visibility = View.VISIBLE
        buttons.take(NotificationPolicy.MAX_INLINE_ACTIONS).forEach { (index, label) ->
            val button = TextView(context).apply {
                text = label
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                minimumHeight = dp(LumaTokens.minTouchTarget * 0.75f)
                setPadding(dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXXS), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXXS))
                setTextColor(colors.content)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeCaptionSp)
                typeface = Typefaces.label(theme.typography)
                background = ThemedDrawables.ripple(
                    colors.ripple,
                    ThemedDrawables.surface(
                        ColorMath.withAlpha(colors.content, 0.10f),
                        LumaTokens.radiusSmall * density
                    ),
                    LumaTokens.radiusSmall * density
                )
                isClickable = true
                isFocusable = true
                contentDescription = label
                setOnClickListener {
                    LumaHaptics.tap(this)
                    if (reply != null && index == reply.index) {
                        onToggleReply(card.key, !expanded)
                    } else {
                        host.onAction(card, index)
                    }
                }
            }
            actionsRow.addView(
                button,
                LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(LumaTokens.spaceXXS)
                }
            )
        }
    }

    private fun bindReplyField(
        card: NotificationCardModel,
        colors: com.lumaos.pro.launcher.ui.theme.GlassColors,
        theme: ResolvedTheme,
        draft: String,
        expanded: Boolean,
        replySent: Boolean
    ) {
        val reply = card.reply
        if (reply == null) {
            replyContainer.visibility = View.GONE
            return
        }
        replyContainer.visibility = View.VISIBLE

        if (replySent) {
            replyField.visibility = View.GONE
            replySend.visibility = View.GONE
            choicesScroll.visibility = View.GONE
            replyStatus.visibility = View.VISIBLE
            replyStatus.text = context.getString(R.string.notification_reply_sent, card.appLabel)
            replyStatus.setTextColor(colors.muted)
            replyStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeCaptionSp)
            replyStatus.typeface = Typefaces.label(theme.typography)
            return
        }

        replyStatus.visibility = View.GONE
        val open = expanded
        replyField.visibility = if (open) View.VISIBLE else View.GONE
        replySend.visibility = if (open) View.VISIBLE else View.GONE

        replyField.background = GlassViews.drawable(
            host.glassPalette()[GlassRole.SEARCH], density, ripple = false, depth = LumaTokens.depthShade
        )
        replyField.setTextColor(colors.content)
        replyField.setHintTextColor(ColorMath.withAlpha(colors.muted, 0.8f))
        replyField.hint = context.getString(R.string.notification_reply_hint)
        replyField.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeBodySp)
        replyField.typeface = Typefaces.body(theme.typography)
        if (replyField.text?.toString() != draft) replyField.setText(draft)
        replyField.contentDescription = context.getString(R.string.notification_reply_field_description, card.appLabel)
        // One watcher per view: bind() runs again on every recycle, and a second
        // watcher would write the same draft twice and leak the previous card.
        watcher?.let { replyField.removeTextChangedListener(it) }
        val nextWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                onDraft?.invoke(card.key, s?.toString().orEmpty())
                replySend.isEnabled = !s.isNullOrBlank()
                replySend.alpha = if (s.isNullOrBlank()) 0.4f else 1f
            }
        }
        watcher = nextWatcher
        replyField.addTextChangedListener(nextWatcher)

        replySend.setImageResource(R.drawable.ic_rx_reply)
        replySend.imageTintList = android.content.res.ColorStateList.valueOf(theme.colors.onAccent)
        replySend.background = ThemedDrawables.ripple(
            theme.colors.onAccent and 0x33FFFFFF,
            ThemedDrawables.surface(theme.colors.accent, LumaTokens.radiusSmall * density),
            LumaTokens.radiusSmall * density
        )
        replySend.contentDescription = context.getString(R.string.notification_reply_send_description)
        replySend.isEnabled = draft.isNotBlank()
        replySend.alpha = if (draft.isBlank()) 0.4f else 1f

        // Suggested replies come from the app's own RemoteInput choices.
        val choices = reply.choices
        if (choices.isEmpty() || !open) {
            choicesScroll.visibility = View.GONE
        } else {
            choicesScroll.visibility = View.VISIBLE
            choicesRow.removeAllViews()
            choices.forEach { choice ->
                val chip = TextView(context).apply {
                    text = choice
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setPadding(dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXXS), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXXS))
                    setTextColor(colors.content)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeCaptionSp)
                    typeface = Typefaces.label(theme.typography)
                    background = ThemedDrawables.ripple(
                        colors.ripple,
                        ThemedDrawables.surface(ColorMath.withAlpha(colors.content, 0.10f), LumaTokens.radiusFull * density),
                        LumaTokens.radiusFull * density
                    )
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        replyField.setText(choice)
                        replyField.setSelection(choice.length)
                        submitReply()
                    }
                }
                choicesRow.addView(chip, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(LumaTokens.spaceXXS) })
            }
        }

        if (open && replyField.visibility == View.VISIBLE) {
            replyField.requestFocus()
            replyField.post {
                runCatching {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                        as? android.view.inputmethod.InputMethodManager
                    imm?.showSoftInput(replyField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }

    private fun submitReply() {
        val card = bound ?: return
        val reply = card.reply ?: return
        val text = replyField.text?.toString().orEmpty().trim()
        if (text.isEmpty()) return
        LumaHaptics.confirm(this)
        host.onReply(card, reply.index, text)
    }

    private fun ageText(postedAt: Long): String {
        if (postedAt <= 0L) return ""
        val now = System.currentTimeMillis()
        val bucket = NotificationPolicy.ageBucket(now - postedAt)
        if (bucket == NotificationPolicy.AgeBucket.JUST_NOW) return context.getString(R.string.notification_age_now)
        return DateUtils.getRelativeTimeSpanString(
            postedAt, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    private fun describe(card: NotificationCardModel, label: String): String = buildString {
        append(label)
        append(", ")
        if (card.contentHidden) {
            append(context.getString(R.string.notification_content_hidden))
        } else {
            card.title?.let { append(it).append(", ") }
            card.body?.let { append(it.replace('\n', ' ')).append(", ") }
        }
        append(ageText(card.postedAtMillis))
    }

    /** Drops listeners so a recycled view cannot act on a stale card. */
    fun release() {
        bound = null
        onDraft = null
        watcher?.let { replyField.removeTextChangedListener(it) }
        watcher = null
    }
}
