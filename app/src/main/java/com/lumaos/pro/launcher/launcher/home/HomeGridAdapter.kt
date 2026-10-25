package com.lumaos.pro.launcher.launcher.home

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.launcher.AppCellViewHolder
import com.lumaos.pro.launcher.launcher.AppEntryAdapter
import com.lumaos.pro.launcher.launcher.CellConfig
import com.lumaos.pro.launcher.launcher.IconLoader
import com.lumaos.pro.launcher.launcher.model.HomeItem
import com.lumaos.pro.launcher.launcher.model.HomeLayout
import com.lumaos.pro.launcher.launcher.model.SmartKind
import com.lumaos.pro.launcher.launcher.view.FolderIconDrawable
import java.util.Collections
import kotlin.math.roundToInt

/** How folder plates are drawn in the current theme. */
data class FolderStyle(val plate: Int, val rim: Int, val outline: FloatArray?, val smartMarker: Int) {
    override fun equals(other: Any?): Boolean = other is FolderStyle && other.plate == plate && other.rim == rim &&
        other.smartMarker == smartMarker && other.outline.contentEquals(outline)
    override fun hashCode(): Int = plate * 31 + rim
}

/**
 * Home grid and dock: apps, folders, smart folders and (grid only) widgets.
 *
 * A plain RecyclerView.Adapter over a mutable list rather than a ListAdapter,
 * because edit-mode drag-and-drop reorders items live under the finger; new
 * data arriving mid-drag is held back and applied when the drag ends.
 */
