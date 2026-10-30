package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.launcher.HomeGridIdentity
import com.lumaos.pro.launcher.launcher.model.HomeItem
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.LumaSpring
import com.lumaos.pro.launcher.motion.MotionPolicy
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
 * Home editing — gesture first, contextual, no reserved space.
 *
 * Interaction contract:
 *  - **Long-press an icon** and it lifts under the finger: drag it to rearrange,
 *    onto another app to group, onto the dock to park it, or up to the remove
 *    target to delete it. No menu opens first; the drag *is* the primary
 *    long-press action.
 *  - **Release without moving** (or tap an item while editing) and the item's
 *    secondary actions open in the existing contextual menu. That is the one
 *    place menus belong: after intent, not instead of a gesture.
 *  - **Long-press empty workspace** and editing begins: the wallpaper steps back
 *    and one glass capsule takes the search pill's slot. It holds the five home
 *    features — Widgets, Layout, Studio, Wallpaper, Settings — plus Done. Tap
 *    empty space, press back or tap Done to leave; the capsule leaves with it.
 *
 * There is deliberately no banner, no instruction card and no permanent toolbar,
 * and nothing here changes the desktop's layout: the capsule overlays the search
 * row, drop zones appear only while an item is in the air, and the remove target
 * exists only while a drag is in flight.
 *
 * Gesture ownership is single-threaded on purpose: `ItemTouchHelper`'s own
 * long-press drag is disabled (`isLongPressDragEnabled() == false`) and drags are
 * started explicitly from the cell's long-click, so one long press can never both
 * lift an item and open a menu. All geometry lives in [EditModeDesign].
 */
