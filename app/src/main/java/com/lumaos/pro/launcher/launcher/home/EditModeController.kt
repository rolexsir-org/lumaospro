package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.launcher.model.HomeItem
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.LumaSpring
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassPalette
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.GlassSpec
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Home edit mode: rearrange by dragging, drop an app on another to make a
 * folder (or on a folder to add it), drag to the top bar to remove.
 *
 * Placement feedback is continuous: every free slot shows a dashed drop zone,
 * the slot the item will land in is outlined in the accent colour, and when a
 * drop would merge, the target fills after a short hover so a pass-over never
 * creates a folder by accident.
 *
 * Layout contract (see [EditModeDesign] for every value): the chrome is two
 * floating glass cards — header on top, toolbar at the bottom — and the desktop
 * reserves their height, so the clock, grid and dock are never covered. All
 * geometry lives in the tokens, none of it here.
 */
class EditModeController(
    private val context: Context,
    private var grid: RecyclerView,
    private var adapter: HomeGridAdapter,
    private val guide: PlacementGuideView,
    private val topBar: LinearLayout,
    private val toolbar: LinearLayout,
    private val doneButton: TextView,
    private val host: Host
) {
    interface Host {
        fun theme(): ResolvedTheme
        fun glassPalette(): GlassPalette
        fun motion(): com.lumaos.pro.launcher.motion.MotionPolicy
        fun depthAllowed(): Boolean
        fun onEditProgress(progress: Float)

        /**
         * The chrome appeared, disappeared or changed height. The host reserves
         * (or releases) exactly that much space around the desktop so nothing
         * can overlap, and matches the grid/dock margins while editing.
         */
        fun onEditChromeChanged()
        fun commitOrder(arranged: List<HomeItem>)
        fun dropOnto(source: HomeItem, target: HomeItem)
        fun remove(item: HomeItem)
        fun onToolbarAction(action: ToolbarAction, anchor: View)
        fun gridColumns(): Int
        fun gridRows(): Int
        fun cellHeight(): Int
    }

    enum class ToolbarAction(val labelRes: Int, val iconRes: Int) {
        WIDGETS(R.string.edit_widgets, R.drawable.ic_rx_widgets),
        LAYOUT(R.string.edit_layout, R.drawable.ic_rx_layout),
        STUDIO(R.string.edit_studio, R.drawable.ic_rx_studio),
        WALLPAPER(R.string.edit_wallpaper, R.drawable.ic_rx_wallpaper),
        SETTINGS(R.string.edit_settings, R.drawable.ic_rx_settings)
    }

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private fun glassPalette(): GlassPalette = host.glassPalette()

    var isActive = false
        private set

    private val spring = SpringAnimator(0f) { p -> applyProgress(p) }

    /** Toolbar buttons in declaration order, so state can be re-applied by action. */
    private val buttons = LinkedHashMap<ToolbarAction, LinearLayout>()

    // Drag state
    private var dragged: RecyclerView.ViewHolder? = null
    private var draggedItem: HomeItem? = null
    private var mergeCandidate: Int = RecyclerView.NO_POSITION
    private var mergeSince = 0L
    private var mergeArmed = false
    private var removeArmed = false
    private var orderChanged = false

    private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
        override fun isLongPressDragEnabled(): Boolean = isActive
        override fun isItemViewSwipeEnabled(): Boolean = false

        override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
            if (!isActive) return 0
            val flags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            return makeMovementFlags(flags, 0)
        }

        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            if (mergeArmed) return false
            val from = vh.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            adapter.move(from, to)
            orderChanged = true
            LumaHaptics.tick(rv)
            return true
        }

        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(vh, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) {
                dragged = vh
                draggedItem = adapter.itemAt(vh.bindingAdapterPosition)
                adapter.beginDrag()
                orderChanged = false
                LumaHaptics.longPress(vh.itemView)
                LumaSpring.animate(vh.itemView, LumaSpring.Property.SCALE, 1.08f, motion = host.motion())
                LumaSpring.animate(vh.itemView, LumaSpring.Property.ALPHA, 0.94f, motion = host.motion())
                vh.itemView.translationZ = dp(12f).toFloat()
            }
        }

        override fun onChildDraw(
            c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
            dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
        ) {
            super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive) return
            updateDragFeedback(rv, vh)
        }

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            LumaSpring.animate(vh.itemView, LumaSpring.Property.SCALE, 1f, motion = host.motion())
            LumaSpring.animate(vh.itemView, LumaSpring.Property.ALPHA, 1f, motion = host.motion())
            vh.itemView.translationZ = 0f
            val source = draggedItem
            val target = adapter.itemAt(mergeCandidate)
            val arranged = adapter.endDrag()
            when {
                removeArmed && source != null -> host.remove(source)
                mergeArmed && source != null && target != null -> host.dropOnto(source, target)
                orderChanged -> host.commitOrder(arranged)
            }
            resetDrag()
        }

        override fun getAnimationDuration(rv: RecyclerView, animationType: Int, animateDx: Float, animateDy: Float): Long =
            if (host.motion().enabled) 220L else 0L
    })

    init {
        touchHelper.attachToRecyclerView(grid)
        doneButton.setOnClickListener { exit() }

        // The chrome's own height decides how much space the desktop reserves.
        // Re-report it whenever it changes (font scale, rotation, subtitle
        // wrapping) instead of assuming a fixed card height.
        val relayout = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (isActive && bottom - top != oldBottom - oldTop) host.onEditChromeChanged()
        }
        topBar.addOnLayoutChangeListener(relayout)
        toolbar.addOnLayoutChangeListener(relayout)
    }

    /** Measured header card height in px (0 until it is laid out). */
    fun headerHeightPx(): Int = topBar.height

    /**
     * Toolbar height in px. Its geometry is fixed (64dp buttons in a 10dp
     * padded container), so the value is known before the first layout pass and
     * the dock never overlaps the toolbar for a frame.
     */
    fun toolbarHeightPx(): Int =
        if (toolbar.height > 0) toolbar.height
        else if (isActive) EditModeDesign.toolbarHeightPx(density)
        else 0

    private fun updateDragFeedback(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
        val v = vh.itemView
        val cx = v.left + v.translationX + v.width / 2f
        val cy = v.top + v.translationY + v.height / 2f

        // Remove zone: the dragged centre above the grid, over the top bar.
        val gridLoc = IntArray(2)
        rv.getLocationOnScreen(gridLoc)
        val barLoc = IntArray(2)
        topBar.getLocationOnScreen(barLoc)
        val screenY = gridLoc[1] + cy
        val nowRemove = screenY < barLoc[1] + topBar.height + dp(8f)
        if (nowRemove != removeArmed) {
            removeArmed = nowRemove
            styleTopBar()
            if (nowRemove) rv.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        // Merge candidate: another app/folder whose centre is under ours.
        var candidate = RecyclerView.NO_POSITION
        val source = draggedItem
        if (source is HomeItem.App) {
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i)
                if (child === v) continue
                val holder = rv.getChildViewHolder(child)
                val pos = holder.bindingAdapterPosition
                val item = adapter.itemAt(pos) ?: continue
                if (item !is HomeItem.App && item !is HomeItem.Folder) continue
                val ox = child.left + child.width / 2f
                val oy = child.top + child.height / 2f
                if (hypot(cx - ox, cy - oy) < child.width * 0.28f) {
                    candidate = pos
                    break
                }
            }
        }
        val now = SystemClock.uptimeMillis()
        if (candidate != mergeCandidate) {
            mergeCandidate = candidate
            mergeSince = now
            mergeArmed = false
        } else if (candidate != RecyclerView.NO_POSITION && !mergeArmed && now - mergeSince > MERGE_HOVER_MS) {
            mergeArmed = true
            rv.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        // Guide highlight: the merge target, or the slot the item will land in.
        val highlight = if (mergeCandidate != RecyclerView.NO_POSITION) {
            rv.findViewHolderForAdapterPosition(mergeCandidate)?.itemView
        } else v
        guide.mergeTarget = mergeArmed
        guide.target = highlight?.let {
            val inset = dp(4f)
            RectF(
                (it.left + inset).toFloat() - rv.paddingLeft, (it.top + inset).toFloat(),
                (it.right - inset).toFloat() - rv.paddingLeft, (it.bottom - inset).toFloat()
            )
        }
        // Keep merge candidates from waiting forever when hovering stops moving.
        if (mergeCandidate != RecyclerView.NO_POSITION && !mergeArmed) rv.postInvalidateOnAnimation()
    }

    private fun resetDrag() {
        dragged = null
        draggedItem = null
        mergeCandidate = RecyclerView.NO_POSITION
        mergeArmed = false
        orderChanged = false
        if (removeArmed) {
            removeArmed = false
            styleTopBar()
        }
        guide.target = null
        guide.mergeTarget = false
    }

    /** Rebinds drag handling to the page that is currently snapped into view. */
    fun attachGrid(grid: RecyclerView, adapter: HomeGridAdapter) {
        if (this.grid === grid && this.adapter === adapter) return
        touchHelper.attachToRecyclerView(null)
        this.adapter.editMode = false
        this.grid = grid
        this.adapter = adapter
        adapter.editMode = isActive
        touchHelper.attachToRecyclerView(grid)
        if (isActive) updateGuideMetrics()
    }

    fun enter() {
        if (isActive) return
        isActive = true
        buildToolbar()
        applyTheme()
        topBar.visibility = View.VISIBLE
        toolbar.visibility = View.VISIBLE
        guide.visibility = View.VISIBLE
        adapter.editMode = true
        // Reserve the chrome's space before the first frame so the animation
        // never plays over an overlapping layout.
        host.onEditChromeChanged()
        updateGuideMetrics()
        LumaHaptics.longPress(grid)
        spring.animateTo(1f, MotionTokens.GENTLE, host.motion())
        topBar.announceForAccessibility(context.getString(R.string.edit_announce))
    }

    fun exit() {
        if (!isActive) return
        isActive = false
        adapter.editMode = false
        LumaHaptics.tap(doneButton)
        // The space stays reserved until the chrome has fully faded, then the
        // desktop returns to its resting geometry in one step.
        spring.animateTo(0f, MotionTokens.GENTLE, host.motion()) {
            topBar.visibility = View.GONE
            toolbar.visibility = View.GONE
            guide.visibility = View.GONE
            host.onEditChromeChanged()
        }
    }

    /**
     * Aligns the drop zones with the real RecyclerView cells.
     *
     * The guide is a sibling of the grid with the same horizontal margin, so the
     * grid's *content* box (width minus its own padding) is the guide's width.
     */
    fun updateGuideMetrics() {
        val columns = host.gridColumns().coerceAtLeast(1)
        guide.columns = columns
        guide.rows = host.gridRows().coerceAtLeast(1)
        guide.cellHeight = host.cellHeight()
        guide.contentWidth = (grid.width - grid.paddingLeft - grid.paddingRight).coerceAtLeast(0)
        guide.scrollOffset = grid.computeVerticalScrollOffset()
        guide.gutterPx = EditModeDesign.gutterPx(density)
        guide.cornerPx = EditModeDesign.slotRadiusPx(density)
        guide.occupied = occupiedSlots()
        guide.invalidate()
    }

    /**
     * Bounds of every placed item in guide coordinates. Items are already laid
     * out by the grid, so this is exact for apps, folders and full-width
     * widgets alike — no second geometry model to drift out of sync.
     */
    private fun occupiedSlots(): List<RectF> {
        if (grid.childCount == 0) return emptyList()
        val rects = ArrayList<RectF>(grid.childCount)
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            rects.add(
                RectF(
                    (child.left - grid.paddingLeft).toFloat(),
                    child.top.toFloat(),
                    (child.right - grid.paddingLeft).toFloat(),
                    child.bottom.toFloat()
                )
            )
        }
        return rects
    }

    private fun applyProgress(p: Float) {
        val clamped = p.coerceIn(0f, 1f)
        val travel = dp(EditModeDesign.chromeTravelDp).toFloat()
        // Fade + a 12dp slide + a 0.97→1 scale: 200–250ms, no bounce.
        topBar.alpha = clamped
        topBar.translationY = -(1f - clamped) * travel
        topBar.scaleX = EditModeDesign.chromeEnterScale + (1f - EditModeDesign.chromeEnterScale) * clamped
        topBar.scaleY = topBar.scaleX
        toolbar.alpha = clamped
        toolbar.translationY = (1f - clamped) * travel
        guide.alpha = clamped
        host.onEditProgress(clamped)
    }

    fun applyTheme() {
        val theme = host.theme()
        val palette = glassPalette()
        styleTopBar(palette)
        topBar.findViewById<TextView>(R.id.edit_title)?.apply {
            setTextColor(palette[GlassRole.SECONDARY].rest.content)
            typeface = Typefaces.title(theme.typography)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, EditModeDesign.headerTitleSp)
            setShadowLayer(0f, 0f, 0f, 0)
        }
        topBar.findViewById<TextView>(R.id.edit_hint)?.apply {
            setTextColor(
                ColorMath.withAlpha(palette[GlassRole.SECONDARY].rest.content, EditModeDesign.secondaryTextAlpha)
            )
            typeface = Typefaces.body(theme.typography)
            // Two lines maximum: shrink instead of truncating. setTextSize would
            // fight the auto-size pass, so the range is configured here too.
            setShadowLayer(0f, 0f, 0f, 0)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                EditModeDesign.headerSubtitleMinSp.toInt(),
                EditModeDesign.headerSubtitleSp.toInt(),
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
        }
        styleDoneButton(palette, theme)
        styleToolbar(palette, theme)
        guide.accentColor = palette[GlassRole.SETTINGS_CONTROL].rest.focusRing
    }

    /** Header card: 24dp glass, 18dp padding, 1px hairline, soft shadow. */
    private fun styleTopBar(palette: GlassPalette = glassPalette()) {
        val spec = palette[GlassRole.SECONDARY]
        val padding = dp(EditModeDesign.headerPaddingDp)
        topBar.setPadding(padding, padding, padding, padding)
        GlassViews.apply(
            view = topBar,
            spec = spec,
            density = density,
            ripple = false,
            radiusOverrideDp = EditModeDesign.headerRadiusDp
        )
        topBar.elevation = EditModeDesign.headerElevationDp * density
        // Remove feedback: the card itself carries the state, so the hint text
        // and the border change together with the armed flag.
        topBar.isSelected = removeArmed
        topBar.findViewById<TextView>(R.id.edit_hint)?.apply {
            text = context.getString(
                if (removeArmed) R.string.edit_release_to_remove else R.string.edit_hint
            )
            setTextColor(
                ColorMath.withAlpha(
                    if (removeArmed) spec.states.selected.content else spec.rest.content,
                    EditModeDesign.secondaryTextAlpha
                )
            )
        }
    }

    /** Done: 76 × 40dp pill, dark glass, hairline, 14sp Medium. */
    private fun styleDoneButton(palette: GlassPalette, theme: ResolvedTheme) {
        val control = palette[GlassRole.SETTINGS_CONTROL]
        doneButton.minWidth = dp(EditModeDesign.doneMinWidthDp)
        doneButton.minHeight = dp(EditModeDesign.doneHeightDp)
        (doneButton.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            val h = dp(EditModeDesign.doneHeightDp)
            if (lp.height != h) {
                lp.height = h
                doneButton.layoutParams = lp
            }
        }
        doneButton.setPadding(dp(20f), 0, dp(20f), 0)
        doneButton.gravity = Gravity.CENTER
        doneButton.includeFontPadding = false
        doneButton.setTextColor(control.rest.content)
        doneButton.typeface = Typefaces.label(theme.typography)
        doneButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, EditModeDesign.doneTextSp)
        // Fully rounded: a pill, not a card.
        GlassViews.apply(
            view = doneButton,
            spec = control,
            density = density,
            ripple = true,
            radiusOverrideDp = EditModeDesign.pillRadiusDp
        )
        doneButton.elevation = 0f
    }

    /** Toolbar: one 24dp glass container, five equal 64dp buttons inside. */
    private fun styleToolbar(palette: GlassPalette, theme: ResolvedTheme) {
        val spec = palette[GlassRole.MENU]
        GlassViews.apply(
            view = toolbar,
            spec = spec,
            density = density,
            ripple = false,
            radiusOverrideDp = EditModeDesign.toolbarRadiusDp
        )
        val containerPadding = dp(EditModeDesign.toolbarPaddingDp)
        toolbar.setPadding(containerPadding, containerPadding, containerPadding, containerPadding)
        toolbar.elevation = spec.elevationDp * density

        val iconTint = ColorStateList.valueOf(spec.rest.content)
        for ((action, button) in buttons) {
            val icon = button.getChildAt(0) as? ImageView
            val label = button.getChildAt(1) as? TextView
            icon?.imageTintList = iconTint
            label?.apply {
                setTextColor(spec.rest.content)
                typeface = Typefaces.label(theme.typography)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, EditModeDesign.toolbarLabelSp)
            }
            button.background = toolbarButtonBackground(spec)
            button.contentDescription = context.getString(action.labelRes)
        }
    }

    /**
     * Transparent at rest, white at 8% while pressed or selected — the only
     * state fill in the toolbar, so the row reads as one calm surface.
     */
    private fun toolbarButtonBackground(spec: GlassSpec): Drawable {
        val radiusPx = dp(EditModeDesign.toolbarButtonRadiusDp).toFloat()
        val stateFill = ColorMath.withAlpha(Color.WHITE, EditModeDesign.toolbarSelectedFillAlpha)
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(
                ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_selected),
                        intArrayOf(android.R.attr.state_pressed),
                        intArrayOf()
                    ),
                    intArrayOf(stateFill, stateFill, Color.TRANSPARENT)
                )
            )
        }
        return ThemedDrawables.ripple(spec.rest.ripple, shape, radiusPx)
    }

    private fun buildToolbar() {
        if (toolbar.childCount > 0) return
        val buttonHeight = dp(EditModeDesign.toolbarButtonHeightDp)
        val gap = dp(EditModeDesign.toolbarButtonGapDp)
        for (action in ToolbarAction.entries) {
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                // A 2dp inset keeps the ripple inside the 16dp corner without
                // stealing width from the label.
                setPadding(gap, 0, gap, 0)
                contentDescription = context.getString(action.labelRes)
                setOnClickListener { host.onToolbarAction(action, it) }
                addView(ImageView(context).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, action.iconRes)?.mutate())
                    layoutParams = LinearLayout.LayoutParams(
                        dp(EditModeDesign.toolbarIconDp),
                        dp(EditModeDesign.toolbarIconDp)
                    )
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                addView(TextView(context).apply {
                    text = context.getString(action.labelRes)
                    isSingleLine = true
                    maxLines = 1
                    includeFontPadding = false
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, EditModeDesign.toolbarLabelSp)
                    // Never wrap, never truncate: shrink to fit instead. This is
                    // what keeps "Wallpaper" on one line on a 360dp phone.
                    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                        this,
                        EditModeDesign.toolbarLabelMinSp.toInt(),
                        EditModeDesign.toolbarLabelSp.toInt(),
                        1,
                        TypedValue.COMPLEX_UNIT_SP
                    )
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = dp(EditModeDesign.toolbarIconLabelGapDp)
                    layoutParams = lp
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
            }
            val lp = LinearLayout.LayoutParams(0, buttonHeight, 1f)
            toolbar.addView(item, lp)
            buttons[action] = item
        }
    }

    @Suppress("unused")
    private fun near(a: Float, b: Float) = abs(a - b) < dp(2f)

    companion object {
        private const val MERGE_HOVER_MS = 380L
    }
}
