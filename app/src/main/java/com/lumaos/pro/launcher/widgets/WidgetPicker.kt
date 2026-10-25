package com.lumaos.pro.launcher.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
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
import com.lumaos.pro.launcher.effects.EffectsDecision
import com.lumaos.pro.launcher.launcher.home.ThemedDrawables
import com.lumaos.pro.launcher.launcher.home.Typefaces
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import java.util.concurrent.Executors

/**
 * Bottom-sheet picker listing the widgets installed apps provide, grouped by
 * app, with the provider's own preview image. Built in code so it follows the
 * active theme. Previews decode on a background thread.
 */
class WidgetPicker(
    private val context: Context,
    private val root: FrameLayout,
    private val theme: ResolvedTheme,
    private val effects: EffectsDecision,
    private val motion: MotionPolicy,
    private val bottomInset: Int
) {
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()
    private var overlay: FrameLayout? = null
    private val loader = Executors.newSingleThreadExecutor()

    val isShowing: Boolean get() = overlay != null

    fun show(providers: List<AppWidgetProviderInfo>, onPick: (AppWidgetProviderInfo) -> Unit) {
        dismiss()
        val c = theme.colors
        val pm = context.packageManager
        val ov = FrameLayout(context)
        val scrim = View(context).apply {
            setBackgroundColor(c.scrim)
            setOnClickListener { dismiss() }
            contentDescription = context.getString(R.string.common_cancel)
        }
        ov.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ThemedDrawables.topRounded(
                ColorMath.withAlpha(c.surface0, theme.surfaceAlpha(effects.translucency)), theme.corner(24f) * density
            )
            setPadding(dp(20f), dp(14f), dp(20f), bottomInset + dp(12f))
            isClickable = true
        }
        sheet.addView(TextView(context).apply {
            text = context.getString(R.string.widgets_title)
            setTextColor(c.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typefaces.title(theme.typography)
        })
        sheet.addView(TextView(context).apply {
            text = context.getString(R.string.widgets_subtitle)
            setTextColor(c.onSurfaceMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(2f), 0, dp(10f))
        })

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (providers.isEmpty()) {
            list.addView(TextView(context).apply {
                text = context.getString(R.string.widgets_empty)
                setTextColor(c.onSurfaceMuted)
                setPadding(0, dp(24f), 0, dp(24f))
                gravity = Gravity.CENTER
            })
        }
        val grouped = providers.groupBy { it.provider.packageName }.toSortedMap(compareBy {
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString().lowercase() }.getOrDefault(it)
        })
        for ((pkg, infos) in grouped) {
            val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            list.addView(TextView(context).apply {
                text = if (theme.typography.readoutCaps) appLabel.uppercase() else appLabel
                setTextColor(c.onSurfaceMuted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                letterSpacing = theme.typography.readoutTracking
                typeface = Typefaces.readout(theme.typography)
                setPadding(0, dp(14f), 0, dp(6f))
            })
            for (info in infos) list.addView(row(info) {
                dismiss()
                onPick(info)
            })
        }
        val scroll = ScrollView(context).apply { addView(list) }
        sheet.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val sheetParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (root.height * 0.72f).toInt().coerceAtLeast(dp(320f)), Gravity.BOTTOM)
        ov.addView(sheet, sheetParams)
        root.addView(ov, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay = ov

        val travel = sheetParams.height.toFloat()
        SpringAnimator(0f) { p ->
            sheet.translationY = (1f - p) * travel
            scrim.alpha = p.coerceIn(0f, 1f)
        }.animateTo(1f, MotionTokens.SNAPPY, motion)
    }

    private fun row(info: AppWidgetProviderInfo, onClick: () -> Unit): View {
        val c = theme.colors
        val label = runCatching { info.loadLabel(context.packageManager) }.getOrNull() ?: info.provider.className
        val cellsW = ((info.minWidth / density + 30) / 70).toInt().coerceAtLeast(1)
        val cellsH = ((info.minHeight / density + 30) / 70).toInt().coerceAtLeast(1)
        val preview = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(96f), dp(64f)).apply { marginEnd = dp(14f) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = ThemedDrawables.surface(c.surface2, theme.corner(12f) * density)
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val densityDpi = context.resources.displayMetrics.densityDpi
        loader.execute {
            val d: Drawable? = runCatching { info.loadPreviewImage(context, densityDpi) }.getOrNull()
                ?: runCatching { info.loadIcon(context, densityDpi) }.getOrNull()
            preview.post { preview.setImageDrawable(d) }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72f)
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
            isClickable = true
            isFocusable = true
            background = ThemedDrawables.ripple(c.ripple, null, theme.corner(14f) * density)
            contentDescription = context.getString(R.string.widgets_row_description, label, cellsW, cellsH)
            setOnClickListener { onClick() }
            addView(preview)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = label
                    setTextColor(c.onSurface)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    maxLines = 2
                })
                addView(TextView(context).apply {
                    text = context.getString(R.string.widgets_size, cellsW, cellsH)
                    setTextColor(c.onSurfaceMuted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                })
            })
        }
    }

    fun dismiss() {
        overlay?.let { root.removeView(it) }
        overlay = null
    }
}