class EditModeController(
    private val context: Context,
    private var grid: RecyclerView,
    private var adapter: HomeGridAdapter,
    private val dockRow: RecyclerView,
    private val dockAdapter: HomeGridAdapter,
    private val guide: PlacementGuideView,
    private val actionBar: LinearLayout,
    private val removeTarget: ImageView,
    private val host: Host
) {
    interface Host {
        fun theme(): ResolvedTheme
        fun glassPalette(): GlassPalette
        fun motion(): MotionPolicy
        fun depthAllowed(): Boolean

        /** Wallpaper veil + defocus progress, 0 (idle) .. 1 (editing). */
        fun onEditProgress(progress: Float)

        /**
         * Editing started or ended. The host swaps the search pill for the
         * contextual capsule and refreshes the page indicator; it must not
         * reserve or release layout space for either.
         */
        fun onEditStateChanged(active: Boolean)

        fun commitOrder(arranged: List<HomeItem>)
        fun commitDockOrder(fromDisplay: Int, toDisplay: Int)
        fun dropOnto(source: HomeItem, target: HomeItem)
        fun remove(item: HomeItem)

        /** Secondary actions for an item that was held but not moved. */
        fun onItemMenu(item: HomeItem, position: Int, anchor: View, inDock: Boolean)

        /** One of the five home features, or Done. [anchor] positions the popup. */
        fun onEditAction(action: Action, anchor: View)

        fun gridColumns(): Int
        fun gridRows(): Int
        fun cellHeight(): Int

        /**
         * One row of the grid as the RecyclerView lays it out: the cell plus the
         * item decoration on both sides. Drop zones advance by this, not by
         * [cellHeight], or they drift further from their cells with every row.
         */
        fun rowPitchPx(): Int = cellHeight()
    }

    /** Everything the contextual capsule can do. Icon-only; labels are a11y only. */
    enum class Action(val labelRes: Int, val iconRes: Int) {
        WIDGETS(R.string.edit_widgets, R.drawable.ic_rx_widgets),
        LAYOUT(R.string.edit_layout, R.drawable.ic_rx_layout),
        STUDIO(R.string.edit_studio, R.drawable.ic_rx_studio),
        WALLPAPER(R.string.edit_wallpaper, R.drawable.ic_rx_wallpaper),
        SETTINGS(R.string.edit_settings, R.drawable.ic_rx_settings),
        DONE(R.string.edit_done, R.drawable.ic_rx_done)
    }

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    var isActive = false
        private set

    /** True while an item is lifted; the exit-on-tap path stays out of the way. */
    val dragInProgress: Boolean get() = dragged != null || dockDragged != null

    private val spring = SpringAnimator(0f, onUpdate = ::applyProgress)
    private val guideSpring = SpringAnimator(0f) { p ->
        guideProgress = p
        applyGuideAlpha()
    }
    private val removeSpring = SpringAnimator(0f) { p ->
        removeProgress = p
        applyRemoveTarget()
    }

    private var editProgress = 0f
    private var guideProgress = 0f
    private var removeProgress = 0f

    private var removeTintRest = Color.WHITE
    private var removeTintArmed = Color.WHITE

    /** Home-grid drag state. */
    private var dragged: RecyclerView.ViewHolder? = null
    private var draggedItem: HomeItem? = null
    private var dragMoved = false
    private var mergeCandidate = RecyclerView.NO_POSITION
    private var mergeSince = 0L
    private var mergeArmed = false
    private var removeArmed = false
    private var orderChanged = false
    private var exitAfterDrag = false

    private val actionButtons = LinkedHashMap<Action, FrameLayout>()

    // ── Home grid drag ─────────────────────────────────────────────────────

    private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
        /**
         * Owned by [beginItemDrag] instead. ItemTouchHelper's own long-press
         * detection would fire alongside the cell's long-click, which is exactly
         * the double-handling this redesign removes.
         */
        override fun isLongPressDragEnabled(): Boolean = false
        override fun isItemViewSwipeEnabled(): Boolean = false

        override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
            makeMovementFlags(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                0
            )

        override fun onMove(
            rv: RecyclerView,
            vh: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
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
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && vh != null) beginDragSession(vh)
        }

        override fun onChildDraw(
            c: Canvas,
            rv: RecyclerView,
            vh: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive) return
            if (!dragMoved && (abs(dX) > slop || abs(dY) > slop)) dragMoved = true
            updateDragFeedback(rv, vh)
        }

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            val view = vh.itemView
            LumaSpring.animate(view, LumaSpring.Property.SCALE, 1f, motion = host.motion())
            LumaSpring.animate(view, LumaSpring.Property.ALPHA, 1f, motion = host.motion())
            view.translationZ = 0f
            if (dragged == null) return
            endDragSession(vh)
        }

        override fun getAnimationDuration(
            rv: RecyclerView,
            animationType: Int,
            animateDx: Float,
            animateDy: Float
        ): Long = if (host.motion().enabled) 220L else 0L
    })

    // ── Dock drag (reorder inside the dock) ────────────────────────────────

    private var dockDragged: RecyclerView.ViewHolder? = null
    private var dockStartPosition = RecyclerView.NO_POSITION
    private var dockMoved = false
    private var dockOrderChanged = false

    private val dockTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
        override fun isLongPressDragEnabled(): Boolean = false
        override fun isItemViewSwipeEnabled(): Boolean = false

        override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
            makeMovementFlags(
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            )

        override fun onMove(
            rv: RecyclerView,
            vh: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = vh.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            dockAdapter.move(from, to)
            dockOrderChanged = true
            LumaHaptics.tick(rv)
            return true
        }

        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(vh, actionState)
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || vh == null) return
            dockDragged = vh
            dockStartPosition = vh.bindingAdapterPosition
            dockMoved = false
            dockOrderChanged = false
            dockAdapter.beginDrag()
            LumaHaptics.longPress(vh.itemView)
            LumaSpring.animate(vh.itemView, LumaSpring.Property.SCALE, 1.06f, motion = host.motion())
            vh.itemView.translationZ = dp(10f).toFloat()
        }

        override fun onChildDraw(
            c: Canvas,
            rv: RecyclerView,
            vh: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive) return
            if (!dockMoved && (abs(dX) > slop || abs(dY) > slop)) dockMoved = true
        }

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            LumaSpring.animate(vh.itemView, LumaSpring.Property.SCALE, 1f, motion = host.motion())
            vh.itemView.translationZ = 0f
            if (dockDragged == null) return
            val to = vh.bindingAdapterPosition
            val item = dockAdapter.itemAt(to)
            val from = dockStartPosition
            val moved = dockMoved
            val changed = dockOrderChanged
            dockAdapter.endDrag()
            dockDragged = null
            dockStartPosition = RecyclerView.NO_POSITION
            dockMoved = false
            dockOrderChanged = false
            when {
                changed && from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION ->
                    host.commitDockOrder(from, to)
                !moved && item != null -> host.onItemMenu(item, to, vh.itemView, inDock = true)
            }
            if (exitAfterDrag) {
                exitAfterDrag = false
                exit()
            }
        }
    })

    init {
        touchHelper.attachToRecyclerView(grid)
        dockTouchHelper.attachToRecyclerView(dockRow)
        buildActionBar()
    }

    // ── Session ────────────────────────────────────────────────────────────

    /**
     * Editing from empty workspace (long-press, gesture binding, or a menu).
     *
     * @param haptic false when the caller's gesture already produced its own
     *        feedback (a lifted item), so the session never buzzes twice.
     */
    fun enter(haptic: Boolean = true) {
        if (isActive) return
        isActive = true
        exitAfterDrag = false
        adapter.editMode = true
        dockAdapter.editMode = true
        actionBar.visibility = View.VISIBLE
        guide.visibility = View.VISIBLE
        applyTheme()
        updateGuideMetrics()
        host.onEditStateChanged(true)
        if (haptic) LumaHaptics.longPress(grid)
        spring.animateTo(1f, MotionTokens.GENTLE, host.motion())
        actionBar.announceForAccessibility(context.getString(R.string.edit_announce))
    }

    fun toggle() {
        if (isActive) exit() else enter()
    }

    fun exit() {
        if (!isActive) return
        // A drag owns the touch stream; finish it first, then leave.
        if (dragged != null || dockDragged != null) {
            exitAfterDrag = true
            return
        }
        isActive = false
        exitAfterDrag = false
        adapter.editMode = false
        dockAdapter.editMode = false
        host.onEditStateChanged(false)
        actionBar.visibility = View.VISIBLE
        guideSpring.animateTo(0f, MotionTokens.GENTLE, host.motion())
        removeSpring.animateTo(0f, MotionTokens.GENTLE, host.motion())
        LumaHaptics.tap(actionBar)
        spring.animateTo(0f, MotionTokens.GENTLE, host.motion()) {
            actionBar.visibility = View.GONE
            guide.visibility = View.GONE
            removeTarget.visibility = View.GONE
        }
    }

    /** Leaving for good (activity stopped, window going away). No motion. */
    fun exitImmediately() {
        if (!isActive) return
        isActive = false
        exitAfterDrag = false
        adapter.editMode = false
        dockAdapter.editMode = false
        restoreLiftedViews()
        if (adapter.dragging) adapter.endDrag()
        resetDrag()
        editProgress = 0f
        guideProgress = 0f
        removeProgress = 0f
        spring.snapTo(0f)
        guideSpring.snapTo(0f)
        removeSpring.snapTo(0f)
        actionBar.visibility = View.GONE
        guide.visibility = View.GONE
        removeTarget.visibility = View.GONE
        host.onEditStateChanged(false)
        host.onEditProgress(0f)
    }

    /**
     * Long-press on a cell. Lifts the item and starts the drag in the same
     * gesture; nothing else may consume that long press.
     *
     * @return true when the drag actually started, so the caller can report the
     *         long press as handled and suppress the click that would otherwise
     *         follow on release.
     */
    fun beginItemDrag(position: Int, anchor: View): Boolean {
        if (dragged != null) return true
        val holder = grid.findContainingViewHolder(anchor)
            ?: grid.findViewHolderForAdapterPosition(position)
            ?: return false
        // Start the drag before entering the session: the edit-mode rebind is
        // then deferred (see HomeGridAdapter.editMode) and the row under the
        // finger is never re-bound mid-gesture.
        touchHelper.startDrag(holder)
        if (dragged == null) {
            // No gesture stream to attach to; at least give the item its state.
            enter()
            return true
        }
        if (!isActive) enter(haptic = false)
        return true
    }

    /** Long-press on a dock cell: same contract, reorder inside the dock. */
    fun beginDockDrag(position: Int, anchor: View): Boolean {
        if (dockDragged != null) return true
        val holder = dockRow.findContainingViewHolder(anchor)
            ?: dockRow.findViewHolderForAdapterPosition(position)
            ?: return false
        dockTouchHelper.startDrag(holder)
        if (!isActive) enter(haptic = dockDragged == null)
        return true
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

    // ── Drop-zone geometry ─────────────────────────────────────────────────

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
        // The guide walks rows, so it needs the pitch: advancing by the bare cell
        // height left the zones 8dp per row short of the cells they outline.
        guide.cellHeight = host.rowPitchPx()
        guide.contentWidth = (grid.width - grid.paddingLeft - grid.paddingRight).coerceAtLeast(0)
        guide.scrollOffset = grid.computeVerticalScrollOffset()
        guide.gutterPx = EditModeDesign.gutterPx(density)
        guide.cornerPx = EditModeDesign.slotRadiusPx(density)
        guide.occupied = occupiedSlots()
        guide.invalidate()
    }

    /**
     * Bounds of every placed item in guide coordinates. Items are already laid
     * out by the grid, so this is exact for apps, folders and full-width widgets
     * alike — no second geometry model to drift out of sync.
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

    // ── Drag session ───────────────────────────────────────────────────────

    private fun beginDragSession(vh: RecyclerView.ViewHolder) {
        dragged = vh
        draggedItem = adapter.itemAt(vh.bindingAdapterPosition)
        dragMoved = false
        mergeCandidate = RecyclerView.NO_POSITION
        mergeArmed = false
        removeArmed = false
        orderChanged = false
        adapter.beginDrag()
        LumaHaptics.longPress(vh.itemView)
        LumaSpring.animate(vh.itemView, LumaSpring.Property.SCALE, 1.06f, motion = host.motion())
        LumaSpring.animate(vh.itemView, LumaSpring.Property.ALPHA, 0.95f, motion = host.motion())
        vh.itemView.translationZ = dp(12f).toFloat()
        // Drop zones wait for the item: they are an affordance, not decoration.
        removeTarget.visibility = View.VISIBLE
        guideSpring.animateTo(1f, MotionTokens.SNAPPY, host.motion())
        removeSpring.animateTo(1f, MotionTokens.SNAPPY, host.motion())
    }

    private fun endDragSession(vh: RecyclerView.ViewHolder) {
        val source = draggedItem
        val target = adapter.itemAt(mergeCandidate)
        val armedRemove = removeArmed
        val armedMerge = mergeArmed
        val moved = dragMoved
        val changed = orderChanged
        val releasedPosition = vh.bindingAdapterPosition
        val arranged = adapter.endDrag()
        resetDrag()
        guideSpring.animateTo(0f, MotionTokens.GENTLE, host.motion())
        removeSpring.animateTo(0f, MotionTokens.GENTLE, host.motion()) {
            removeTarget.visibility = View.GONE
        }
        when {
            armedRemove && source != null -> host.remove(source)
            armedMerge && source != null && target != null -> host.dropOnto(source, target)
            changed -> host.commitOrder(arranged)
        }
        // A hold that never moved is a request for the item's actions, not a drop.
        if (!moved && !armedRemove && !armedMerge && source != null) {
            val position = adapter.items
                .indexOfFirst { HomeGridIdentity.of(it) == HomeGridIdentity.of(source) }
                .takeIf { it >= 0 }
                ?: releasedPosition
            host.onItemMenu(source, position, vh.itemView, inDock = false)
        }
        if (exitAfterDrag) {
            exitAfterDrag = false
            exit()
        }
    }

    private fun resetDrag() {
        dragged = null
        draggedItem = null
        mergeCandidate = RecyclerView.NO_POSITION
        mergeArmed = false
        removeArmed = false
        dragMoved = false
        orderChanged = false
        guide.target = null
        guide.mergeTarget = false
    }

    private fun restoreLiftedViews() {
        for (holder in listOfNotNull(dragged, dockDragged)) {
            val view = holder.itemView
            LumaSpring.animate(view, LumaSpring.Property.SCALE, 1f, motion = host.motion())
            LumaSpring.animate(view, LumaSpring.Property.ALPHA, 1f, motion = host.motion())
            view.translationZ = 0f
        }
        dockDragged = null
        dockStartPosition = RecyclerView.NO_POSITION
        dockMoved = false
        dockOrderChanged = false
    }

    /**
     * Remove zone and merge candidate, evaluated against the dragged item.
     *
     * The remove zone is the strip of workspace above the grid — the clock's
     * band. It never overlaps a cell, so an ordinary reorder can never delete
     * anything by accident, and the target only saturates once the release would
     * really remove.
     */
    private fun updateDragFeedback(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
        val v = vh.itemView
        val cx = v.left + v.translationX + v.width / 2f
        val cy = v.top + v.translationY + v.height / 2f

        val gridLoc = IntArray(2)
        rv.getLocationOnScreen(gridLoc)
        val screenY = gridLoc[1] + cy
        val nowRemove = screenY < gridLoc[1] + dp(2f)
        if (nowRemove != removeArmed) {
            removeArmed = nowRemove
            applyRemoveTarget()
            LumaSpring.animate(
                v,
                LumaSpring.Property.ALPHA,
                if (nowRemove) EditModeDesign.draggedAlphaWhenRemoving else 0.95f,
                motion = host.motion()
            )
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

    // ── Chrome ─────────────────────────────────────────────────────────────

    private fun applyProgress(p: Float) {
        val clamped = p.coerceIn(0f, 1f)
        editProgress = clamped
        val travel = dp(EditModeDesign.actionBarRiseDp).toFloat()
        actionBar.alpha = clamped
        actionBar.translationY = (1f - clamped) * travel
        applyGuideAlpha()
        host.onEditProgress(clamped)
    }

    private fun applyGuideAlpha() {
        // Zones are visible only while an item is in the air, scaled by session progress.
        guide.alpha = (guideProgress * editProgress).coerceIn(0f, 1f)
        if (guide.visibility == View.VISIBLE) guide.invalidate()
    }

    private fun applyRemoveTarget() {
        val p = removeProgress.coerceIn(0f, 1f)
        removeTarget.alpha = p * if (removeArmed) 1f else EditModeDesign.removeTargetRestAlpha
        val scale = (0.92f + 0.08f * p) * if (removeArmed) EditModeDesign.removeTargetArmedScale else 1f
        removeTarget.scaleX = scale
        removeTarget.scaleY = scale
        removeTarget.isSelected = removeArmed
        removeTarget.imageTintList =
            ColorStateList.valueOf(if (removeArmed) removeTintArmed else removeTintRest)
    }

    fun applyTheme() {
        val theme = host.theme()
        val palette = glassPalette()
        val control = palette[GlassRole.SETTINGS_CONTROL]
        styleActionBar(palette)
        styleRemoveTarget(palette, theme)
        // Drop zones borrow the theme's content colour at two very low alphas:
        // visible as a frame, never as a second surface.
        guide.guideColor = ColorMath.withAlpha(control.rest.content, EditModeDesign.slotBorderAlpha)
        guide.fillColor = ColorMath.withAlpha(control.rest.content, EditModeDesign.slotFillAlpha)
        guide.accentColor = control.rest.focusRing
        guide.invalidate()
    }

    /** One glass capsule, one radius, no border competing with the icons. */
    private fun styleActionBar(palette: GlassPalette) {
        val spec = palette[GlassRole.MENU]
        GlassViews.apply(
            view = actionBar,
            spec = spec,
            density = density,
            ripple = false,
            radiusOverrideDp = EditModeDesign.actionBarRadiusDp
        )
        // Horizontal inset only: the buttons keep the capsule's full height so
        // every target stays at 48dp (the pill clips their press fill anyway).
        val inset = dp(EditModeDesign.actionBarInsetDp)
        actionBar.setPadding(inset, 0, inset, 0)
        val tint = ColorStateList.valueOf(spec.rest.content)
        for ((action, button) in actionButtons) {
            val icon = button.getChildAt(0) as? ImageView
            icon?.imageTintList = tint
            button.background = actionButtonBackground(spec)
            button.contentDescription = context.getString(action.labelRes)
        }
    }

    private fun styleRemoveTarget(palette: GlassPalette, theme: ResolvedTheme) {
        val spec = palette[GlassRole.MENU]
        GlassViews.apply(
            view = removeTarget,
            spec = spec,
            density = density,
            ripple = false,
            radiusOverrideDp = EditModeDesign.removeTargetSizeDp / 2f
        )
        removeTarget.elevation = spec.elevationDp * density + dp(4f)
        removeTintRest = palette[GlassRole.SETTINGS_CONTROL].rest.content
        removeTintArmed = theme.colors.danger
        applyRemoveTarget()
    }

    /** Transparent at rest, white at 10% while pressed — one state fill, nothing else. */
    private fun actionButtonBackground(spec: GlassSpec): Drawable {
        val radiusPx = dp(EditModeDesign.actionButtonRadiusDp).toFloat()
        val stateFill = ColorMath.withAlpha(Color.WHITE, EditModeDesign.actionPressedFillAlpha)
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(
                ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_pressed),
                        intArrayOf()
                    ),
                    intArrayOf(stateFill, Color.TRANSPARENT)
                )
            )
        }
        return ThemedDrawables.ripple(spec.rest.ripple, shape, radiusPx)
    }

    /**
     * Five feature targets, a hairline, Done. Equal weight, icon-only, sized to
     * the capsule's own height so nothing is clipped or scrolled.
     */
    private fun buildActionBar() {
        if (actionBar.childCount > 0) {
            for (i in 0 until actionBar.childCount) {
                val child = actionBar.getChildAt(i)
                val action = child.tag as? Action ?: continue
                if (child is FrameLayout) actionButtons[action] = child
            }
            return
        }
        for (action in Action.entries) {
            if (action == Action.DONE) {
                actionBar.addView(
                    View(context).apply {
                        setBackgroundColor(ColorMath.withAlpha(Color.WHITE, EditModeDesign.actionDividerAlpha))
                    },
                    LinearLayout.LayoutParams(
                        dp(EditModeDesign.actionDividerWidthDp).coerceAtLeast(1),
                        dp(EditModeDesign.actionDividerHeightDp)
                    ).apply { gravity = Gravity.CENTER_VERTICAL }
                )
            }
            val button = FrameLayout(context).apply {
                isClickable = true
                isFocusable = true
                tag = action
                contentDescription = context.getString(action.labelRes)
                setOnClickListener {
                    if (isActive) host.onEditAction(action, it)
                }
            }
            button.addView(
                ImageView(context).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, action.iconRes)?.mutate())
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(
                    dp(EditModeDesign.actionIconDp),
                    dp(EditModeDesign.actionIconDp)
                ).apply { gravity = Gravity.CENTER }
            )
            actionBar.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            actionButtons[action] = button
        }
    }

    private fun glassPalette(): GlassPalette = host.glassPalette()

    companion object {
        private const val MERGE_HOVER_MS = 380L
    }
}
