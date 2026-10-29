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
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lumaos.pro.launcher.effects.EffectsDecision
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.LumaSpring
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.GlassRole
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
    private val glassPalette = GlassViews.palette(theme, effects)
    private fun dp(v: Float) = (v * density).toInt()

    fun show(anchor: View, title: String?, entries: List<MenuEntry>, onSelect: (MenuEntry) -> Unit): PopupWindow {
        val c = theme.colors
        val menuSpec = glassPalette[GlassRole.MENU]
        val menuColors = menuSpec.rest
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            minimumWidth = dp(220f)
        }
        val scroll = ScrollView(context).apply {
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        GlassViews.apply(scroll, menuSpec, density, ripple = false)
        val popup = PopupWindow(scroll, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(0))
        popup.isOutsideTouchable = true
        popup.elevation = menuSpec.elevationDp * density

        if (!title.isNullOrBlank()) {
            content.addView(TextView(context).apply {
                text = if (theme.typography.readoutCaps) title.uppercase() else title
                typeface = Typefaces.readout(theme.typography)
                letterSpacing = theme.typography.readoutTracking
                setTextColor(menuColors.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.labelMediumSp)
                setPadding(dp(14f), dp(8f), dp(14f), dp(6f))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            })
        }
        var lastGroup: Int? = null
        val rowViews = ArrayList<View>(entries.size)
        for (entry in entries) {
            if (lastGroup != null && entry.group != lastGroup) {
                content.addView(View(context).apply {
                    setBackgroundColor(menuColors.edgeTop)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f).coerceAtLeast(1)).apply {
                        setMargins(dp(10f), dp(4f), dp(10f), dp(4f))
                    }
                })
            }
            lastGroup = entry.group
            val r = row(entry) {
                if (entry.destructive) LumaHaptics.confirm(anchor) else LumaHaptics.tap(anchor)
                popup.dismiss()
                onSelect(entry)
            }
            rowViews += r
            content.addView(r)
        }

        // Measure against the visible frame before placing the popup. Long
        // menus and large fonts scroll instead of extending beyond the screen.
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val frame = Rect()
        anchor.getWindowVisibleDisplayFrame(frame)
        if (frame.isEmpty) {
            val metrics = context.resources.displayMetrics
            frame.set(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val margin = dp(8f)
        val maxWidth = (frame.width() - 2 * margin).coerceAtLeast(1)
        val maxHeight = (frame.height() - 2 * margin).coerceAtLeast(1)
        content.minimumWidth = minOf(dp(220f), maxWidth)
        scroll.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
        )
        val w = scroll.measuredWidth.coerceAtLeast(1)
        val h = scroll.measuredHeight.coerceAtLeast(1)
        popup.width = w
        popup.height = h
        val x = (loc[0] + anchor.width / 2 - w / 2)
            .coerceIn(frame.left + margin, (frame.right - w - margin).coerceAtLeast(frame.left + margin))
        val above = loc[1] - h - margin >= frame.top + margin
        val desiredY = if (above) loc[1] - h - margin else loc[1] + anchor.height + margin
        val y = desiredY.coerceIn(frame.top + margin, (frame.bottom - h - margin).coerceAtLeast(frame.top + margin))
        scroll.pivotX = (loc[0] + anchor.width / 2 - x).toFloat().coerceIn(0f, w.toFloat())
        scroll.pivotY = if (above) h.toFloat() else 0f
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)

        if (motion.enabled && !motion.reduced) {
            val from = theme.motionProfile.popupScaleFrom
            scroll.alpha = 0f
            scroll.scaleX = from
            scroll.scaleY = from
            SpringAnimator(0f) { p ->
                scroll.alpha = p.coerceIn(0f, 1f)
                val s = from + (1f - from) * p
                scroll.scaleX = s
                scroll.scaleY = s
            }.animateTo(1f, MotionTokens.LUMA, motion)
            val cancelStagger = LumaSpring.staggerReveal(rowViews, motion)
            popup.setOnDismissListener { cancelStagger.run() }
        }
        content.getChildAt(if (title.isNullOrBlank()) 0 else 1)?.requestFocus()
        return popup
    }

    private fun row(entry: MenuEntry, onClick: () -> Unit): View {
        val c = theme.colors
        val rowColors = glassPalette[GlassRole.SETTINGS_CONTROL].rest
        val tint = if (entry.destructive) c.danger else rowColors.content
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48f)
            setPadding(dp(12f), 0, dp(16f), 0)
            isClickable = true
            isFocusable = true
            contentDescription = entry.label
            background = GlassViews.drawable(
                glassPalette[GlassRole.SETTINGS_CONTROL],
                density,
                ripple = true
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
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        }
    }
}
