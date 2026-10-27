package com.lumaos.pro.launcher.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.effects.EffectsDecision
import com.lumaos.pro.launcher.launcher.home.Typefaces
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.LumaTypeScale
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import java.util.concurrent.Executors

/**
 * Bottom-sheet picker listing the widgets installed apps provide, grouped by
 * app, with the provider's own preview image and truthful capability metadata.
 *
 * Built in code so it follows the active Luma Glass theme. Preview and icon
 * decoding happens on a background daemon thread; the executor is shut down
 * with [shutdown] when the home activity is destroyed.
 */
class WidgetPicker(
    private val context: Context,
    private val root: FrameLayout,
    private val theme: ResolvedTheme,
    private val effects: EffectsDecision,
    private val motion: MotionPolicy,
    private val bottomInset: Int,
    /** Fresh provider capabilities, evaluated per row against the live grid. */
    private val capabilities: (AppWidgetProviderInfo) -> WidgetCapabilities?
) {
    private val density = context.resources.displayMetrics.density
    private val glassPalette = GlassViews.palette(theme, effects)
    private fun dp(v: Float) = (v * density).toInt()
    private var overlay: FrameLayout? = null
    private var listContainer: LinearLayout? = null
    private var emptyView: TextView? = null
    private var retryButton: TextView? = null
    private var allProviders: List<AppWidgetProviderInfo> = emptyList()
    private var onPick: ((AppWidgetProviderInfo) -> Unit)? = null
    private var onRetry: (() -> Unit)? = null

    private val loader = Executors.newSingleThreadExecutor { r ->
        Thread(r, "luma-widget-preview").apply { isDaemon = true }
    }

    val isShowing: Boolean get() = overlay != null

    fun show(
        state: ProviderLoad,
        onPick: (AppWidgetProviderInfo) -> Unit,
        onRetry: () -> Unit
    ) {
        dismiss()
        this.onPick = onPick
        this.onRetry = onRetry
        val providers = (state as? ProviderLoad.Loaded)?.providers.orEmpty()
        allProviders = providers
        val c = theme.colors
        val sheetSpec = glassPalette[GlassRole.SHEET]
        val sheetColors = sheetSpec.rest
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
            setPadding(dp(20f), dp(14f), dp(20f), bottomInset + dp(12f))
            isClickable = true
        }
        GlassViews.apply(sheet, sheetSpec, density, ripple = false, clipToOutline = false)
        sheet.addView(TextView(context).apply {
            text = context.getString(R.string.widgets_title)
            setTextColor(sheetColors.content)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.titleLargeSp)
            typeface = Typefaces.title(theme.typography)
            letterSpacing = theme.typography.titleTracking
        })
        sheet.addView(TextView(context).apply {
            text = context.getString(R.string.widgets_subtitle)
            setTextColor(sheetColors.muted)
            typeface = Typefaces.body(theme.typography)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodySmallSp)
            setPadding(0, dp(2f), 0, dp(10f))
        })

        if (state is ProviderLoad.Loaded && providers.isNotEmpty()) {
            sheet.addView(buildSearchField(sheetColors))
        }

        val scroll = ScrollView(context)
        listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listContainer)
        sheet.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        emptyView = TextView(context).apply {
            setTextColor(sheetColors.muted)
            typeface = Typefaces.body(theme.typography)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodyMediumSp)
            gravity = Gravity.CENTER
            setPadding(0, dp(24f), 0, dp(24f))
            visibility = View.GONE
        }
        sheet.addView(emptyView)
        retryButton = TextView(context).apply {
            text = context.getString(R.string.widgets_retry)
            setTextColor(sheetColors.focusRing)
            typeface = Typefaces.label(theme.typography)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.labelLargeSp)
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            isClickable = true
            isFocusable = true
            background = GlassViews.drawable(glassPalette[GlassRole.SETTINGS_CONTROL], density, ripple = true)
            visibility = View.GONE
            setOnClickListener { onRetry() }
        }
        sheet.addView(retryButton)

        when (state) {
            is ProviderLoad.Failed -> showErrorState()
            is ProviderLoad.Loaded -> {
                if (providers.isEmpty()) showEmptyState() else renderRows("")
            }
        }

        val sheetParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (root.height * 0.78f).toInt().coerceAtLeast(dp(360f)), Gravity.BOTTOM)
        ov.addView(sheet, sheetParams)
        root.addView(ov, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay = ov

        val travel = sheetParams.height.toFloat()
        SpringAnimator(0f) { p ->
            sheet.translationY = (1f - p) * travel
            scrim.alpha = p.coerceIn(0f, 1f)
        }.animateTo(1f, MotionTokens.SNAPPY, motion)
    }

    private fun buildSearchField(colors: com.lumaos.pro.launcher.ui.theme.GlassColors): View {
        val field = EditText(context).apply {
            hint = context.getString(R.string.widgets_search_hint)
            setHintTextColor(colors.muted)
            setTextColor(colors.content)
            typeface = Typefaces.body(theme.typography)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodyMediumSp)
            background = GlassViews.drawable(glassPalette[GlassRole.SEARCH], density, ripple = false)
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            singleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = renderRows(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
        return field.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(10f) }
        }
    }

    private fun showEmptyState() {
        listContainer?.visibility = View.GONE
        emptyView?.apply {
            text = context.getString(R.string.widgets_empty)
            visibility = View.VISIBLE
        }
        retryButton?.visibility = View.GONE
    }

    private fun showNoResultsState(query: String) {
        listContainer?.visibility = View.GONE
        emptyView?.apply {
            text = context.getString(R.string.widgets_no_results, query)
            visibility = View.VISIBLE
        }
        retryButton?.visibility = View.GONE
    }

    private fun showErrorState() {
        listContainer?.visibility = View.GONE
        emptyView?.apply {
            text = context.getString(R.string.widgets_providers_failed)
            visibility = View.VISIBLE
        }
        retryButton?.visibility = View.VISIBLE
    }

    private fun renderRows(query: String) {
        val container = listContainer ?: return
        val pm = context.packageManager
        val needle = query.trim().lowercase()
        val filtered = allProviders.filter { info ->
            if (needle.isEmpty()) return@filter true
            val widgetLabel = runCatching { info.loadLabel(pm) }.getOrNull()?.lowercase().orEmpty()
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            }.getOrNull()?.lowercase().orEmpty()
            widgetLabel.contains(needle) || appLabel.contains(needle)
        }
        container.removeAllViews()
        if (filtered.isEmpty()) {
            showNoResultsState(query)
            return
        }
        emptyView?.visibility = View.GONE
        retryButton?.visibility = View.GONE
        container.visibility = View.VISIBLE

        val grouped = filtered.groupBy { it.provider.packageName }.toSortedMap(compareBy { pkg ->
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().lowercase() }.getOrDefault(pkg)
        })
        for ((pkg, infos) in grouped) {
            val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            container.addView(buildAppHeader(pkg, appLabel))
            for (info in infos) container.addView(row(info) {
                dismiss()
                onPick?.invoke(info)
            })
        }
    }

    private fun buildAppHeader(pkg: String, appLabel: String): View {
        val spec = glassPalette[GlassRole.SHEET]
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14f), 0, dp(6f))
        }
        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { marginEnd = dp(8f) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        loader.execute {
            val d = runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
            icon.post { icon.setImageDrawable(d) }
        }
        row.addView(icon)
        row.addView(TextView(context).apply {
            text = if (theme.typography.readoutCaps) appLabel.uppercase() else appLabel
            setTextColor(spec.rest.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.labelMediumSp)
            letterSpacing = theme.typography.readoutTracking
            typeface = Typefaces.readout(theme.typography)
        })
        return row
    }

    private fun row(info: AppWidgetProviderInfo, onClick: () -> Unit): View {
        val rowSpec = glassPalette[GlassRole.SETTINGS_CONTROL]
        val rowColors = rowSpec.rest
        val widgetSpec = glassPalette[GlassRole.WIDGET]
        val label = runCatching { info.loadLabel(context.packageManager) }.getOrNull() ?: info.provider.className
        val cellsW = cellsForWidth(info)
        val cellsH = cellsForHeight(info)
        val caps = capabilities(info)
        val preview = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(96f), dp(64f)).apply { marginEnd = dp(14f) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GlassViews.drawable(widgetSpec, density, ripple = false)
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val densityDpi = context.resources.displayMetrics.densityDpi
        loader.execute {
            val d: Drawable? = runCatching { info.loadPreviewImage(context, densityDpi) }.getOrNull()
                ?: runCatching { info.loadIcon(context, densityDpi) }.getOrNull()
            preview.post { preview.setImageDrawable(d) }
        }
        val capability = when {
            caps == null -> null
            caps.resizableVertical -> context.getString(R.string.widget_capability_resizable)
            else -> context.getString(R.string.widget_capability_fixed)
        }
        val meta = buildList {
            add(context.getString(R.string.widgets_size, cellsW, cellsH))
            if (capability != null) add(capability)
            if (caps?.configurable == true) add(context.getString(R.string.widget_capability_configurable))
        }.joinToString(" · ")
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72f)
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
            isClickable = true
            isFocusable = true
            background = GlassViews.drawable(rowSpec, density, ripple = true)
            contentDescription = context.getString(R.string.widgets_row_description, label, cellsW, cellsH)
            setOnClickListener { onClick() }
            addView(preview)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = label
                    setTextColor(rowColors.content)
                    typeface = Typefaces.body(theme.typography)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.titleSmallSp)
                    maxLines = 2
                })
                addView(TextView(context).apply {
                    text = meta
                    setTextColor(rowColors.muted)
                    typeface = Typefaces.body(theme.typography)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodySmallSp)
                    maxLines = 2
                })
            })
        }
    }

    /** Cell width from the API 31 target when present, else the 70dp cell estimate. */
    private fun cellsForWidth(info: AppWidgetProviderInfo): Int {
        if (android.os.Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) return info.targetCellWidth
        return ((info.minWidth / density + 30) / 70).toInt().coerceAtLeast(1)
    }

    private fun cellsForHeight(info: AppWidgetProviderInfo): Int {
        if (android.os.Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) return info.targetCellHeight
        return ((info.minHeight / density + 30) / 70).toInt().coerceAtLeast(1)
    }

    fun dismiss() {
        overlay?.let { root.removeView(it) }
        overlay = null
        listContainer = null
        emptyView = null
        retryButton = null
    }

    /** Releases the preview executor. Called once when the home activity is destroyed. */
    fun shutdown() {
        loader.shutdownNow()
    }
}
