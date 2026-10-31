package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.controls.QuickControlsController
import com.lumaos.pro.launcher.controls.QuickControlsHost
import com.lumaos.pro.launcher.controls.QuickControlsSnapshot
import com.lumaos.pro.launcher.controls.QuickControlsView
import com.lumaos.pro.launcher.controls.TileId
import com.lumaos.pro.launcher.controls.TileTapOutcome
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.notifications.NotificationAccess
import com.lumaos.pro.launcher.notifications.NotificationAccessState
import com.lumaos.pro.launcher.notifications.NotificationCardAdapter
import com.lumaos.pro.launcher.notifications.NotificationCardHost
import com.lumaos.pro.launcher.notifications.NotificationCardModel
import com.lumaos.pro.launcher.notifications.NotificationHub
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassPalette
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import com.lumaos.pro.launcher.widgets.music.MusicCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The LumaOS Pro shade: Quick Controls above, the device's real notifications
 * below, in one pull-down glass panel.
 *
 * Why one surface and two: the reference design's control panel and Android's
 * notification shade answer the same gesture (a pull from the top), and splitting
 * them would mean either stealing a gesture the launcher already uses or stacking
 * two overlays that fight for the same touch stream. One panel, two sections, one
 * dismissal contract.
 *
 * Interaction contract, matching the rest of the home screen:
 *  - opened by a finger-tracked pull from the top edge, or by any gesture bound to
 *    `GestureAction.OpenQuickControls`;
 *  - closed by releasing below the halfway point, by tapping the scrim, by Back,
 *    by the close button, or by opening a notification;
 *  - the overlay is removed from the window when closed, so it can never leave an
 *    invisible touch-blocking layer over the desktop;
 *  - observation starts on open and stops on close: nothing polls while the shade
 *    is hidden.
 *
 * It owns no device state of its own. [QuickControlsController] reads and writes
 * the platform, [NotificationHub] holds the notifications, and this class only
 * renders both and routes intent - which is why a theme change, a rotation or a
 * re-open cannot desynchronise the panel from the device.
 */
