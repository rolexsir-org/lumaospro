package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.effects.EffectsDecision
import com.lumaos.pro.launcher.icons.LivingIconCategory
import com.lumaos.pro.launcher.icons.LivingIconId
import com.lumaos.pro.launcher.icons.LivingIconManager
import com.lumaos.pro.launcher.icons.LivingIconOverride
import com.lumaos.pro.launcher.icons.LivingIconResolver
import com.lumaos.pro.launcher.launcher.IconLoader
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.GlassSpec
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import com.lumaos.pro.launcher.ui.theme.LumaTypeScale
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme

/**
 * Glass bottom-sheet picker allowing the user to customize an individual app's
 * icon: Automatic (verified Living Icon or native fallback), App's original
 * icon, or any of the 30 bundled LumaOS Pro Living Icons.
 *
 * Uses [SpringAnimator] exclusively for open/close choreography and loads all
 * icon previews off the main thread through [IconLoader.submit].
 */
class LivingIconPickerSheet(
    private val context: Context,
    private val root: FrameLayout,
    private val theme: ResolvedTheme,
    private val effects: EffectsDecision,
    private val motion: MotionPolicy,
    private val bottomInset: Int,
    private val livingIcons: LivingIconManager,
    private val iconLoader: IconLoader,
    private val systemIconProvider: (ComponentKey, Int) -> Drawable?,
    private val onDepthProgress: ((Float) -> Unit)? = null
) {
    private val density = context.resources.displayMetrics.density
    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val glassPalette = GlassViews.palette(theme, effects)
    private var overlay: FrameLayout? = null
    private var sheetSpring: SpringAnimator? = null
    private var openState: Boolean = false

    val isShowing: Boolean get() = openState

    private fun dp(v: Float): Int = (v * density).toInt()

    fun show(
        key: ComponentKey,
        appLabel: String,
        currentOverride: LivingIconOverride?,
        onSelection: (LivingIconOverride?) -> Unit
    ) {
        dismissImmediately()
        openState = true

        val sheetSpec = glassPalette[GlassRole.SHEET]
        val colors = sheetSpec.rest
        val controlSpec = glassPalette[GlassRole.SETTINGS_CONTROL]
        val controlColors = controlSpec.rest

        val host = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
        }
        val scrim = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ColorMath.withAlpha(0xFF000000.toInt(), 0.46f))
            alpha = 0f
            setOnClickListener { dismiss() }
        }
        host.addView(scrim)

        val maxHeight = (context.resources.displayMetrics.heightPixels * 0.76f).toInt()
        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20f)
            setPadding(pad, dp(16f), pad, pad + bottomInset)
            GlassViews.apply(
                view = this,
                spec = sheetSpec,
                density = density,
                ripple = false,
                depth = LumaTokens.depthModal,
                radiusOverrideDp = LumaTokens.radiusLarge
            )
            isClickable = true
        }

        val handle = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36f), dp(4f)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(14f)
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(2f).toFloat()
                setColor(ColorMath.withAlpha(colors.muted, 0.45f))
            }
        }
        sheet.addView(handle)

        sheet.addView(TextView(context).apply {
            text = context.getString(R.string.icon_override_title, appLabel)
            setTextColor(colors.content)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.titleMediumSp)
            typeface = Typefaces.title(theme.typography)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { topMargin = dp(12f) }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        sheet.addView(scroll)

        val defaultId = LivingIconResolver.resolveDefault(key.packageName, key.className)
        val autoSubtitle = if (defaultId != null) {
            context.getString(R.string.icon_override_auto_living_desc, defaultId.displayName)
        } else {
            context.getString(R.string.icon_override_auto_system_desc)
        }

        content.addView(
            buildOptionRow(
                title = context.getString(R.string.icon_override_auto),
                subtitle = autoSubtitle,
                selected = currentOverride == null,
                controlSpec = controlSpec,
                loadDrawable = {
                    if (defaultId != null) {
                        livingIcons.drawableForId(defaultId, dp(42f))
                    } else {
                        systemIconProvider(key, densityDpi)
                    }
                },
                onClick = {
                    LumaHaptics.tap(sheet)
                    onSelection(null)
                    dismiss()
                }
            )
        )

        content.addView(
            buildOptionRow(
                title = context.getString(R.string.icon_override_system),
                subtitle = context.getString(R.string.icon_override_system_desc),
                selected = currentOverride == LivingIconOverride.UseSystem,
                controlSpec = controlSpec,
                loadDrawable = { systemIconProvider(key, densityDpi) },
                onClick = {
                    LumaHaptics.tap(sheet)
                    onSelection(LivingIconOverride.UseSystem)
                    dismiss()
                }
            )
        )

        content.addView(buildSectionLabel(context.getString(R.string.icon_override_section_core), controlColors.muted))
        LivingIconId.byCategory(LivingIconCategory.CORE).forEach { id ->
            val selected = (currentOverride as? LivingIconOverride.UseLiving)?.icon == id
            content.addView(
                buildOptionRow(
                    title = id.displayName,
                    subtitle = null,
                    selected = selected,
                    controlSpec = controlSpec,
                    loadDrawable = { livingIcons.drawableForId(id, dp(42f)) },
                    onClick = {
                        LumaHaptics.tap(sheet)
                        onSelection(LivingIconOverride.UseLiving(id))
                        dismiss()
                    }
                )
            )
        }

        content.addView(buildSectionLabel(context.getString(R.string.icon_override_section_popular), controlColors.muted))
        LivingIconId.byCategory(LivingIconCategory.POPULAR).forEach { id ->
            val selected = (currentOverride as? LivingIconOverride.UseLiving)?.icon == id
            content.addView(
                buildOptionRow(
                    title = id.displayName,
                    subtitle = null,
                    selected = selected,
                    controlSpec = controlSpec,
                    loadDrawable = { livingIcons.drawableForId(id, dp(42f)) },
                    onClick = {
                        LumaHaptics.tap(sheet)
                        onSelection(LivingIconOverride.UseLiving(id))
                        dismiss()
                    }
                )
            )
        }

        val sheetParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            maxHeight,
            Gravity.BOTTOM
        )
        sheet.translationY = maxHeight.toFloat()
        host.addView(sheet, sheetParams)
        root.addView(host)
        overlay = host

        val spring = SpringAnimator(0f) { p ->
            val clamped = p.coerceIn(0f, 1f)
            scrim.alpha = clamped
            sheet.translationY = maxHeight * (1f - p)
            onDepthProgress?.invoke(clamped)
        }
        sheetSpring = spring
        spring.animateTo(1f, MotionTokens.LUMA, motion)
    }

    private fun buildSectionLabel(text: String, color: Int): View =
        TextView(context).apply {
            this.text = if (theme.typography.readoutCaps) text.uppercase() else text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.labelMediumSp)
            letterSpacing = theme.typography.readoutTracking
            typeface = Typefaces.readout(theme.typography)
            setPadding(dp(4f), dp(14f), dp(4f), dp(6f))
        }

    private fun buildOptionRow(
        title: String,
        subtitle: String?,
        selected: Boolean,
        controlSpec: GlassSpec,
        loadDrawable: () -> Drawable?,
        onClick: () -> Unit
    ): View {
        val stateColors = if (selected) controlSpec.states.selected else controlSpec.states.rest
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = dp(12f)
            val vPad = dp(10f)
            setPadding(hPad, vPad, hPad, vPad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6f) }
            GlassViews.apply(
                view = this,
                spec = controlSpec,
                density = density,
                ripple = true
            )
            isSelected = selected
            setOnClickListener { onClick() }
        }

        val thumb = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f)).apply { marginEnd = dp(12f) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        iconLoader.submit {
            val d = runCatching { loadDrawable() }.getOrNull()
            if (d != null) {
                thumb.post { if (openState) thumb.setImageDrawable(d) }
            }
        }
        row.addView(thumb)

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        labels.addView(TextView(context).apply {
            text = title
            setTextColor(stateColors.content)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodyMediumSp)
            typeface = Typefaces.body(theme.typography)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        if (!subtitle.isNullOrBlank()) {
            labels.addView(TextView(context).apply {
                text = subtitle
                setTextColor(stateColors.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodySmallSp)
                typeface = Typefaces.body(theme.typography)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        row.addView(labels)

        if (selected) {
            row.addView(TextView(context).apply {
                text = "✓"
                setTextColor(theme.colors.accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.titleSmallSp)
                setPadding(dp(8f), 0, dp(4f), 0)
            })
        }
        return row
    }

    fun dismiss() {
        if (!openState) return
        val host = overlay ?: return
        openState = false
        val spring = sheetSpring
        if (spring == null || !motion.enabled) {
            dismissImmediately()
            return
        }
        spring.animateTo(0f, MotionTokens.LUMA, motion) {
            if (!openState && overlay === host) dismissImmediately()
        }
    }

    fun dismissImmediately() {
        openState = false
        sheetSpring?.cancel()
        sheetSpring = null
        overlay?.let { root.removeView(it) }
        overlay = null
        onDepthProgress?.invoke(0f)
    }
}
