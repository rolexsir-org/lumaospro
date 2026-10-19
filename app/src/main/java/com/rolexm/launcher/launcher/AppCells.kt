package com.rolexm.launcher.launcher

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
import com.rolexm.launcher.core.apps.AppEntry
import com.rolexm.launcher.core.model.ComponentKey
import com.rolexm.launcher.launcher.model.HomeItem
import com.rolexm.launcher.launcher.view.FolderIconDrawable

/**
 * Visual parameters shared by the home grid, dock and drawer.
 *
 * [labelShadow] exists because the home grid sits directly on the wallpaper:
 * there, text needs a shadow to survive arbitrary images, whereas inside the
 * opaque drawer it needs theme-coloured text and no shadow.
 */
data class CellConfig(
    val iconSizePx: Int,
    val showLabels: Boolean,
    val textScale: Float,
    val labelColor: Int,
    val labelShadow: Boolean
)

class AppCellViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val icon: ImageView = view.findViewById(R.id.app_icon)
    val label: TextView = view.findViewById(R.id.app_label)
}

/**
 * Drawer grid.
 *
 * A ListAdapter so that a re-sort or a filter change binds only the rows that
 * actually changed - a full notifyDataSetChanged() on every keystroke is the
 * classic reason launcher search feels laggy.
 */
class AppEntryAdapter(
    private val config: () -> CellConfig,
    private val iconLoader: IconLoader,
    private val density: Int,
    private val onClick: (AppEntry) -> Unit,
    private val onLongClick: (AppEntry, View) -> Unit
) : ListAdapter<AppEntry, AppCellViewHolder>(AppEntryDiff) {

    fun entryAt(position: Int): AppEntry? = getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppCellViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppCellViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppCellViewHolder, position: Int) {
        val entry = getItem(position)
        val cfg = config()
        bindCell(holder, cfg, iconLoader.get(entry.key, density), entry.label)
        holder.itemView.setOnClickListener { onClick(entry) }
        holder.itemView.setOnLongClickListener { onLongClick(entry, it); true }
    }

    companion object {
        fun bindCell(
            holder: AppCellViewHolder,
            config: CellConfig,
            icon: Drawable?,
            label: String
        ) {
            val iconParams = holder.icon.layoutParams
            if (iconParams.width != config.iconSizePx || iconParams.height != config.iconSizePx) {
                iconParams.width = config.iconSizePx
                iconParams.height = config.iconSizePx
                holder.icon.layoutParams = iconParams
            }
            holder.icon.setImageDrawable(icon)

            holder.label.text = label
            holder.label.setTextColor(config.labelColor)
            holder.label.textSize = BASE_LABEL_SP * config.textScale
            holder.label.visibility = if (config.showLabels) View.VISIBLE else View.GONE
            if (config.labelShadow) {
                holder.label.setShadowLayer(3f, 0f, 1f, SHADOW_COLOR)
            } else {
                holder.label.setShadowLayer(0f, 0f, 0f, 0)
            }
        }

        const val BASE_LABEL_SP = 12f
        const val SHADOW_COLOR = 0xB3000000.toInt()
    }
}

private object AppEntryDiff : DiffUtil.ItemCallback<AppEntry>() {
    override fun areItemsTheSame(oldItem: AppEntry, newItem: AppEntry): Boolean =
        oldItem.key == newItem.key

    override fun areContentsTheSame(oldItem: AppEntry, newItem: AppEntry): Boolean =
        oldItem == newItem
}

/**
 * Home grid and dock: renders both plain apps and folders.
 */
class HomeItemAdapter(
    private val config: () -> CellConfig,
    private val iconLoader: IconLoader,
    private val density: Int,
    private val onClick: (HomeItem, Int) -> Unit,
    private val onLongClick: (HomeItem, Int, View) -> Unit
) : ListAdapter<HomeItem, AppCellViewHolder>(HomeItemDiff) {

    fun itemAt(position: Int): HomeItem? = getItem(position)

    /**
     * Positions that display [key] directly or inside a folder. Used to repaint
     * the right cells when a background icon decode finishes.
     */
    fun positionsFor(key: ComponentKey): List<Int> =
        currentList.mapIndexedNotNull { index, item ->
            when (item) {
                is HomeItem.App -> if (item.key == key) index else null
                is HomeItem.Folder -> if (item.children.contains(key)) index else null
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppCellViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppCellViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppCellViewHolder, position: Int) {
        val item = getItem(position)
        val cfg = config()
        when (item) {
            is HomeItem.App -> {
                AppEntryAdapter.bindCell(holder, cfg, iconLoader.get(item.key, density), resolveLabel(item.key))
            }
            is HomeItem.Folder -> {
                val members = item.children.take(FolderIconDrawable.MAX_MEMBERS)
                    .mapNotNull { iconLoader.get(it, density) }
                val drawable = FolderIconDrawable(members)
                AppEntryAdapter.bindCell(holder, cfg, drawable, item.name)
            }
        }
        holder.itemView.setOnClickListener { onClick(item, holder.bindingAdapterPosition) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item, holder.bindingAdapterPosition, it)
            true
        }
    }

    /** Folder members are stored as keys; the label comes from the live list. */
    var labelResolver: (ComponentKey) -> String = { it.packageName }

    private fun resolveLabel(key: ComponentKey): String = labelResolver(key)
}

private object HomeItemDiff : DiffUtil.ItemCallback<HomeItem>() {
    override fun areItemsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean = when {
        oldItem is HomeItem.App && newItem is HomeItem.App -> oldItem.key == newItem.key
        oldItem is HomeItem.Folder && newItem is HomeItem.Folder -> oldItem.id == newItem.id
        else -> false
    }

    override fun areContentsTheSame(oldItem: HomeItem, newItem: HomeItem): Boolean =
        oldItem == newItem
}
