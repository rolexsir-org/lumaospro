package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.launcher.model.HomeItem
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassPalette
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.LumaTypeScale
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Home edit mode: rearrange by dragging, drop an app on another to make a
 * folder (or on a folder to add it), drag to the top bar to remove.
 *
 * Placement feedback is continuous: every cell shows a guide outline, the slot
 * the item will land in is outlined in the accent colour, and when a drop
 * would merge, the target fills after a short hover so a pass-over never
 * creates a folder by accident.
 */
class EditModeController(
    private val context: Context,
    private val grid: RecyclerView,
    private val adapter: HomeGridAdapter,
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
            rv.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
                vh.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                vh.itemView.animate().scaleX(1.1f).scaleY(1.1f).alpha(0.94f).setDuration(120).start()
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
            vh.itemView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start()
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
    }

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

    fun enter() {
        if (isActive) return
        isActive = true
        buildToolbar()
        applyTheme()
        topBar.visibility = View.VISIBLE
        toolbar.visibility = View.VISIBLE
        guide.visibility = View.VISIBLE
        adapter.editMode = true
        updateGuideMetrics()
        grid.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        spring.animateTo(1f, MotionTokens.STANDARD, host.motion())
        topBar.announceForAccessibility(context.getString(R.string.edit_announce))
    }

    fun exit() {
        if (!isActive) return
        isActive = false
        adapter.editMode = false
        spring.animateTo(0f, MotionTokens.STANDARD, host.motion()) {
            topBar.visibility = View.GONE
            toolbar.visibility = View.GONE
            guide.visibility = View.GONE
        }
    }

    fun updateGuideMetrics() {
        guide.columns = host.gridColumns()
        guide.rows = host.gridRows()
        guide.cellHeight = host.cellHeight()
        guide.scrollOffset = grid.computeVerticalScrollOffset()
        guide.invalidate()
    }

    private fun applyProgress(p: Float) {
        val clamped = p.coerceIn(0f, 1f)
        host.onEditProgress(clamped)
        topBar.alpha = clamped
        topBar.translationY = -(1f - p) * dp(24f)
        toolbar.alpha = clamped
        toolbar.translationY = (1f - p) * dp(32f)
        guide.alpha = clamped
    }

    fun applyTheme() {
        val theme = host.theme()
        val c = theme.colors
        val palette = glassPalette()
        val secondary = palette[GlassRole.SECONDARY]
        val control = palette[GlassRole.SETTINGS_CONTROL]
        styleTopBar(palette)
        topBar.findViewById<TextView>(R.id.edit_title)?.apply {
            setTextColor(secondary.rest.content)
            typeface = Typefaces.title(theme.typography)
            setShadowLayer(0f, 0f, 0f, 0)
        }
        topBar.findViewById<TextView>(R.id.edit_hint)?.apply {
            setTextColor(secondary.rest.muted)
            typeface = Typefaces.body(theme.typography)
            setShadowLayer(0f, 0f, 0f, 0)
        }
        doneButton.setTextColor(control.rest.content)
        doneButton.typeface = Typefaces.label(theme.typography)
        doneButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.labelLargeSp)
        doneButton.background = GlassViews.drawable(control, density, ripple = true)
        GlassViews.apply(toolbar, palette[GlassRole.MENU], density, ripple = false)
        guide.guideColor = ColorMath.withAlpha(c.homeForeground, 0.22f)
        guide.accentColor = control.rest.focusRing
        guide.cornerPx = control.radiusDp * density
        for (i in 0 until toolbar.childCount) {
            val item = toolbar.getChildAt(i) as? LinearLayout ?: continue
            (item.getChildAt(0) as? ImageView)?.imageTintList = android.content.res.ColorStateList.valueOf(control.rest.content)
            (item.getChildAt(1) as? TextView)?.apply {
                setTextColor(control.rest.muted)
                typeface = Typefaces.label(theme.typography)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.labelSmallSp)
            }
            item.background = GlassViews.drawable(control, density, ripple = true)
        }
    }

    private fun styleTopBar(palette: GlassPalette = glassPalette()) {
        val spec = palette[GlassRole.SECONDARY]
        GlassViews.apply(topBar, spec, density, ripple = false)
        topBar.isSelected = removeArmed
        topBar.findViewById<TextView>(R.id.edit_hint)?.apply {
            text = context.getString(
                if (removeArmed) R.string.edit_release_to_remove else R.string.edit_hint
            )
            setTextColor(if (removeArmed) spec.states.selected.content else spec.rest.muted)
        }
    }

    private fun buildToolbar() {
        if (toolbar.childCount > 0) return
        for (action in ToolbarAction.entries) {
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(56f)
                minimumWidth = dp(56f)
                setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
                isClickable = true
                isFocusable = true
                contentDescription = context.getString(action.labelRes)
                setOnClickListener { host.onToolbarAction(action, it) }
                addView(ImageView(context).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, action.iconRes)?.mutate())
                    layoutParams = LinearLayout.LayoutParams(dp(24f), dp(24f))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                addView(TextView(context).apply {
                    text = context.getString(action.labelRes)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.labelSmallSp)
                    setPadding(0, dp(4f), 0, 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
            }
            toolbar.addView(item, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    @Suppress("unused")
    private fun near(a: Float, b: Float) = abs(a - b) < dp(2f)

    companion object {
        private const val MERGE_HOVER_MS = 380L
    }
}
