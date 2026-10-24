package com.rolexm.launcher.launcher.home

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rolexm.launcher.R
import com.rolexm.launcher.launcher.AppCellViewHolder
import com.rolexm.launcher.launcher.AppEntryAdapter
import com.rolexm.launcher.launcher.CellConfig
import com.rolexm.launcher.launcher.IconLoader
import com.rolexm.launcher.launcher.model.DrawerAction
import com.rolexm.launcher.launcher.model.DrawerModel
import com.rolexm.launcher.launcher.model.DrawerPresentation
import com.rolexm.launcher.launcher.model.DrawerRow

/** Colours and type for drawer headers and list rows. */
data class DrawerRowStyle(
    val headerColor: Int,
    val headerTypeface: Typeface?,
    val headerCaps: Boolean,
    val headerTracking: Float,
    val titleColor: Int,
    val subtitleColor: Int,
    val iconTint: Int,
    val rippleColor: Int,
    val cornerPx: Float
)

/**
 * Sectioned drawer list: headers, app cells (one span), and full-width rows
 * for shortcuts and "search elsewhere" actions. A ListAdapter, so typing in
 * search binds only rows that changed.
 */
class DrawerAdapter(
    private val cellConfig: () -> CellConfig,
    private val presentation: () -> DrawerPresentation,
    private val rowStyle: () -> DrawerRowStyle,
    private val iconLoader: IconLoader,
    private val density: Int,
    private val shortcutIcon: (DrawerRow.Shortcut) -> Drawable?,
    private val onClick: (DrawerRow, View) -> Unit,
    private val onLongClick: (DrawerRow, View) -> Unit
) : ListAdapter<DrawerRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DrawerRow.Header -> TYPE_HEADER
        is DrawerRow.App -> if (presentation() == DrawerPresentation.LIST) TYPE_APP_LIST else TYPE_APP
        is DrawerRow.Shortcut, is DrawerRow.Action -> TYPE_ROW
    }

    fun isFullSpan(position: Int): Boolean =
        position in 0 until itemCount && getItemViewType(position) != TYPE_APP

    fun rowAt(position: Int): DrawerRow? = currentList.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.item_drawer_header, parent, false) as TextView)
            TYPE_ROW -> RowHolder(inflater.inflate(R.layout.item_drawer_row, parent, false))
            TYPE_APP_LIST -> AppCellViewHolder(inflater.inflate(R.layout.item_drawer_app, parent, false))
            else -> AppCellViewHolder(inflater.inflate(R.layout.item_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = getItem(position)
        val style = rowStyle()
        when (holder) {
            is HeaderHolder -> {
                row as DrawerRow.Header
                val title = if (row.id == DrawerModel.SECTION_PINNED) {
                    holder.text.context.getString(R.string.drawer_pinned)
                } else row.title
                holder.text.text = DrawerModel.headerText(title, style.headerCaps)
                holder.text.setTextColor(style.headerColor)
                holder.text.letterSpacing = style.headerTracking
                style.headerTypeface?.let { holder.text.typeface = it }
            }
            is AppCellViewHolder -> {
                val app = (row as DrawerRow.App).entry
                AppEntryAdapter.bindCell(holder, cellConfig(), iconLoader.get(app.key, density), app.label)
                holder.itemView.setOnClickListener { onClick(row, holder.icon) }
                holder.itemView.setOnLongClickListener { onLongClick(row, it); true }
            }
            is RowHolder -> {
                holder.itemView.background = ThemedDrawables.ripple(style.rippleColor, null, style.cornerPx)
                holder.title.setTextColor(style.titleColor)
                holder.subtitle.setTextColor(style.subtitleColor)
                when (row) {
                    is DrawerRow.Shortcut -> {
                        holder.title.text = row.ref.label
                        holder.subtitle.text = row.appLabel
                        holder.subtitle.visibility = View.VISIBLE
                        holder.icon.imageTintList = null
                        holder.icon.setImageDrawable(shortcutIcon(row))
                        holder.itemView.contentDescription = "${row.ref.label}, ${row.appLabel} shortcut"
                    }
                    is DrawerRow.Action -> {
                        val ctx = holder.itemView.context
                        val (titleRes, icon) = when (row.action) {
                            DrawerAction.WEB_SEARCH -> R.string.search_web_for to R.drawable.ic_rx_web
                            DrawerAction.PLAY_STORE -> R.string.search_store_for to R.drawable.ic_rx_store
                        }
                        holder.title.text = ctx.getString(titleRes, row.query)
                        holder.subtitle.visibility = View.GONE
                        holder.icon.setImageResource(icon)
                        holder.icon.imageTintList = android.content.res.ColorStateList.valueOf(style.iconTint)
                        holder.itemView.contentDescription = holder.title.text
                    }
                    else -> Unit
                }
                holder.itemView.setOnClickListener { onClick(row, holder.icon) }
                holder.itemView.setOnLongClickListener { onLongClick(row, it); true }
            }
        }
    }

    private class HeaderHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    private class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.row_icon)
        val title: TextView = view.findViewById(R.id.row_title)
        val subtitle: TextView = view.findViewById(R.id.row_subtitle)
    }

    private object Diff : DiffUtil.ItemCallback<DrawerRow>() {
        override fun areItemsTheSame(oldItem: DrawerRow, newItem: DrawerRow) = oldItem.stableKey == newItem.stableKey
        override fun areContentsTheSame(oldItem: DrawerRow, newItem: DrawerRow) = oldItem == newItem
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_APP = 1
        const val TYPE_ROW = 2
        const val TYPE_APP_LIST = 3
    }
}
