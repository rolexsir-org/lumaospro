package com.lumaos.pro.launcher.launcher.home

import com.lumaos.pro.launcher.ui.ThemedUiIcons
import com.lumaos.pro.launcher.ui.theme.ColorMath
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
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.launcher.AppCellViewHolder
import com.lumaos.pro.launcher.launcher.AppEntryAdapter
import com.lumaos.pro.launcher.launcher.CellConfig
import com.lumaos.pro.launcher.launcher.IconLoader
import com.lumaos.pro.launcher.launcher.model.DrawerAction
import com.lumaos.pro.launcher.launcher.model.DrawerModel
import com.lumaos.pro.launcher.launcher.model.DrawerPresentation
import com.lumaos.pro.launcher.launcher.model.DrawerRow

/** Colours and type for drawer headers and list rows. */
data class DrawerRowStyle(
    val headerColor: Int,
    val headerTypeface: Typeface?,
    val headerCaps: Boolean,
    val headerTracking: Float,
    val titleColor: Int,
    val titleTypeface: Typeface? = null,
    val subtitleColor: Int,
    val subtitleTypeface: Typeface? = null,
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

    override fun getItemViewType(position: Int): Int = when (val row = getItem(position)) {
        is DrawerRow.Header -> TYPE_HEADER
        is DrawerRow.App -> if (presentation() == DrawerPresentation.LIST &&
            row.section !in curatedSections
        ) TYPE_APP_LIST else TYPE_APP
        is DrawerRow.Shortcut -> TYPE_SHORTCUT
        is DrawerRow.Action -> TYPE_ROW
        is DrawerRow.Empty -> TYPE_EMPTY
    }

    fun isFullSpan(position: Int): Boolean =
        position in 0 until itemCount && getItemViewType(position) != TYPE_APP && getItemViewType(position) != TYPE_SHORTCUT

    fun rowAt(position: Int): DrawerRow? = currentList.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.item_drawer_header, parent, false) as TextView)
            TYPE_ROW -> RowHolder(inflater.inflate(R.layout.item_drawer_row, parent, false))
            TYPE_EMPTY -> EmptyHolder(inflater.inflate(R.layout.item_drawer_empty, parent, false) as TextView)
            TYPE_APP_LIST -> AppCellViewHolder(inflater.inflate(R.layout.item_drawer_app, parent, false))
            TYPE_SHORTCUT -> AppCellViewHolder(inflater.inflate(R.layout.item_app, parent, false))
            else -> AppCellViewHolder(inflater.inflate(R.layout.item_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = getItem(position)
        val style = rowStyle()
        when (holder) {
            is HeaderHolder -> {
                row as DrawerRow.Header
                val titleRes = when (row.id) {
                    DrawerModel.SECTION_PINNED -> R.string.drawer_pinned
                    DrawerModel.SECTION_RECENT -> R.string.drawer_recent
                    DrawerModel.SECTION_SUGGESTED -> R.string.drawer_suggested
                    DrawerModel.SECTION_ALL -> R.string.drawer_all_apps
                    else -> null
                }
                val title = titleRes?.let { holder.text.context.getString(it) } ?: row.title
                holder.text.text = DrawerModel.headerText(title, style.headerCaps)
                holder.text.setTextColor(style.headerColor)
                holder.text.letterSpacing = style.headerTracking
                style.headerTypeface?.let { holder.text.typeface = it }
                // The short blue dash is OBSIDIAN's section signature, not an icon
                // or an extra accessibility node. Reset it on every bind.
                val curated = row.id == DrawerModel.SECTION_PINNED ||
                    row.id == DrawerModel.SECTION_RECENT || row.id == DrawerModel.SECTION_SUGGESTED
                val marker = if (curated) android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF4B91F7.toInt())
                    cornerRadius = holder.text.resources.displayMetrics.density * 2f
                    setBounds(0, 0, (12 * holder.text.resources.displayMetrics.density).toInt(),
                        (3 * holder.text.resources.displayMetrics.density).toInt().coerceAtLeast(1))
                } else null
                holder.text.compoundDrawablePadding = (8 * holder.text.resources.displayMetrics.density).toInt()
                holder.text.setCompoundDrawablesRelative(marker, null, null, null)
                // Heading semantics without requiring API 28 XML attributes.
                androidx.core.view.ViewCompat.setAccessibilityHeading(holder.text, true)
            }
            is EmptyHolder -> {
                row as DrawerRow.Empty
                holder.text.text = holder.text.context.getString(R.string.empty_no_results, row.query)
                holder.text.setTextColor(style.subtitleColor)
                style.subtitleTypeface?.let { holder.text.typeface = it }
            }
            is AppCellViewHolder -> {
                if (row is DrawerRow.Shortcut) {
                    AppEntryAdapter.bindCell(holder, cellConfig(), shortcutIcon(row), row.ref.label,
                        holder.itemView.context.getString(R.string.drawer_shortcut_description, row.ref.label, row.appLabel))
                    holder.itemView.background = ThemedDrawables.ripple(style.rippleColor, null, style.cornerPx)
                    holder.itemView.setOnClickListener { onClick(row, holder.icon) }
                    holder.itemView.setOnLongClickListener(null)
                    holder.itemView.isLongClickable = false
                    return
                }
                val app = (row as DrawerRow.App).entry
                val config = cellConfig()
                val listRow = holder.itemViewType == TYPE_APP_LIST
                val curatedShelf = presentation() == DrawerPresentation.LIST && row.section in curatedSections
                val drawerConfig = when {
                    listRow -> config.copy(labelSizeSp = maxOf(config.labelSizeSp ?: 0f, 15f), labelAlpha = 1f)
                    curatedShelf -> config.copy(
                        iconSizePx = minOf(config.iconSizePx, (48f * holder.itemView.resources.displayMetrics.density).toInt()),
                        labelSizeSp = maxOf(config.labelSizeSp ?: 0f, 12f), labelAlpha = 1f
                    )
                    else -> config
                }
                AppEntryAdapter.bindCell(holder, drawerConfig, iconLoader.get(app.key, density), app.label)
                // Curated shelves carry a quiet material plate. All rows still
                // use the same launch and long-press handlers.
                val plate = if (curatedShelf) {
                    ThemedDrawables.surface(ColorMath.withAlpha(style.titleColor, 0.055f), style.cornerPx)
                } else null
                holder.itemView.background = ThemedDrawables.ripple(style.rippleColor, plate, style.cornerPx)
                holder.itemView.setOnClickListener { onClick(row, holder.icon) }
                holder.itemView.setOnLongClickListener { onLongClick(row, it); true }
            }
            is RowHolder -> {
                holder.itemView.background = ThemedDrawables.ripple(style.rippleColor, null, style.cornerPx)
                holder.title.setTextColor(style.titleColor)
                holder.subtitle.setTextColor(style.subtitleColor)
                style.titleTypeface?.let { holder.title.typeface = it }
                style.subtitleTypeface?.let { holder.subtitle.typeface = it }
                ThemedUiIcons.bind(holder.action, R.drawable.ic_rx_chevron_right)
                holder.action.imageTintList = android.content.res.ColorStateList.valueOf(style.iconTint)
                holder.action.visibility = View.GONE
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
                        holder.action.visibility = View.VISIBLE
                        val ctx = holder.itemView.context
                        val (titleRes, icon) = when (row.action) {
                            DrawerAction.WEB_SEARCH -> R.string.search_web_for to R.drawable.ic_rx_web
                            DrawerAction.PLAY_STORE -> R.string.search_store_for to R.drawable.ic_rx_store
                        }
                        holder.title.text = ctx.getString(titleRes, row.query)
                        holder.subtitle.visibility = View.GONE
                        holder.icon.setImageDrawable(ThemedUiIcons.drawable(holder.icon.context, icon))
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
    private class EmptyHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    private class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.row_icon)
        val title: TextView = view.findViewById(R.id.row_title)
        val subtitle: TextView = view.findViewById(R.id.row_subtitle)
        val action: ImageView = view.findViewById(R.id.row_action)
    }

    private object Diff : DiffUtil.ItemCallback<DrawerRow>() {
        override fun areItemsTheSame(oldItem: DrawerRow, newItem: DrawerRow) = oldItem.stableKey == newItem.stableKey
        override fun areContentsTheSame(oldItem: DrawerRow, newItem: DrawerRow) = oldItem == newItem
    }

    companion object {
        private val curatedSections = setOf(
            DrawerModel.SECTION_PINNED, DrawerModel.SECTION_RECENT, DrawerModel.SECTION_SUGGESTED
        )
        const val TYPE_HEADER = 0
        const val TYPE_APP = 1
        const val TYPE_ROW = 2
        const val TYPE_APP_LIST = 3
        const val TYPE_EMPTY = 4
        const val TYPE_SHORTCUT = 5
    }
}
