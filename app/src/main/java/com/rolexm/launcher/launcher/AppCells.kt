package com.rolexm.launcher.launcher

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
import com.rolexm.launcher.core.apps.AppEntry
import com.rolexm.launcher.launcher.home.ThemedDrawables

/**
 * Visual parameters shared by the home grid, dock, drawer and folders.
 *
 * [labelShadow] exists because the home grid sits directly on the wallpaper:
 * there, text needs a shadow to survive arbitrary images, whereas inside
 * opaque surfaces it needs theme-coloured text and no shadow.
 */
data class CellConfig(
    val iconSizePx: Int,
    val showLabels: Boolean,
    val textScale: Float,
    val labelColor: Int,
    val labelShadow: Boolean,
    val labelShadowColor: Int = 0xB3000000.toInt(),
    /** Fixed viewport row height for the home grid; null keeps natural sizing. */
    val cellHeightPx: Int? = null,
    val typeface: Typeface? = null,
    val labelTracking: Float = 0f,
    /** Theme ripple colour; 0 keeps the platform default background. */
    val rippleColor: Int = 0,
    val cornerPx: Float = 16f
)

class AppCellViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val icon: ImageView = view.findViewById(R.id.app_icon)
    val label: TextView = view.findViewById(R.id.app_label)
    var boundRipple: Int = 0
}

/**
 * Plain app grid (folders, suggestions).
 *
 * A ListAdapter so that a re-sort or a filter change binds only the rows that
 * actually changed - a full notifyDataSetChanged() on every keystroke is the
 * classic reason launcher search feels laggy.
 */
class AppEntryAdapter(
    private val config: () -> CellConfig,
    private val iconLoader: IconLoader,
    private val density: Int,
    private val onClick: (AppEntry, View) -> Unit,
    private val onLongClick: (AppEntry, View) -> Unit
) : ListAdapter<AppEntry, AppCellViewHolder>(AppEntryDiff) {

    fun entryAt(position: Int): AppEntry? = getItem(position)

    fun positionOf(key: com.rolexm.launcher.core.model.ComponentKey): Int =
        currentList.indexOfFirst { it.key == key }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppCellViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppCellViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppCellViewHolder, position: Int) {
        val entry = getItem(position)
        bindCell(holder, config(), iconLoader.get(entry.key, density), entry.label)
        holder.itemView.setOnClickListener { onClick(entry, holder.icon) }
        holder.itemView.setOnLongClickListener { onLongClick(entry, it); true }
    }

    companion object {
        fun bindCell(
            holder: AppCellViewHolder,
            config: CellConfig,
            icon: Drawable?,
            label: String,
            description: String = label
        ) {
            val iconParams = holder.icon.layoutParams
            if (iconParams.width != config.iconSizePx || iconParams.height != config.iconSizePx) {
                iconParams.width = config.iconSizePx
                iconParams.height = config.iconSizePx
                holder.icon.layoutParams = iconParams
            }
            holder.icon.setImageDrawable(icon)
            // The placeholder disc only shows while an icon is decoding.
            holder.icon.background = if (icon == null) {
                holder.icon.context.getDrawable(R.drawable.bg_icon_placeholder)
            } else null

            val cellParams = holder.itemView.layoutParams
            val targetHeight = config.cellHeightPx ?: ViewGroup.LayoutParams.WRAP_CONTENT
            if (cellParams.height != targetHeight) {
                cellParams.height = targetHeight
                holder.itemView.layoutParams = cellParams
            }

            holder.label.text = label
            holder.label.setTextColor(config.labelColor)
            holder.label.textSize = BASE_LABEL_SP * config.textScale
            config.typeface?.let { holder.label.typeface = it }
            holder.label.letterSpacing = config.labelTracking
            holder.label.visibility = if (config.showLabels) View.VISIBLE else View.GONE
            if (config.labelShadow) {
                holder.label.setShadowLayer(3f, 0f, 1f, config.labelShadowColor)
            } else {
                holder.label.setShadowLayer(0f, 0f, 0f, 0)
            }
            if (config.rippleColor != 0 && holder.boundRipple != config.rippleColor) {
                holder.itemView.background = ThemedDrawables.ripple(config.rippleColor, null, config.cornerPx)
                holder.boundRipple = config.rippleColor
            }
            // One announcement per cell; labels may be hidden visually.
            holder.itemView.contentDescription = description
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
