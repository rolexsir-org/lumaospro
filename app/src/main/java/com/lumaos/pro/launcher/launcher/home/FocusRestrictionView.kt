package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.RadiusRole
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme

/**
 * Native LumaOS Focus Protection screen.
 *
 * Shown instead of launching an app that is restricted during the active
 * Work/Study focus session. Dismissing returns to the launcher; the app is
 * never started. Restrictions are launcher-level (launches made through
 * LumaOS Pro) and release the moment the session ends or the user switches
 * to an unrestricted context.
 */
class FocusRestrictionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val isShowing: Boolean get() = visibility == VISIBLE

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var message: TextView
    private lateinit var note: TextView
    private lateinit var dismissButton: TextView
    private var built = false

    init {
        visibility = GONE
        isFocusable = true
        isClickable = true
        // Tapping the scrim must not leak through to icons underneath.
        setOnClickListener { /* consumed */ }
    }

    fun show(
        theme: ResolvedTheme,
        appLabel: String,
        contextTitle: String,
        onDismiss: () -> Unit,
        onManage: () -> Unit,
        onUnlock: (() -> Unit)? = null,
        unlockLabel: String? = null
    ) {
        if (!built) {
            build()
            built = true
        }
        applyTheme(theme)
        message.text = context.getString(R.string.focus_restriction_message, appLabel, contextTitle)
        contentDescription = context.getString(R.string.focus_restriction_title) + ". " + message.text
        dismissButton.setOnClickListener { onDismiss() }
        findViewWithTag<View>(TAG_MANAGE)?.setOnClickListener { onManage() }
        findViewWithTag<TextView>(TAG_UNLOCK)?.apply {
            if (onUnlock != null && !unlockLabel.isNullOrBlank()) {
                text = unlockLabel
                setOnClickListener { onUnlock() }
                visibility = VISIBLE
            } else {
                setOnClickListener(null)
                visibility = GONE
            }
        }
        visibility = VISIBLE
        // Announce to accessibility services and take focus so Back/dismiss is reachable.
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        dismissButton.requestFocus()
    }

    fun hide() {
        visibility = GONE
    }

    private fun build() {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28f), dp(32f), dp(28f), dp(28f))
        }
        icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_rx_focus)
            layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f)).apply {
                bottomMargin = dp(16f)
            }
        }
        title = TextView(context).apply {
            text = context.getString(R.string.focus_restriction_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10f) }
        }
        message = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10f) }
        }
        note = TextView(context).apply {
            text = context.getString(R.string.focus_restriction_note)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(22f) }
        }
        dismissButton = TextView(context).apply {
            text = context.getString(R.string.focus_restriction_dismiss)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            minimumHeight = dp(52f)
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) }
        }
        val manageButton = TextView(context).apply {
            tag = TAG_MANAGE
            text = context.getString(R.string.focus_restriction_manage)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            minimumHeight = dp(48f)
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) }
        }
        // Emergency temporary unlock. Hidden unless the host passes a handler:
        // layer 1 (HomeActivity) and layer 2 (FocusRestrictionActivity) both
        // offer it with the user's configured duration.
        val unlockButton = TextView(context).apply {
            tag = TAG_UNLOCK
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            minimumHeight = dp(48f)
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = GONE
        }
        card.addView(icon)
        card.addView(title)
        card.addView(message)
        card.addView(note)
        card.addView(dismissButton)
        card.addView(manageButton)
        card.addView(unlockButton)
        addView(
            card,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                leftMargin = dp(32f)
                rightMargin = dp(32f)
            }
        )
    }

    private fun applyTheme(theme: ResolvedTheme) {
        val c = theme.colors
        setBackgroundColor(ColorMath.withAlpha(ColorMath.BLACK, 0.55f))
        (getChildAt(0) as LinearLayout).background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(theme.radius(RadiusRole.LARGE)).toFloat()
            setColor(c.surface1)
            setStroke(dp(1f), c.outline)
        }
        icon.imageTintList = android.content.res.ColorStateList.valueOf(c.accent)
        title.setTextColor(c.onSurface)
        message.setTextColor(c.onSurface)
        note.setTextColor(c.onSurfaceMuted)
        dismissButton.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14f).toFloat()
            setColor(c.accent)
        }
        dismissButton.setTextColor(c.onAccent)
        findViewWithTag<TextView>(TAG_MANAGE)?.apply {
            setTextColor(c.accent)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14f).toFloat()
                setColor(ColorMath.withAlpha(c.accent, 0.12f))
            }
        }
        findViewWithTag<TextView>(TAG_UNLOCK)?.apply {
            setTextColor(c.onSurfaceMuted)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14f).toFloat()
                setColor(ColorMath.withAlpha(c.onSurface, 0.08f))
            }
        }
    }

    private companion object {
        const val TAG_MANAGE = "focus_manage"
        const val TAG_UNLOCK = "focus_unlock"
    }
}
