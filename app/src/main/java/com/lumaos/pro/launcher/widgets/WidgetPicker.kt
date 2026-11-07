package com.lumaos.pro.launcher.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.text.TextUtils
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
import com.lumaos.pro.launcher.launcher.home.LumaDepthStack
import com.lumaos.pro.launcher.launcher.home.Typefaces
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.LumaSpring
import com.lumaos.pro.launcher.motion.MotionPolicy
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.LumaTokens
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
    private val onDepthProgress: ((Float) -> Unit)? = null,
    /** Fresh provider capabilities, evaluated per row against the live grid. */
    private val capabilities: (AppWidgetProviderInfo) -> WidgetCapabilities?
) {
    private val density = context.resources.displayMetrics.density
    private val glassPalette = GlassViews.palette(theme, effects)
    private fun dp(v: Float) = (v * density).toInt()
    private var overlay: FrameLayout? = null
    private var sheetSpring: SpringAnimator? = null
    private var openState: Boolean = false
    private var selectionInFlight = false
    private var listContainer: LinearLayout? = null
    private var emptyView: TextView? = null
    private var retryButton: TextView? = null
    private var allProviders: List<WidgetPickerEntry<AppWidgetProviderInfo>> = emptyList()
    @Volatile private var renderGeneration = 0
    private var onPick: ((AppWidgetProviderInfo) -> Unit)? = null
    private var onRetry: (() -> Unit)? = null

    private val loader = Executors.newSingleThreadExecutor { r ->
        Thread(r, "luma-widget-preview").apply { isDaemon = true }
    }

    val isShowing: Boolean get() = openState

    fun show(
        state: ProviderLoad,
        onPick: (AppWidgetProviderInfo) -> Unit,
        onRetry: () -> Unit
    ) {
        dismissImmediately()
        this.onPick = onPick
        this.onRetry = onRetry
        selectionInFlight = false
        openState = true
        val providers = (state as? ProviderLoad.Loaded)?.providers.orEmpty()
        val pm = context.packageManager
        val appLabels = HashMap<String, String>()
        allProviders = providers.map { info ->
            val pkg = info.provider.packageName
            val appLabel = appLabels.getOrPut(pkg) {
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            }
            val widgetLabel = runCatching { info.loadLabel(pm) }.getOrNull() ?: info.provider.className
            WidgetPickerEntry(info, pkg, appLabel, widgetLabel)
        }
        val c = theme.colors
        val sheetSpec = glassPalette[GlassRole.SHEET]
        val sheetColors = sheetSpec.rest
        val ov = overlay ?: FrameLayout(context).apply {
            LumaDepthStack.bindLayerElevation(this, LumaTokens.depthModal, density)
            root.addView(this, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            this@WidgetPicker.overlay = this
        }
        ov.removeAllViews()
        ov.visibility = View.VISIBLE
        val scrim = View(context).apply {
            setBackgroundColor(c.scrim)
            setOnClickListener { dismiss() }
            contentDescription = context.getString(R.string.common_cancel)
        }
        ov.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(LumaTokens.screenMarginHorizontal),
                dp(LumaTokens.spaceMD),
                dp(LumaTokens.screenMarginHorizontal),
                bottomInset + dp(LumaTokens.dockBottomMargin)
            )
            isClickable = true
        }
        GlassViews.apply(sheet, sheetSpec, density, ripple = false, clipToOutline = false, depth = LumaTokens.depthModal)
        // Grabber: says "this is a sheet" the way the drawer does.
        sheet.addView(View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ColorMath.withAlpha(ColorMath.opaque(c.onSurface), 0.22f))
                cornerRadius = dp(2f).toFloat()
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(36f), dp(4f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(LumaTokens.spaceMD)
        })
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
            setPadding(0, dp(4f), 0, dp(16f))
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
            setPadding(dp(24f), dp(48f), dp(24f), dp(16f))
            compoundDrawablePadding = dp(16f)
            androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_rx_grid)?.mutate()?.let { d ->
                d.setTint(ColorMath.withAlpha(ColorMath.opaque(c.onSurface), 0.35f))
                d.setBounds(0, 0, dp(40f), dp(40f))
                setCompoundDrawablesRelative(null, d, null, null)
            }
            visibility = View.GONE
        }
        sheet.addView(emptyView)
        retryButton = TextView(context).apply {
            text = context.getString(R.string.widgets_retry)
            setTextColor(GlassViews.chipText(theme, selected = true))
            typeface = Typefaces.title(theme.typography)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.labelLargeSp)
            gravity = Gravity.CENTER
            setPadding(dp(24f), 0, dp(24f), 0)
            minHeight = dp(LumaTokens.minTouchTarget)
            isClickable = true
            isFocusable = true
            background = GlassViews.chip(theme, selected = true, density = density, visualHeightDp = 40f)
            visibility = View.GONE
            setOnClickListener { onRetry() }
        }
        sheet.addView(retryButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        when (state) {
            is ProviderLoad.Failed -> showErrorState()
            is ProviderLoad.Loaded -> {
                if (providers.isEmpty()) showEmptyState() else renderRows("")
            }
        }

        val availableHeight = root.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        val sheetHeight = (availableHeight * 0.78f).toInt()
            .coerceAtLeast(minOf(dp(360f), availableHeight))
            .coerceAtMost(availableHeight)
        val sheetParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sheetHeight, Gravity.BOTTOM)
        ov.addView(sheet, sheetParams)

        val travel = sheetParams.height.toFloat()
        LumaHaptics.snap(sheet)
        val s = SpringAnimator(0f) { p ->
            val clamped = p.coerceIn(0f, 1f)
            sheet.translationY = (1f - p) * travel
            scrim.alpha = clamped
            onDepthProgress?.invoke(clamped)
        }
        sheetSpring = s
        s.animateTo(1f, MotionTokens.LUMA, motion)
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
            compoundDrawablePadding = dp(10f)
            androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_rx_search)?.mutate()?.let { d ->
                d.setTint(colors.muted)
                d.setBounds(0, 0, dp(LumaTokens.iconInline), dp(LumaTokens.iconInline))
                setCompoundDrawablesRelative(d, null, null, null)
            }
            isSingleLine = true
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
                .apply { bottomMargin = dp(LumaTokens.spaceMD) }
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
        val generation = ++renderGeneration
        val groups = WidgetPickerModel.groups(allProviders, query)
        container.removeAllViews()
        if (groups.isEmpty()) {
            showNoResultsState(query)
            return
        }
        emptyView?.visibility = View.GONE
        retryButton?.visibility = View.GONE
        container.visibility = View.VISIBLE

        // Grouped list: one quiet card per app, rows separated by inset hairlines,
        // instead of a stack of individually framed controls.
        for (group in groups) {
            container.addView(buildAppHeader(group.packageName, group.appLabel, generation))
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = groupCardBackground()
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            }
            group.entries.forEachIndexed { index, entry ->
                if (index > 0) card.addView(View(context).apply {
                    setBackgroundColor(ColorMath.opaque(theme.colors.outline))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                    marginStart = dp(16f + 112f + 14f)
                })
                card.addView(row(entry.provider, entry.widgetLabel, generation) {
                    if (!selectionInFlight) {
                        selectionInFlight = true
                        dismiss()
                        onPick?.invoke(entry.provider)
                    }
                })
            }
            container.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun buildAppHeader(pkg: String, appLabel: String, generation: Int): View {
        val spec = glassPalette[GlassRole.SHEET]
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(20f), 0, dp(8f))
        }
        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f)).apply { marginEnd = dp(10f) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        loader.execute {
            if (generation != renderGeneration) return@execute
            val core = (context.applicationContext as? com.lumaos.pro.launcher.core.LumaApp)?.core
            val livingDrawable = core?.livingIcons?.drawableForPackage(pkg, dp(24f))
            val d = livingDrawable ?: runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
            icon.post { if (generation == renderGeneration) icon.setImageDrawable(d) }
        }
        row.addView(icon)
        row.addView(TextView(context).apply {
            text = appLabel
            setTextColor(spec.rest.content)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.titleSmallSp)
            letterSpacing = theme.typography.titleTracking
            typeface = Typefaces.title(theme.typography)
        })
        return row
    }

    private fun row(info: AppWidgetProviderInfo, label: String, generation: Int, onClick: () -> Unit): View {
        val rowSpec = glassPalette[GlassRole.SETTINGS_CONTROL]
        val rowColors = rowSpec.rest
        val cellsW = cellsForWidth(info)
        val cellsH = cellsForHeight(info)
        val caps = capabilities(info)
        val preview = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(112f), dp(76f)).apply { marginEnd = dp(14f) }
            scaleType = ImageView.ScaleType.FIT_CENTER
            // Preview plate: the theme's raised surface at the widget radius.
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ColorMath.opaque(theme.colors.surface2))
                cornerRadius = theme.radius(com.lumaos.pro.launcher.ui.theme.RadiusRole.MEDIUM) * density
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val densityDpi = context.resources.displayMetrics.densityDpi
        loader.execute {
            if (generation != renderGeneration) return@execute
            val d: Drawable? = runCatching { info.loadPreviewImage(context, densityDpi) }.getOrNull()
                ?: runCatching { info.loadIcon(context, densityDpi) }.getOrNull()
            preview.post { if (generation == renderGeneration) preview.setImageDrawable(d) }
        }
        val capability = when {
            caps == null -> null
            caps.resizableVertical -> context.getString(R.string.widget_capability_resizable)
            else -> context.getString(R.string.widget_capability_fixed)
        }
        val meta = buildList {
            if (capability != null) add(capability)
            if (caps?.configurable == true) add(context.getString(R.string.widget_capability_configurable))
        }.joinToString(" · ")
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(92f)
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
            isClickable = true
            isFocusable = true
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(theme.colors.ripple), null,
                android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
            )
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
                    ellipsize = TextUtils.TruncateAt.END
                })
                // Size as a small pill: the one fact people scan for.
                addView(TextView(context).apply {
                    text = context.getString(R.string.widgets_size, cellsW, cellsH)
                    setTextColor(rowColors.content)
                    typeface = Typefaces.label(theme.typography)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.labelSmallSp)
                    setPadding(dp(8f), dp(2f), dp(8f), dp(2f))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(ColorMath.withAlpha(ColorMath.opaque(theme.colors.onSurface), if (theme.isDark) 0.10f else 0.06f))
                        cornerRadius = dp(10f).toFloat()
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4f); bottomMargin = dp(2f)
                })
                if (meta.isNotEmpty()) addView(TextView(context).apply {
                    text = meta
                    setTextColor(rowColors.muted)
                    typeface = Typefaces.body(theme.typography)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodySmallSp)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Quiet grouped-list card: a whisper of content colour plus the theme hairline. */
    private fun groupCardBackground(): Drawable = android.graphics.drawable.GradientDrawable().apply {
        val c = theme.colors
        setColor(ColorMath.withAlpha(ColorMath.opaque(c.onSurface), if (theme.isDark) 0.05f else 0.035f))
        setStroke(1, ColorMath.withAlpha(ColorMath.opaque(c.outline), 0.8f))
        cornerRadius = theme.radius(com.lumaos.pro.launcher.ui.theme.RadiusRole.LARGE) * density
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
        if (!openState) return
        openState = false
        renderGeneration++
        val s = sheetSpring
        if (s == null || !motion.enabled) {
            dismissImmediately()
            return
        }
        s.animateTo(0f, MotionTokens.LUMA, motion) {
            if (!openState) dismissImmediately()
        }
    }

    private fun dismissImmediately() {
        openState = false
        renderGeneration++
        sheetSpring?.cancel()
        sheetSpring = null
        overlay?.let { ov ->
            ov.removeAllViews()
            ov.visibility = View.GONE
        }
        onDepthProgress?.invoke(0f)
        listContainer = null
        emptyView = null
        retryButton = null
    }

    /** Releases the preview executor. Called once when the home activity is destroyed. */
    fun shutdown() {
        dismissImmediately()
        overlay?.let { root.removeView(it) }
        overlay = null
        loader.shutdownNow()
    }
}