class LumaShade(
    private val context: Context,
    private val root: FrameLayout,
    private val scope: CoroutineScope,
    private val host: Host
) : QuickControlsHost, NotificationCardHost {

    /** The home activity: navigation, insets, theming and the desktop depth effect. */
    interface Host {
        fun theme(): ResolvedTheme
        fun glassPalette(): GlassPalette
        fun motion(): MotionPolicy
        /** Status-bar / cutout inset in px, so the panel starts below it. */
        fun topInsetPx(): Int
        /** Navigation-bar inset in px, so the panel ends above it. */
        fun bottomInsetPx(): Int
        /** Usable panel width in dp, after the panel's own margins. */
        fun availableWidthDp(): Int
        fun onDepthProgress(progress: Float)
        fun onOpenSettings()
        fun showMessage(message: String)
        fun appLabel(packageName: String): String?
        fun appIcon(packageName: String): Drawable?
        /** A notification was opened: the host brings the target app forward. */
        fun onNotificationLaunched()
        /**
         * The user moved Android's interruption filter by hand, from
         * [previousFilter] to [newFilter] (platform constants).
         */
        fun onDndChangedByUser(previousFilter: Int, newFilter: Int)
    }

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float): Int = (value * density).toInt()

    val controls = QuickControlsController(context)

    private var overlay: FrameLayout? = null
    private var scrim: View? = null
    private var sheet: LinearLayout? = null
    private var headerTitle: TextView? = null
    private var batteryPill: TextView? = null
    private var notificationHeader: TextView? = null
    private var clearAll: TextView? = null
    private var list: RecyclerView? = null
    private var stateView: LinearLayout? = null
    private var quickControls: QuickControlsView? = null
    private var adapter: NotificationCardAdapter? = null

    private val spring = SpringAnimator(0f, valueThreshold = 0.002f) { p -> applyProgress(p) }
    private var tracking = false
    private var openState = false
    private var observeJob: Job? = null
    private var built = false
    private var listHeightSet = -1

    private var lastControls: QuickControlsSnapshot = QuickControlsSnapshot.EMPTY

    val isOpen: Boolean get() = openState
    val progress: Float get() = spring.value
    val isAnimating: Boolean get() = spring.isRunning || tracking

    // ── Open / close ───────────────────────────────────────────────────────

    fun open() {
        if (openState) return
        ensureBuilt()
        openState = true
        val ov = overlay ?: return
        ov.visibility = View.VISIBLE
        applyTheme()
        refreshEverything()
        startObserving()
        LumaHaptics.snap(ov)
        spring.animateTo(1f, MotionTokens.LUMA, host.motion())
        ov.announceForAccessibility(context.getString(R.string.shade_announce_open))
    }

    fun dismiss() {
        if (!openState && spring.value <= 0.001f) return
        openState = false
        spring.animateTo(0f, MotionTokens.LUMA, host.motion()) { finishClosed() }
    }

    fun dismissImmediately() {
        openState = false
        spring.cancel()
        applyProgress(0f)
        finishClosed()
    }

    private fun finishClosed() {
        if (openState) return
        stopObserving()
        overlay?.visibility = View.GONE
        // The keyboard must not survive the panel that summoned it.
        hideKeyboard()
        host.onDepthProgress(0f)
    }

    private fun hideKeyboard() {
        val focused = root.findFocus() ?: return
        runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(focused.windowToken, 0)
        }
        focused.clearFocus()
    }

    /** Finger tracking, mirroring the drawer's contract. */
    fun startTracking() {
        spring.cancel()
        tracking = true
        if (overlay?.visibility != View.VISIBLE) {
            ensureBuilt()
            overlay?.visibility = View.VISIBLE
            applyTheme()
            refreshEverything()
            startObserving()
        }
    }

    fun trackBy(deltaY: Float) {
        if (!tracking) return
        val travel = travelPx()
        val next = (spring.value + deltaY / travel).coerceIn(0f, 1f)
        spring.track(next)
        if (next > 0.02f && !openState) openState = true
    }

    fun release(velocity: Float) {
        if (!tracking) return
        tracking = false
        val travel = travelPx()
        spring.setVelocity(velocity / travel)
        val shouldOpen = spring.value > OPEN_THRESHOLD || velocity > FLING_OPEN_PX_PER_S
        openState = shouldOpen
        if (shouldOpen) {
            refreshEverything()
            startObserving()
        }
        spring.animateTo(if (shouldOpen) 1f else 0f, MotionTokens.LUMA, host.motion()) {
            if (!shouldOpen) finishClosed()
        }
    }

    fun canDragClose(): Boolean = openState || spring.value > 0.02f

    /**
     * True when a touch that went down at this point landed on the scrim rather
     * than on the panel.
     *
     * Only those drags may close the shade. A drag that starts on the panel
     * belongs to whatever is under the finger - above all the notification list,
     * which must keep every scroll it is entitled to instead of fighting a
     * container gesture for the same stream.
     */
    fun ownsDismissDrag(rawX: Float, rawY: Float): Boolean {
        val panel = sheet ?: return false
        if (panel.visibility != View.VISIBLE || panel.height <= 0) return true
        val location = IntArray(2)
        panel.getLocationOnScreen(location)
        val inside = rawX >= location[0] && rawY >= location[1] &&
            rawX <= location[0] + panel.width && rawY <= location[1] + panel.height
        return !inside
    }

    private fun travelPx(): Float {
        val measured = sheet?.height?.toFloat() ?: 0f
        if (measured > 0f) return measured
        return dp(DEFAULT_TRAVEL_DP).toFloat()
    }

    private fun applyProgress(value: Float) {
        val p = value.coerceIn(0f, 1f)
        val ov = overlay ?: return
        val sh = sheet
        val sc = scrim
        if (sh != null) {
            val travel = if (sh.height > 0) sh.height.toFloat() else dp(DEFAULT_TRAVEL_DP).toFloat()
            sh.translationY = (p - 1f) * travel
            sh.alpha = (0.35f + 0.65f * p).coerceIn(0f, 1f)
        }
        sc?.alpha = p
        ov.visibility = if (p <= 0.001f && !openState) View.GONE else View.VISIBLE
        host.onDepthProgress(p)
    }

    // ── Construction ───────────────────────────────────────────────────────

    private fun ensureBuilt() {
        if (built) return
        built = true

        val ov = FrameLayout(context).apply {
            visibility = View.GONE
            LumaDepthStack.bindLayerElevation(this, LumaTokens.depthControlCenter, density)
        }
        overlay = ov
        root.addView(
            ov,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val scrimView = View(context).apply {
            setBackgroundColor(ColorMath.withAlpha(0xFF000000.toInt(), LumaTokens.folderScrimAlpha))
            alpha = 0f
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.shade_dismiss_description)
            setOnClickListener {
                LumaHaptics.tap(this)
                dismiss()
            }
        }
        scrim = scrimView
        ov.addView(scrimView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // The container bounds the panel; the sheet inside is wrap_content, so a
        // short notification list never stretches the glass to the full screen.
        val container = FrameLayout(context)
        ov.addView(
            container,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP)
        )

        val sheetView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
        }
        sheet = sheetView
        container.addView(
            sheetView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply {
                marginStart = dp(LumaTokens.screenMarginHorizontal)
                marginEnd = dp(LumaTokens.screenMarginHorizontal)
            }
        )

        sheetView.addView(buildHeader(), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val controlsView = QuickControlsView(context, this)
        quickControls = controlsView
        sheetView.addView(
            controlsView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(LumaTokens.spaceSM)
                marginStart = dp(LumaTokens.spaceSM)
                marginEnd = dp(LumaTokens.spaceSM)
            }
        )

        sheetView.addView(buildNotificationHeader(), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(LumaTokens.spaceMD)
            marginStart = dp(LumaTokens.spaceSM)
            marginEnd = dp(LumaTokens.spaceSM)
        })

        val stateContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        stateView = stateContainer
        sheetView.addView(
            stateContainer,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(LumaTokens.spaceSM)
                marginEnd = dp(LumaTokens.spaceSM)
            }
        )

        val listView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            setPadding(0, 0, 0, dp(LumaTokens.spaceSM))
            clipToPadding = false
        }
        list = listView
        val cardAdapter = NotificationCardAdapter(context, this)
        adapter = cardAdapter
        listView.adapter = cardAdapter
        sheetView.addView(
            listView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(LumaTokens.spaceXS)
                marginStart = dp(LumaTokens.spaceSM)
                marginEnd = dp(LumaTokens.spaceSM)
            }
        )

        // A long list must not push the panel past the screen: the panel is capped
        // and the list scrolls inside the space that is left.
        sheetView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> capPanelHeight() }
        applyInsets()
        applyTheme()
    }

    private fun buildHeader(): View {
        val theme = host.theme()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(LumaTokens.spaceMD), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXS), 0)
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.shade_title)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            typeface = Typefaces.title(theme.typography)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeTitleSp)
        }
        headerTitle = title
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val battery = TextView(context).apply {
            maxLines = 1
            includeFontPadding = false
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeMicroSp)
            typeface = Typefaces.label(theme.typography)
            setPadding(dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXXS), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXXS))
            visibility = View.GONE
        }
        batteryPill = battery
        row.addView(battery, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        row.addView(headerButton(R.drawable.ic_rx_settings, R.string.shade_settings_description) {
            LumaHaptics.tap(it)
            host.onOpenSettings()
        })
        row.addView(headerButton(R.drawable.ic_rx_close, R.string.shade_close_description) {
            LumaHaptics.tap(it)
            dismiss()
        })
        return row
    }

    private fun headerButton(iconRes: Int, descriptionRes: Int, onClick: (View) -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(iconRes)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(LumaTokens.spaceSM), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceSM))
            contentDescription = context.getString(descriptionRes)
            background = ThemedDrawables.ripple(0x22FFFFFF, null, LumaTokens.radiusFull * density)
            isClickable = true
            isFocusable = true
            setOnClickListener(onClick)
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(dp(LumaTokens.minTouchTarget), dp(LumaTokens.minTouchTarget))
        }

    private fun buildNotificationHeader(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(LumaTokens.spaceXXS), 0, dp(LumaTokens.spaceXXS), 0)
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.shade_notifications)
            maxLines = 1
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeSectionHeaderSp)
        }
        notificationHeader = title
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val clear = TextView(context).apply {
            text = context.getString(R.string.shade_clear_all)
            maxLines = 1
            includeFontPadding = false
            gravity = Gravity.CENTER
            minHeight = dp(LumaTokens.minTouchTarget * 0.75f)
            setPadding(dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXXS), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXXS))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeCaptionSp)
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener {
                LumaHaptics.tap(this)
                if (!NotificationHub.dismissAll()) {
                    host.showMessage(context.getString(R.string.notification_dismiss_all_failed))
                }
            }
        }
        clearAll = clear
        row.addView(clear, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun capPanelHeight() {
        val sh = sheet ?: return
        val lv = list ?: return
        val maximum = maxSheetHeightPx()
        if (maximum <= 0) return
        val natural = sh.height
        if (natural <= maximum) {
            if (listHeightSet != ViewGroup.LayoutParams.WRAP_CONTENT) {
                listHeightSet = ViewGroup.LayoutParams.WRAP_CONTENT
                lv.layoutParams = lv.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            }
            return
        }
        val overflow = natural - maximum
        val target = (lv.height - overflow).coerceAtLeast(dp(MIN_LIST_HEIGHT_DP))
        if (listHeightSet != target) {
            listHeightSet = target
            lv.layoutParams = lv.layoutParams.apply { height = target }
        }
    }

    private fun maxSheetHeightPx(): Int {
        val available = root.height - host.topInsetPx() - host.bottomInsetPx() - dp(PANEL_VERTICAL_MARGIN_DP)
        if (available <= 0) return 0
        // The panel is a floating surface, not the whole screen: 92% of the usable
        // height keeps the desktop visible behind it, which is what makes the
        // scrim and the depth effect readable as "above the home screen".
        return (available * MAX_HEIGHT_FRACTION).toInt()
    }

    private fun applyInsets() {
        val sh = sheet ?: return
        val lp = sh.layoutParams as? FrameLayout.LayoutParams ?: return
        val topMargin = host.topInsetPx() + dp(PANEL_TOP_MARGIN_DP)
        if (lp.topMargin != topMargin) {
            lp.topMargin = topMargin
            sh.layoutParams = lp
        }
    }

    /** Re-applies insets; called by the host when window insets change. */
    fun onInsetsChanged() {
        if (!built) return
        applyInsets()
        capPanelHeight()
    }

    // ── Theming ────────────────────────────────────────────────────────────

    fun applyTheme() {
        if (!built) return
        val theme = host.theme()
        val palette = host.glassPalette()
        val panelSpec = palette[GlassRole.PRIMARY]
        sheet?.let {
            GlassViews.apply(
                it, panelSpec, density, ripple = false, clipToOutline = true, depth = LumaTokens.depthShade,
                radiusOverrideDp = theme.corner(LumaTokens.radiusXLarge)
            )
        }
        headerTitle?.setTextColor(panelSpec.rest.content)
        batteryPill?.let { pill ->
            pill.setTextColor(panelSpec.rest.content)
            pill.background = ThemedDrawables.surface(
                ColorMath.withAlpha(panelSpec.rest.content, 0.12f), LumaTokens.radiusFull * density
            )
        }
        notificationHeader?.setTextColor(
            ColorMath.withAlpha(panelSpec.rest.content, LumaTokens.typeSectionHeaderAlpha + 0.25f)
        )
        notificationHeader?.typeface = Typefaces.label(theme.typography)
        clearAll?.let { clear ->
            clear.setTextColor(panelSpec.rest.content)
            clear.typeface = Typefaces.label(theme.typography)
            clear.background = ThemedDrawables.ripple(
                panelSpec.rest.ripple, null, LumaTokens.radiusSmall * density
            )
        }
        quickControls?.rebuild(lastControls, controls.torchCapable)
        renderNotificationState()
    }

    // ── State ──────────────────────────────────────────────────────────────

    private fun refreshEverything() {
        applyInsets()
        controls.refresh()
        NotificationHub.refreshAccessState(context)
        else NotificationAccess.requestRebindIfGranted(context)
    }

    private fun startObserving() {
        if (observeJob != null) return
        controls.start()
        controls.onDndChangedByUser = { before, after -> host.onDndChangedByUser(before, after) }
        observeJob = scope.launch {
            launch {
                controls.state.collect { snapshot ->
                    lastControls = snapshot
                    quickControls?.bind(snapshot)
                    renderBattery(snapshot)
                }
            }
            launch {
                NotificationHub.state.collect { shade ->
                    adapter?.submit(shade.cards, shade.awaitingReply)
                    list?.visibility = if (shade.cards.isEmpty()) View.GONE else View.VISIBLE
                    clearAll?.visibility = if (shade.cards.size > 1) View.VISIBLE else View.GONE
                    notificationHeader?.text = if (shade.cards.isEmpty()) {
                        context.getString(R.string.shade_notifications)
                    } else {
                        context.resources.getQuantityString(
                            R.plurals.shade_notifications_count, shade.cards.size, shade.cards.size
                        )
                    }
                    renderNotificationState()
                }
            }
        }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
        controls.stop()
        controls.onDndChangedByUser = null
    }

    private fun renderBattery(snapshot: QuickControlsSnapshot) {
        val pill = batteryPill ?: return
        val percent = snapshot.battery.percent
        if (percent == null) {
            pill.visibility = View.GONE
            return
        }
        pill.visibility = View.VISIBLE
        pill.text = context.getString(
            if (snapshot.battery.charging) R.string.shade_battery_charging else R.string.shade_battery,
            percent
        )
        pill.contentDescription = pill.text
    }

    /**
     * The notification section's non-list states: a missing grant, a disconnected
     * listener, or simply nothing posted. Each is a real state with a real route
     * forward; none of them is filler content.
     */
    private fun renderNotificationState() {
        val container = stateView ?: return
        val theme = host.theme()
        val palette = host.glassPalette()
        val colors = palette[GlassRole.SECONDARY].rest
        val access = NotificationHub.state.value.access
        val hasCards = NotificationHub.state.value.cards.isNotEmpty()

        container.removeAllViews()
        if (hasCards || access == NotificationAccessState.GRANTED) {
            if (hasCards) {
                container.visibility = View.GONE
                return
            }
            container.visibility = View.VISIBLE
            container.addView(stateText(context.getString(R.string.notification_empty), colors.content, theme))
            return
        }

        container.visibility = View.VISIBLE
        val title = if (access == NotificationAccessState.DISCONNECTED) {
            R.string.notification_access_disconnected_title
        } else {
            R.string.notification_access_title
        }
        val body = if (access == NotificationAccessState.DISCONNECTED) {
            R.string.notification_access_disconnected_body
        } else {
            R.string.notification_access_disclosure
        }
        container.addView(stateText(context.getString(title), colors.content, theme, heading = true))
        container.addView(stateText(context.getString(body), colors.muted, theme))

        val actionLabel = if (access == NotificationAccessState.DISCONNECTED) {
            context.getString(R.string.notification_access_retry)
        } else {
            context.getString(R.string.notification_access_open)
        }
        val button = TextView(context).apply {
            text = actionLabel
            gravity = Gravity.CENTER
            maxLines = 2
            minHeight = dp(LumaTokens.minTouchTarget * 0.85f)
            includeFontPadding = false
            setTextColor(theme.colors.onAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeBodySp)
            typeface = Typefaces.label(theme.typography)
            setPadding(dp(LumaTokens.spaceMD), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceMD), dp(LumaTokens.spaceSM))
            background = ThemedDrawables.ripple(
                theme.colors.onAccent and 0x33FFFFFF,
                ThemedDrawables.surface(theme.colors.accent, LumaTokens.radiusSmall * density),
                LumaTokens.radiusSmall * density
            )
            isClickable = true
            isFocusable = true
            contentDescription = actionLabel
            setOnClickListener {
                LumaHaptics.confirm(this)
                if (access == NotificationAccessState.DISCONNECTED) {
                    if (!NotificationAccess.requestRebindIfGranted(context)) {
                        openNotificationAccessSettings()
                    } else {
                        host.showMessage(context.getString(R.string.notification_access_retry_started))
                    }
                } else {
                    openNotificationAccessSettings()
                }
            }
        }
        container.addView(
            button,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(LumaTokens.spaceSM)
            }
        )
    }

    private fun stateText(text: String, color: Int, theme: ResolvedTheme, heading: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(if (heading) color else ColorMath.withAlpha(color, LumaTokens.typeCaptionAlpha + 0.15f))
            setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                if (heading) LumaTokens.typeHeadingSp else LumaTokens.typeCaptionSp
            )
            typeface = if (heading) Typefaces.title(theme.typography) else Typefaces.body(theme.typography)
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(if (heading) LumaTokens.spaceSM else LumaTokens.spaceXXS), 0, 0)
        }

    private fun openNotificationAccessSettings() {
        val intent = NotificationAccess.settingsIntent(context)
        if (intent == null) {
            host.showMessage(context.getString(R.string.notification_access_unavailable))
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { host.showMessage(context.getString(R.string.notification_access_unavailable)) }
    }

    /** Releases views and platform handles; called from the host's onDestroy. */
    fun shutdown() {
        stopObserving()
        controls.shutdown()
        spring.cancel()
        adapter = null
        quickControls = null
        overlay?.let { ov ->
            ov.removeAllViews()
            root.removeView(ov)
        }
        overlay = null
        scrim = null
        sheet = null
        list = null
        stateView = null
        built = false
        listHeightSet = -1
        openState = false
    }

    // ── QuickControlsHost ──────────────────────────────────────────────────

    override fun theme(): ResolvedTheme = host.theme()
    override fun glassPalette(): GlassPalette = host.glassPalette()
    override fun density(): Float = density
    override fun motion(): MotionPolicy = host.motion()
    override fun availableWidthDp(): Int = host.availableWidthDp()
    override fun mediaArtwork(): Drawable? = controls.mediaArtwork()
    override fun mediaAccessGranted(): Boolean = controls.hasNotificationAccess()

    override fun onTileTap(id: TileId) {
        val outcome = controls.onTileTap(id)
        when (outcome) {
            is TileTapOutcome.Toggled -> controls.refresh()
            TileTapOutcome.OpenedSystemRoute -> Unit
            is TileTapOutcome.NeedsGrant -> {
                host.showMessage(context.getString(outcome.explanation))
                openGrantRoute(id)
            }
            is TileTapOutcome.Unavailable -> host.showMessage(context.getString(outcome.reason))
        }
    }

    override fun onTileLongPress(id: TileId) {
        val intent = controls.systemRouteFor(id)
        if (intent == null) {
            host.showMessage(context.getString(R.string.qc_no_system_page))
            return
        }
        runCatching { context.startActivity(intent) }
            .onFailure { host.showMessage(context.getString(R.string.qc_no_system_page)) }
    }

    private fun openGrantRoute(id: TileId) {
        val intent = when (id) {
            TileId.ROTATION_LOCK -> controls.writeSettingsIntent()
            TileId.FOCUS -> controls.notificationPolicyIntent()
            else -> controls.systemRouteFor(id)
        } ?: return
        runCatching { context.startActivity(intent) }
            .onFailure { host.showMessage(context.getString(R.string.qc_no_system_page)) }
    }

    override fun onBrightnessChanged(fraction: Float, commit: Boolean) {
        val applied = controls.applyBrightness(fraction, commit)
        if (!applied && commit) onNeedsWriteSettings()
    }

    override fun onVolumeChanged(fraction: Float, commit: Boolean) {
        val applied = controls.applyVolume(fraction, commit)
        if (!applied && commit) {
            host.showMessage(context.getString(R.string.qc_volume_failed))
            controls.refresh()
        }
    }

    override fun onMediaCommand(command: MusicCommand) {
        if (!controls.mediaCommand(command)) {
            host.showMessage(context.getString(R.string.qc_media_command_failed))
        }
    }

    override fun onNeedsWriteSettings() {
        host.showMessage(context.getString(R.string.qc_grant_write_settings))
        runCatching { context.startActivity(controls.writeSettingsIntent()) }
            .onFailure { host.showMessage(context.getString(R.string.qc_no_system_page)) }
    }

    override fun onNeedsNotificationPolicy() {
        host.showMessage(context.getString(R.string.qc_grant_notification_policy))
        runCatching { context.startActivity(controls.notificationPolicyIntent()) }
            .onFailure { host.showMessage(context.getString(R.string.qc_no_system_page)) }
    }

    override fun onNeedsNotificationAccess() = openNotificationAccessSettings()

    // ── NotificationCardHost ───────────────────────────────────────────────

    override fun appLabel(packageName: String): String? = host.appLabel(packageName)
    override fun appIcon(packageName: String): Drawable? = host.appIcon(packageName)

    override fun onOpen(card: NotificationCardModel) {
        val opened = NotificationHub.open(context, card.key)
        if (opened) {
            host.onNotificationLaunched()
        } else {
            host.showMessage(context.getString(R.string.notification_open_failed, card.appLabel))
        }
    }

    override fun onDismiss(card: NotificationCardModel) {
        if (!NotificationHub.dismiss(card.key)) {
            host.showMessage(context.getString(R.string.notification_dismiss_failed))
        }
    }

    override fun onReply(card: NotificationCardModel, actionIndex: Int, text: String) {
        when (val outcome = NotificationHub.reply(context, card.key, actionIndex, text)) {
            com.lumaos.pro.launcher.notifications.ReplyOutcome.Submitted -> Unit
            com.lumaos.pro.launcher.notifications.ReplyOutcome.ActionExpired ->
                host.showMessage(context.getString(R.string.notification_reply_expired, card.appLabel))
            com.lumaos.pro.launcher.notifications.ReplyOutcome.Unsupported ->
                host.showMessage(context.getString(R.string.notification_reply_unsupported))
            is com.lumaos.pro.launcher.notifications.ReplyOutcome.Failed ->
                host.showMessage(context.getString(R.string.notification_reply_failed, card.appLabel))
        }
    }

    override fun onAction(card: NotificationCardModel, actionIndex: Int) {
        if (!NotificationHub.sendAction(context, card.key, actionIndex)) {
            host.showMessage(context.getString(R.string.notification_action_failed, card.appLabel))
        }
    }

    override fun onEnableAccess() = openNotificationAccessSettings()

    companion object {
        private const val OPEN_THRESHOLD = 0.42f
        private const val FLING_OPEN_PX_PER_S = 900f
        private const val DEFAULT_TRAVEL_DP = 380f
        private const val PANEL_TOP_MARGIN_DP = 8f
        private const val PANEL_VERTICAL_MARGIN_DP = 24f
        private const val MAX_HEIGHT_FRACTION = 0.92f
        private const val MIN_LIST_HEIGHT_DP = 72f

        /**
         * True when a touch that began at [downY] belongs to the shade's pull-down
         * gesture rather than to the desktop. The band starts below the status bar
         * (Android owns the bar itself) and ends before the clock, so it cannot
         * steal the search pull, icon drags or page swipes.
         */
        fun isShadePullZone(downY: Float, topInsetPx: Int, density: Float, dragSlopPx: Int): Boolean {
            val bandBottom = topInsetPx + PULL_BAND_DP * density
            return downY >= topInsetPx - dragSlopPx && downY <= bandBottom
        }

        private const val PULL_BAND_DP = 44f
    }
}
