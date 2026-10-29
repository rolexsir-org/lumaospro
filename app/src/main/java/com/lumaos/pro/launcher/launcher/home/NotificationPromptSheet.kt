package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.effects.EffectsDecision
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme

/**
 * Minimal single-prompt optional Notification Access bottom sheet (Phase 6).
 *
 * Shown AT MOST ONCE after LumaOS Pro becomes the default launcher if
 * Notification Listener access is not yet granted. Never nags, never blocks
 * home screen usage, and animates purely on [LumaTokens] glass + spring tokens.
 */
class NotificationPromptSheet(
    private val context: Context,
    private val root: FrameLayout,
    private val onDepthProgress: (Float) -> Unit
) {
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private var overlay: FrameLayout? = null
    private var spring: SpringAnimator? = null
    private var openState: Boolean = false

    val isShowing: Boolean get() = openState

    fun show(
        theme: ResolvedTheme,
        effects: EffectsDecision,
        motion: MotionPolicy,
        bottomInset: Int,
        onSetUp: () -> Unit,
        onSkip: () -> Unit
    ) {
        if (openState) return
        openState = true

        val palette = GlassViews.palette(theme, effects)
        val sheetSpec = palette[GlassRole.SHEET]
        val controlSpec = palette[GlassRole.SETTINGS_CONTROL]
        val colors = sheetSpec.rest

        val ov = overlay ?: FrameLayout(context).apply {
            LumaDepthStack.bindLayerElevation(this, LumaTokens.depthModal, density)
            root.addView(this, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            this@NotificationPromptSheet.overlay = this
        }
        ov.removeAllViews()
        ov.visibility = View.VISIBLE

        val scrim = View(context).apply {
            setBackgroundColor(ColorMath.withAlpha(0xFF000000.toInt(), LumaTokens.folderScrimAlpha))
            setOnClickListener {
                LumaHaptics.tap(this)
                dismiss { onSkip() }
            }
            contentDescription = context.getString(R.string.onboarding_skip)
        }
        ov.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setPadding(
                dp(LumaTokens.screenMarginHorizontal),
                dp(LumaTokens.spaceXL),
                dp(LumaTokens.screenMarginHorizontal),
                dp(LumaTokens.spaceXL) + bottomInset
            )
        }
        GlassViews.apply(
            view = card,
            spec = sheetSpec,
            density = density,
            ripple = false,
            clipToOutline = false,
            depth = LumaTokens.depthModal
        )

        card.addView(TextView(context).apply {
            text = context.getString(R.string.music_access_title)
            setTextColor(colors.content)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeTitleSp)
            typeface = Typefaces.title(theme.typography)
        })

        card.addView(TextView(context).apply {
            text = context.getString(R.string.music_access_disclosure)
            setTextColor(ColorMath.withAlpha(colors.content, LumaTokens.typeCaptionAlpha))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeBodySp)
            typeface = Typefaces.body(theme.typography)
            setPadding(0, dp(LumaTokens.spaceXS), 0, dp(LumaTokens.spaceLG))
        })

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val skipButton = TextView(context).apply {
            text = context.getString(R.string.onboarding_skip)
            gravity = Gravity.CENTER
            minimumHeight = dp(LumaTokens.minTouchTarget)
            setTextColor(colors.content)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeBodySp)
            typeface = Typefaces.label(theme.typography)
            background = GlassViews.drawable(controlSpec, density, ripple = true, depth = LumaTokens.depthModal)
            isClickable = true
            isFocusable = true
            contentDescription = text
            setOnClickListener {
                LumaHaptics.tap(this)
                dismiss { onSkip() }
            }
        }

        val setUpButton = TextView(context).apply {
            text = context.getString(R.string.music_access_open)
            gravity = Gravity.CENTER
            minimumHeight = dp(LumaTokens.minTouchTarget)
            setTextColor(theme.colors.onAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeBodySp)
            typeface = Typefaces.title(theme.typography)
            background = ThemedDrawables.ripple(
                theme.colors.onAccent and 0x33FFFFFF,
                ThemedDrawables.surface(theme.colors.accent, LumaTokens.radiusSmall * density),
                LumaTokens.radiusSmall * density
            )
            isClickable = true
            isFocusable = true
            contentDescription = text
            setOnClickListener {
                LumaHaptics.confirm(this)
                dismiss {
                    onSetUp()
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
            }
        }

        buttonRow.addView(
            skipButton,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(LumaTokens.spaceSM)
            }
        )
        buttonRow.addView(
            setUpButton,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        card.addView(buttonRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        ov.addView(
            card,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        )

        card.post {
            val travel = card.height.takeIf { it > 0 }?.toFloat() ?: dp(260f).toFloat()
            LumaHaptics.snap(card)
            val s = SpringAnimator(0f) { p ->
                val clamped = p.coerceIn(0f, 1f)
                card.translationY = (1f - p) * travel
                scrim.alpha = clamped
                onDepthProgress(clamped)
            }
            spring = s
            s.animateTo(1f, MotionTokens.LUMA, motion)
        }
    }

    fun dismiss(after: (() -> Unit)? = null) {
        if (!openState) return
        openState = false
        val s = spring
        if (s == null) {
            hideImmediately()
            after?.invoke()
            return
        }
        s.animateTo(0f, MotionTokens.LUMA, MotionPolicy.DEFAULT) {
            if (!openState) hideImmediately()
            after?.invoke()
        }
    }

    private fun hideImmediately() {
        openState = false
        spring?.cancel()
        spring = null
        overlay?.let { ov ->
            ov.removeAllViews()
            ov.visibility = View.GONE
        }
        onDepthProgress(0f)
    }

    fun shutdown() {
        hideImmediately()
        overlay?.let { root.removeView(it) }
        overlay = null
    }
}
