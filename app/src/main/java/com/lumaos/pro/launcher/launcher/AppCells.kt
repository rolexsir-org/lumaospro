package com.lumaos.pro.launcher.launcher

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
import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.icons.MonogramDrawable
import com.lumaos.pro.launcher.launcher.home.ThemedDrawables
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.LumaSpring
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.LumaTokens

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
    /**
     * Label size from the active theme's token (Signature 10.5 sp, Atmosphere 11,
     * Monolith 9). Null keeps the historical base size, so anything that builds a
     * CellConfig without a resolved theme behaves exactly as before.
     */
    val labelSizeSp: Float? = null,
    /** Theme ripple colour; 0 keeps the platform default background. */
    val rippleColor: Int = 0,
    val cornerPx: Float = LumaTokens.radiusMedium,
    /** Monogram plate colour for an app whose icon could not be resolved. */
    val placeholderFill: Int = 0,
    /** Monogram letter colour. */
    val placeholderText: Int = 0,
    /**
     * Pressed-state scale (`LumaTokens.pressScale = 0.92f`). 1 disables the effect
     * when reduced motion is active.
     */
    val pressScale: Float = LumaTokens.pressScale,
    val pressMs: Int = 90,
    val labelAlpha: Float = LumaTokens.typeAppLabelAlpha,
    /** Icon geometry for the monogram fallback, so it matches the theme's icons. */
    val iconShape: com.lumaos.pro.launcher.ui.theme.IconShape =
        com.lumaos.pro.launcher.ui.theme.IconShape.SQUIRCLE
)

class AppCellViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val iconWrap: View? = view.findViewById(R.id.app_icon_wrap)
    val iconPlaceholder: View? = view.findViewById(R.id.app_icon_placeholder)
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

    fun positionOf(key: com.lumaos.pro.launcher.core.model.ComponentKey): Int =
        currentList.indexOfFirst { it.key == key }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppCellViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppCellViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppCellViewHolder, position: Int) {
        val entry = getItem(position)
        bindCell(holder, config(), iconLoader.get(entry.key, density), entry.label)
        holder.itemView.setOnClickListener { onClick(entry, holder.icon) }
        holder.itemView.setOnLongClickListener {
            LumaHaptics.longPress(it)
            onLongClick(entry, it)
            true
        }
    }

    companion object {
        fun bindCell(
            holder: AppCellViewHolder,
            config: CellConfig,
            icon: Drawable?,
            label: String,
            description: String = label
        ) {
            holder.iconWrap?.let { wrap ->
                val wrapParams = wrap.layoutParams
                if (wrapParams.width != config.iconSizePx || wrapParams.height != config.iconSizePx) {
                    wrapParams.width = config.iconSizePx
                    wrapParams.height = config.iconSizePx
                    wrap.layoutParams = wrapParams
                }
            }
            val iconParams = holder.icon.layoutParams
            if (iconParams.width != config.iconSizePx || iconParams.height != config.iconSizePx) {
                iconParams.width = config.iconSizePx
                iconParams.height = config.iconSizePx
                holder.icon.layoutParams = iconParams
            }
            holder.icon.setImageDrawable(icon)
            // While an icon decodes - or when it can never be resolved - the cell
            // shows the app's initial in the active icon shape rather than a
            // blank square. The app's own artwork is never recoloured.
            holder.iconPlaceholder?.visibility = View.GONE
            if (icon == null) {
                holder.icon.setImageDrawable(
                    MonogramDrawable(
                        label = label,
                        fill = if (config.placeholderFill != 0) config.placeholderFill else 0x33FFFFFF,
                        textColor = if (config.placeholderText != 0) config.placeholderText else 0xFFFFFFFF.toInt(),
                        typeface = config.typeface,
                        shape = config.iconShape,
                        cornerRadiusPx = config.cornerPx
                    )
                )
                holder.icon.background = null
            } else {
                holder.icon.background = null
            }

            val cellParams = holder.itemView.layoutParams
            val targetHeight = config.cellHeightPx ?: ViewGroup.LayoutParams.WRAP_CONTENT
            if (cellParams.height != targetHeight) {
                cellParams.height = targetHeight
                holder.itemView.layoutParams = cellParams
            }

            holder.label.text = label
            val effectiveAlpha = ((ColorMath.alpha(config.labelColor) / 255f) * config.labelAlpha).coerceIn(0f, 1f)
            holder.label.setTextColor(ColorMath.withAlpha(config.labelColor, effectiveAlpha))
            holder.label.textSize = (config.labelSizeSp ?: BASE_LABEL_SP) * config.textScale
            config.typeface?.let { holder.label.typeface = it }
            holder.label.letterSpacing = config.labelTracking
            holder.label.maxLines = 1
            holder.label.visibility = if (config.showLabels) View.VISIBLE else View.GONE
            attachPressFeedback(holder.itemView, config.pressScale, config.pressMs)
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

        const val BASE_LABEL_SP = LumaTokens.typeAppLabelSp
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
 * Press feedback shared by every cell in the grid, dock, drawer and folders.
 *
 * The view shrinks slightly while a finger is down and springs back on release
 * or cancel; the touch listener returns false so click, long-press and drag
 * handling keep working. The whole effect is disabled (scale 1) when the user
 * or the effects policy asks for reduced motion, and it never delays input.
 */
private fun attachPressFeedback(view: View, scale: Float, durationMs: Int) {
    LumaSpring.cancelAll(view)
    view.scaleX = 1f
    view.scaleY = 1f
    view.alpha = 1f
    if (scale >= 1f || scale < 0.85f || durationMs <= 0) {
        view.setOnTouchListener(null)
        return
    }
    view.setOnTouchListener { v, event ->
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                LumaSpring.animate(v, LumaSpring.Property.SCALE, scale)
                LumaSpring.animate(v, LumaSpring.Property.ALPHA, LumaTokens.pressAlpha)
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                LumaSpring.animate(v, LumaSpring.Property.SCALE, 1f)
                LumaSpring.animate(v, LumaSpring.Property.ALPHA, 1f)
            }
        }
        false
    }
}
