package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lumaos.pro.launcher.effects.EffectsDecision
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.theme.LumaComponents
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.LumaTypeScale
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme

/** One row in a [LumaMenu]. */
data class MenuEntry(
    val id: Int,
    val label: String,
    val iconRes: Int? = null,
    /** Pre-rendered icon (app shortcuts); not tinted. */
    val icon: Drawable? = null,
    val destructive: Boolean = false,
    /** Entries with the same group are drawn together; a divider separates groups. */
    val group: Int = 0
)

/**
 * Long-press and edit menus in the active theme.
 *
 * The platform PopupMenu is drawn by the system theme and would ignore
 * LumaOS Pro's palette, typography and corner geometry; this popup follows them
 * while keeping what PopupMenu gave for free: it is focusable (back and
 * outside-touch dismiss it), every row is a 48dp touch target with a content
 * description, and keyboard focus moves through rows.
 */
class LumaMenu(
    private val context: Context,
    private val theme: ResolvedTheme,
    private val effects: EffectsDecision,
    private val motion: MotionPolicy
) {
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    fun show(anchor: View, title: String?, entries: List<MenuEntry>, onSelect: (MenuEntry) -> Unit): PopupWindow {
        val c = theme.colors
        val semantics = theme.semantics
        val components = LumaComponents.of(theme)
        val radius = theme.corner(components.shapes.menuDp) * density
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            background = ThemedDrawables.surface(
                ColorMath.withAlpha(semantics.dialogSurface, theme.surfaceAlpha(effects.translucency)),
                radius, semantics.dialogBorder, dp(components.sizes.borderDp).coerceAtLeast(1)
            )
            elevation = dp(components.surfaces.elevationDp + 4f).toFloat()
            minimumWidth = dp(220f)
        }
        val popup = PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(0))
        popup.isOutsideTouchable = true
        popup.elevation = dp(components.surfaces.elevationDp + 4f).toFloat()

        if (!title.isNullOrBlank()) {
            content.addView(TextView(context).apply {
                text = if (theme.typography.readoutCaps) title.uppercase() else title
                typeface = Typefaces.readout(theme.typography)
                letterSpacing = theme.typography.readoutTracking
                setTextColor(c.onSurfaceMuted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.labelMediumSp)
                setPadding(dp(14f), dp(8f), dp(14f), dp(6f))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            })
        }
        var lastGroup: Int? = null
        for (entry in entries) {
            if (lastGroup != null && entry.group != lastGroup) {
                content.addView(View(context).apply {
                    setBackgroundColor(c.outline)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f).coerceAtLeast(1)).apply {
                        setMargins(dp(10f), dp(4f), dp(10f), dp(4f))
                    }
                })
            }
            lastGroup = entry.group
            content.addView(row(entry) {
                popup.dismiss()
                onSelect(entry)
            })
        }

        // Place above the anchor when there is room, otherwise below; centred
        // horizontally on the anchor and kept on screen.
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        val frame = Rect()
        anchor.getWindowVisibleDisplayFrame(frame)
        val w = content.measuredWidth
        val h = content.measuredHeight
        val margin = dp(8f)
        var x = loc[0] + anchor.width / 2 - w / 2
        x = x.coerceIn(frame.left + margin, (frame.right - w - margin).coerceAtLeast(frame.left + margin))
        val above = loc[1] - h - margin >= frame.top + margin
        val y = if (above) loc[1] - h - margin else (loc[1] + anchor.height + margin).coerceAtMost(frame.bottom - h - margin)
        content.pivotX = (loc[0] + anchor.width / 2 - x).toFloat().coerceIn(0f, w.toFloat())
        content.pivotY = if (above) h.toFloat() else 0f
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)

        if (motion.enabled && !motion.reduced) {
            // Enters from the theme's own popup scale; Void opens a little larger
            // than Aurora, and both honour reduced motion by skipping this block.
            val from = theme.motionProfile.popupScaleFrom
            content.alpha = 0f
            content.scaleX = from
            content.scaleY = from
            SpringAnimator(0f) { p ->
                content.alpha = p.coerceIn(0f, 1f)
                val s = from + (1f - from) * p
                content.scaleX = s
                content.scaleY = s
            }.animateTo(1f, MotionTokens.SNAPPY, motion)
        }
        content.getChildAt(if (title.isNullOrBlank()) 0 else 1)?.requestFocus()
        return popup
    }

    private fun row(entry: MenuEntry, onClick: () -> Unit): View {
        val c = theme.colors
        val tint = if (entry.destructive) c.danger else c.onSurface
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48f)
            setPadding(dp(12f), 0, dp(16f), 0)
            isClickable = true
            isFocusable = true
            contentDescription = entry.label
            background = ThemedDrawables.ripple(
                c.ripple, null,
                theme.corner(LumaComponents.of(theme).shapes.controlDp) * density
            )
            setOnClickListener { onClick() }
            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { marginEnd = dp(14f) }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                when {
                    entry.icon != null -> setImageDrawable(entry.icon)
                    entry.iconRes != null -> {
                        setImageDrawable(ContextCompat.getDrawable(context, entry.iconRes)?.mutate())
                        imageTintList = android.content.res.ColorStateList.valueOf(tint)
                    }
                    else -> visibility = View.INVISIBLE
                }
            }
            addView(iconView)
            addView(TextView(context).apply {
                text = entry.label
                setTextColor(tint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.titleSmallSp)
                typeface = Typefaces.body(theme.typography)
                maxLines = 2
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        }
    }
}