class HomeGridAdapter(
    private val config: () -> CellConfig,
    private val iconLoader: IconLoader,
    private val density: Int,
    private val folderStyle: () -> FolderStyle,
    private val labelFor: (ComponentKey) -> String,
    private val smartMembers: (SmartKind) -> List<ComponentKey>,
    private val widgetHost: WidgetViews?,
    private val listener: Listener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun onItemClick(item: HomeItem, position: Int, view: View)
        fun onItemLongClick(item: HomeItem, position: Int, view: View): Boolean
        fun onWidgetResize(item: HomeItem.Widget, rows: Int, commit: Boolean) {}
    }

    /** Supplies hosted widget views and the height of one grid row. */
    interface WidgetViews {
        fun viewFor(item: HomeItem.Widget): View?
        fun rowHeightPx(): Int
        fun editOutline(): Drawable
        fun handle(): Drawable
        /** Themed frame drawn behind every hosted widget so widgets stay consistent. */
        fun frame(): Drawable? = null
        /** Corner radius of that frame, used to clip the provider's content to it. */
        fun frameRadiusPx(): Float = 0f
    }

    val items = ArrayList<HomeItem>()
    private var pending: List<HomeItem>? = null

    var dragging = false
        private set

    var editMode = false
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    init {
        setHasStableIds(true)
    }

    fun submit(newItems: List<HomeItem>) {
        if (dragging) {
            pending = newItems
            return
        }
        val old = ArrayList(items)
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = identity(old[o]) == identity(newItems[n])
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == newItems[n]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    fun beginDrag() {
        dragging = true
    }

    /** Ends a drag; returns the order the user arranged, then applies any held-back data. */
    fun endDrag(): List<HomeItem> {
        dragging = false
        val arranged = ArrayList(items)
        pending?.let { submit(it) }
        pending = null
        return arranged
    }

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        if (from < to) for (i in from until to) Collections.swap(items, i, i + 1)
        else for (i in from downTo to + 1) Collections.swap(items, i, i - 1)
        notifyItemMoved(from, to)
    }

    fun itemAt(position: Int): HomeItem? = items.getOrNull(position)

    fun positionsFor(key: ComponentKey): List<Int> = items.mapIndexedNotNull { index, item ->
        when (item) {
            is HomeItem.App -> if (item.key == key) index else null
            is HomeItem.Folder -> if (item.children.contains(key)) index else null
            is HomeItem.SmartFolder -> index
            is HomeItem.Widget -> null
        }
    }

    fun isWidget(position: Int): Boolean = items.getOrNull(position) is HomeItem.Widget

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = identity(items[position]).hashCode().toLong()

    override fun getItemViewType(position: Int): Int = if (items[position] is HomeItem.Widget) TYPE_WIDGET else TYPE_CELL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_WIDGET) WidgetHolder(inflater.inflate(R.layout.item_widget, parent, false))
        else AppCellViewHolder(inflater.inflate(R.layout.item_app, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is WidgetHolder && item is HomeItem.Widget) {
            bindWidget(holder, item)
            return
        }
        holder as AppCellViewHolder
        val cfg = config()
        when (item) {
            is HomeItem.App -> {
                val label = labelFor(item.key)
                AppEntryAdapter.bindCell(holder, cfg, iconLoader.get(item.key, density), label)
            }
            is HomeItem.Folder -> {
                val members = item.children.take(FolderIconDrawable.MAX_MEMBERS).mapNotNull { iconLoader.get(it, density) }
                val style = folderStyle()
                AppEntryAdapter.bindCell(
                    holder, cfg, FolderIconDrawable(members, style.plate, style.rim, style.outline), item.name,
                    "${item.name}, folder, ${item.children.size} apps"
                )
            }
            is HomeItem.SmartFolder -> {
                val keys = smartMembers(item.kind)
                val members = keys.take(FolderIconDrawable.MAX_MEMBERS).mapNotNull { iconLoader.get(it, density) }
                val style = folderStyle()
                AppEntryAdapter.bindCell(
                    holder, cfg, FolderIconDrawable(members, style.plate, style.rim, style.outline, style.smartMarker),
                    item.name, "${item.name}, smart folder, ${keys.size} apps"
                )
            }
            is HomeItem.Widget -> Unit
        }
        holder.itemView.tag = (item as? HomeItem.Folder)?.id ?: (item as? HomeItem.SmartFolder)?.id
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) listener.onItemClick(items[pos], pos, holder.icon)
        }
        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            pos != RecyclerView.NO_POSITION && listener.onItemLongClick(items[pos], pos, it)
        }
    }

    private var widgetFrameBackground: Drawable? = null

    /** Re-applies the themed widget frame after a theme change. */
    fun refreshWidgetFrames() {
        widgetFrameBackground = widgetHost?.frame()
        if (widgetFrameBackground != null) notifyItemRangeChanged(0, itemCount)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindWidget(holder: WidgetHolder, item: HomeItem.Widget) {
        val host = widgetHost
        val rowHeight = host?.rowHeightPx() ?: 0
        val height = (rowHeight * item.rows).coerceAtLeast(rowHeight)
        holder.setHeight(height)
        val frameDrawable = widgetFrameBackground ?: host?.frame()
        if (host != null && frameDrawable != null) {
            holder.frame.background = frameDrawable.constantState?.newDrawable(holder.frame.resources)?.mutate()
            val radius = host.frameRadiusPx()
            holder.frame.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            holder.frame.clipToOutline = radius > 0f
        }
        holder.missing.setTextColor(config().labelColor)
        val view = host?.viewFor(item)
        holder.container.removeAllViews()
        if (view != null) {
            (view.parent as? ViewGroup)?.removeView(view)
            holder.container.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            holder.missing.visibility = View.GONE
        } else {
            holder.missing.visibility = View.VISIBLE
        }
        holder.itemView.contentDescription = holder.itemView.context.getString(R.string.widget_description, item.rows)
        holder.outline.visibility = if (editMode) View.VISIBLE else View.GONE
        holder.handle.visibility = if (editMode) View.VISIBLE else View.GONE
        if (editMode && host != null) {
            holder.outline.background = host.editOutline()
            holder.handle.background = host.handle()
        }
        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            pos != RecyclerView.NO_POSITION && listener.onItemLongClick(items[pos], pos, it)
        }
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && editMode) listener.onItemClick(items[pos], pos, it)
        }
        // Drag the handle to resize in whole rows, with the height following the finger.
        var startY = 0f
        var startHeight = 0
        holder.handle.setOnTouchListener { v, ev ->
            if (rowHeight <= 0) return@setOnTouchListener false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = ev.rawY
                    startHeight = holder.itemView.height
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val h = (startHeight + (ev.rawY - startY)).toInt()
                        .coerceIn(rowHeight * HomeLayout.MIN_WIDGET_ROWS, rowHeight * HomeLayout.MAX_WIDGET_ROWS)
                    holder.setHeight(h)
                    listener.onWidgetResize(item, (h / rowHeight.toFloat()).roundToInt(), commit = false)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val rows = (holder.itemView.height / rowHeight.toFloat()).roundToInt()
                        .coerceIn(HomeLayout.MIN_WIDGET_ROWS, HomeLayout.MAX_WIDGET_ROWS)
                    holder.setHeight(rowHeight * rows)
                    listener.onWidgetResize(item, rows, commit = true)
                    true
                }
                else -> false
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is WidgetHolder) holder.container.removeAllViews()
        super.onViewRecycled(holder)
    }

    class WidgetHolder(view: View) : RecyclerView.ViewHolder(view) {
        val frame: FrameLayout = view.findViewById(R.id.widget_frame)
        val container: FrameLayout = view.findViewById(R.id.widget_container)
        val missing: TextView = view.findViewById(R.id.widget_missing)
        val outline: View = view.findViewById(R.id.widget_outline)
        val handle: View = view.findViewById(R.id.widget_resize)

        fun setHeight(h: Int) {
            val lp = itemView.layoutParams
            if (lp.height != h) {
                lp.height = h
                itemView.layoutParams = lp
            }
        }
    }

    companion object {
        const val TYPE_CELL = 0
        const val TYPE_WIDGET = 1

        fun identity(item: HomeItem): String = when (item) {
            is HomeItem.App -> "a:" + item.key.serialize()
            is HomeItem.Folder -> "f:" + item.id
            is HomeItem.SmartFolder -> "s:" + item.id
            is HomeItem.Widget -> "w:" + item.appWidgetId
        }
    }
}
