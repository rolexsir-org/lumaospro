package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.apps.DrawerCategory
import com.lumaos.pro.launcher.core.apps.DrawerSort
import com.lumaos.pro.launcher.launcher.model.DrawerFilter
import com.lumaos.pro.launcher.launcher.model.DrawerModel
import com.lumaos.pro.launcher.launcher.model.DrawerRow
import com.lumaos.pro.launcher.launcher.model.ObsidianPage
import com.lumaos.pro.launcher.launcher.model.ObsidianProjection
import com.lumaos.pro.launcher.launcher.view.AlphaIndexView
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.LumaSpring
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.ThemedUiIcons
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassPalette
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import com.lumaos.pro.launcher.ui.theme.LumaTypeScale
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import kotlin.math.abs

/**
 * The app drawer: a sheet that follows the finger and settles on a spring.
 *
 * Open progress (0 closed .. 1 open) is a single value; the sheet position,
 * scrim, and the desktop's recession behind it are all derived from it, so
 * every surface moves in lockstep and a gesture can reverse at any moment.
 */
class DrawerController(
    private val context: Context,
    private val container: FrameLayout,
    private val sheet: LinearLayout,
    private val scrim: View,
    val grid: RecyclerView,
    private val searchField: EditText,
    private val clearButton: ImageButton,
    private val alphaIndex: AlphaIndexView,
    private val emptyView: TextView,
    private val chipsScroll: HorizontalScrollView,
    private val chips: LinearLayout,
    private val title: TextView,
    private val subtitle: TextView,
    private val handle: View,
    private val sortButton: ImageButton,
    private val host: Host
) {
    interface Host {
        fun theme(): ResolvedTheme
        fun motion(): com.lumaos.pro.launcher.motion.MotionPolicy
        fun onDrawerProgress(progress: Float)
        fun onDrawerOpened()
        fun onDrawerClosed()
        fun onQueryChanged(query: String)
        fun onFilterSelected(filter: DrawerFilter)
        fun onSortSelected(sort: DrawerSort)
        fun launchFirst(rows: List<DrawerRow>): Boolean
        fun retryApps()
        fun showSortMenu(anchor: View, current: DrawerSort)
    }

    /** Current shared glass recipes, refreshed on every theme/effects change. */
    private var glassPalette: GlassPalette =
        GlassMaterials.palette(host.theme(), GlassCapabilities.SOLID)

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    var isOpen = false
        private set
    val isAnimating: Boolean get() = spring.isRunning
    var progress = 0f
        private set
    private var staggerCancel: Runnable? = null
    private var buckets: List<Pair<Char, Int>> = emptyList()
    private var currentRows: List<DrawerRow> = emptyList()
    private var observedQuery = searchField.text?.toString().orEmpty()
    private var retryAvailable = false
    private val pageBar: View = sheet.findViewById(R.id.drawer_pages)
    private val overviewButton: TextView = sheet.findViewById(R.id.drawer_overview)
    private val libraryButton: TextView = sheet.findViewById(R.id.drawer_library)
    var page: ObsidianPage = ObsidianPage.OVERVIEW
        private set
    private var waitingForAll = false
    private var activeFilter: DrawerFilter = DrawerFilter.All
    // Suppress out-of-order background model emissions after a user changes filters.
    private var expectedFilter: DrawerFilter? = null
    private var renderGeneration = 0
    private data class Submission(
        val output: DrawerModel.Output, val adapter: DrawerAdapter,
        val loading: Boolean, val query: String, val refreshFailed: Boolean
    )
    private var lastSubmission: Submission? = null
    var currentSort: DrawerSort = DrawerSort.ALPHABETICAL

    private val spring = SpringAnimator(0f, valueThreshold = 0.0015f) { p -> applyProgress(p) }

    init {
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                if (q != observedQuery) {
                    observedQuery = q
                    // Never launch or tap rows from the previous query while the
                    // background filter and ListAdapter diff are catching up.
                    currentRows = emptyList()
                    buckets = emptyList()
                    setIndexVisible(false)
                    grid.visibility = View.INVISIBLE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = context.getString(R.string.drawer_searching_apps)
                    updateRetryAvailability(false)
                }
                clearButton.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                pageBar.visibility = if (q.isBlank()) View.VISIBLE else View.GONE
                host.onQueryChanged(q)
                chipsScroll.visibility = if (q.isBlank() && chips.childCount > 1) View.VISIBLE else View.GONE
            }
        })
        clearButton.setOnClickListener {
            searchField.setText("")
            searchField.requestFocus()
            showKeyboard()
        }
        searchField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_NULL
            ) {
                host.launchFirst(currentRows)
                true
            } else false
        }
        alphaIndex.onLetterTouched = { letter ->
            buckets.firstOrNull { it.first == letter }?.second?.let { pos ->
                (grid.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pos, 0)
            }
        }
        grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                // Scrolling the list means browsing, not typing.
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })
        scrim.setOnClickListener { close() }
        emptyView.setOnClickListener { if (retryAvailable) host.retryApps() }
        emptyView.isClickable = false
        emptyView.isFocusable = false
        sortButton.setOnClickListener { host.showSortMenu(it, currentSort) }
        listOf(overviewButton, libraryButton).forEach { button ->
            androidx.core.view.ViewCompat.setAccessibilityDelegate(button, object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = android.widget.Button::class.java.name
                }
            })
        }
        overviewButton.setOnClickListener { selectPage(ObsidianPage.OVERVIEW) }
        libraryButton.setOnClickListener { selectPage(ObsidianPage.LIBRARY) }
        stylePages()
    }

    private fun sheetTravel(): Float {
        val h = sheet.height.takeIf { it > 0 } ?: container.rootView.height
        return h.toFloat().coerceAtLeast(1f)
    }

    private fun applyProgress(p: Float) {
        progress = p
        val clamped = p.coerceIn(0f, 1f)
        sheet.translationY = (1f - p) * sheetTravel()
        // A single, reversible transform follows the drag and spring. No per-row
        // effects or extra animator are needed; reduced motion omits the scale.
        sheet.pivotX = sheet.width / 2f
        sheet.pivotY = sheet.height.toFloat()
        val scale = if (host.motion().reduced) 1f else 0.985f + 0.015f * clamped
        sheet.scaleX = scale
        sheet.scaleY = scale
        scrim.alpha = clamped
        host.onDrawerProgress(clamped)
        if (p <= 0.0005f && !isOpen && !spring.isRunning) finishClose()
    }

    private fun ensureVisible() {
        if (container.visibility != View.VISIBLE) {
            container.visibility = View.VISIBLE
            sheet.translationY = sheetTravel()
        }
    }

    fun open(focusSearch: Boolean) {
        val shouldStagger = !isOpen && progress < 0.08f
        ensureVisible()
        val wasOpen = isOpen
        isOpen = true
        if (!wasOpen) {
            LumaHaptics.snap(sheet)
            host.onDrawerOpened()
        }
        // Wait one layout pass when the sheet was GONE, so travel is real.
        sheet.post {
            if (!isOpen) return@post
            val spec = if (!host.theme().usesBlur) MotionTokens.SNAPPY else MotionTokens.LUMA
            spring.animateTo(1f, spec, host.motion())
            if (shouldStagger) {
                staggerCancel?.run()
                staggerCancel = LumaSpring.staggerRecyclerView(grid, host.motion())
            }
            if (focusSearch) {
                searchField.requestFocus()
                showKeyboard()
            }
        }
    }

    fun close() {
        if (!isOpen && container.visibility != View.VISIBLE) return
        isOpen = false
        staggerCancel?.run()
        staggerCancel = null
        hideKeyboard()
        val spec = if (!host.theme().usesBlur) MotionTokens.SNAPPY else MotionTokens.LUMA
        spring.animateTo(0f, spec, host.motion()) { finishClose() }
    }

    /** Immediately closed (returning home with the Home button). */
    fun closeInstantly() {
        isOpen = false
        staggerCancel?.run()
        staggerCancel = null
        hideKeyboard()
        spring.snapTo(0f)
        finishClose()
    }

    private fun finishClose() {
        if (container.visibility == View.GONE) return
        staggerCancel?.run()
        staggerCancel = null
        container.visibility = View.GONE
        page = ObsidianPage.OVERVIEW
        activeFilter = DrawerFilter.All
        expectedFilter = DrawerFilter.All
        waitingForAll = false
        renderGeneration++
        stylePages()
        if (searchField.text.isNotEmpty()) searchField.setText("")
        host.onFilterSelected(DrawerFilter.All)
        lastSubmission?.takeIf { it.output.filter == DrawerFilter.All && it.output.query.isBlank() }?.let {
            submit(it.output, it.adapter, it.loading, it.query, it.refreshFailed)
        }
        grid.scrollToPosition(0)
        host.onDrawerClosed()
    }

    // ------------------------------------------------------------ dragging

    fun startTracking() {
        ensureVisible()
        staggerCancel?.run()
        staggerCancel = null
        spring.cancel()
    }

    /** [delta] in pixels: negative moves the sheet up (opening). */
    fun trackBy(deltaPx: Float) {
        val p = (progress - deltaPx / sheetTravel()).coerceIn(-0.02f, 1.02f)
        spring.track(p)
    }

    /** Settles after a drag; [velocityY] in px/s, negative = upward fling. */
    fun release(velocityY: Float, focusSearchWhenOpened: Boolean = false) {
        val v = -velocityY / sheetTravel()
        val target = LumaSpring.resolveBinarySnapTarget(progress, v)
        val spec = if (!host.theme().usesBlur) MotionTokens.SNAPPY else MotionTokens.LUMA
        spring.setVelocity(v)
        if (target == 1f) {
            val wasOpen = isOpen
            isOpen = true
            LumaHaptics.snap(sheet)
            if (!wasOpen) host.onDrawerOpened()
            spring.animateTo(1f, spec, host.motion()) {
                if (focusSearchWhenOpened && isOpen) {
                    searchField.requestFocus()
                    showKeyboard()
                }
            }
        } else {
            if (isOpen) LumaHaptics.snap(sheet)
            isOpen = false
            hideKeyboard()
            spring.animateTo(0f, spec, host.motion()) { finishClose() }
        }
    }

    /** True when a downward drag on the drawer should close it rather than scroll. */
    fun canDragClose(): Boolean = (isOpen || spring.isRunning) && !grid.canScrollVertically(-1)

    // ------------------------------------------------------------- content

    /** Restores only this transient presentation, never changes saved user data. */
    fun restorePage(saved: String?) {
        page = ObsidianPage.entries.firstOrNull { it.name == saved } ?: ObsidianPage.OVERVIEW
        stylePages()
    }

    private fun selectPage(target: ObsidianPage) {
        page = target
        expectedFilter = DrawerFilter.All
        hideKeyboard()
        val last = lastSubmission
        waitingForAll = last != null && last.output.filter != DrawerFilter.All
        activeFilter = DrawerFilter.All
        stylePages()
        host.onFilterSelected(DrawerFilter.All)
        grid.scrollToPosition(0)
        if (waitingForAll) {
            // Never display the previous collection under the library tab.
            renderGeneration++
            currentRows = emptyList()
            buckets = emptyList()
            setIndexVisible(false)
            grid.visibility = View.INVISIBLE
            emptyView.visibility = View.VISIBLE
            emptyView.text = context.getString(R.string.drawer_loading_apps)
        } else if (last != null) {
            submit(last.output, last.adapter, last.loading, last.query, last.refreshFailed)
        }
    }

    private fun stylePages() {
        val dark = host.theme().isDark
        val active = if (dark) ObsidianStyle.BLUE_DARK else ObsidianStyle.BLUE_LIGHT
        val muted = if (dark) ObsidianStyle.MUTED_DARK else host.theme().colors.onSurfaceMuted
        listOf(overviewButton to ObsidianPage.OVERVIEW, libraryButton to ObsidianPage.LIBRARY).forEach { (view, item) ->
            val chosen = item == page && !waitingForAll && activeFilter == DrawerFilter.All
            view.isSelected = chosen
            view.typeface = if (chosen) Typefaces.title(host.theme().typography) else Typefaces.label(host.theme().typography)
            view.setTextColor(if (chosen) active else muted)
            view.background = ThemedDrawables.ripple(
                ColorMath.withAlpha(active, 0.16f),
                if (chosen) ThemedDrawables.surface(ColorMath.withAlpha(active, 0.10f), dp(10f).toFloat()) else null,
                dp(10f).toFloat()
            )
            view.contentDescription = if (chosen) context.getString(R.string.drawer_chip_selected, view.text) else view.text
        }
    }

    fun submit(
        output: DrawerModel.Output,
        adapter: DrawerAdapter,
        loading: Boolean,
        query: String,
        refreshFailed: Boolean = false
    ) {
        if (output.query != query.trim() || searchField.text.toString().trim() != output.query) return
        if (expectedFilter != null && output.filter != expectedFilter) return
        if (waitingForAll && output.filter != DrawerFilter.All) return
        if (waitingForAll) { waitingForAll = false; stylePages() }
        lastSubmission = Submission(output, adapter, loading, query, refreshFailed)
        val projected = ObsidianProjection.project(output, page)
        val generation = ++renderGeneration
        adapter.submitList(projected.rows) {
            if (generation != renderGeneration || searchField.text.toString().trim() != output.query) return@submitList
            currentRows = projected.rows
            buckets = projected.buckets
            alphaIndex.letters = projected.buckets.map { it.first }
            setIndexVisible(projected.buckets.size > 3 && query.isBlank())
            grid.visibility = View.VISIBLE
            val empty = projected.rows.isEmpty()
            emptyView.visibility = if (empty) View.VISIBLE else View.GONE
            updateRetryAvailability(empty && !loading && refreshFailed)
            emptyView.text = when {
                loading -> context.getString(R.string.drawer_loading_apps)
                refreshFailed -> context.getString(R.string.drawer_retry_apps)
                query.isNotBlank() -> context.getString(R.string.empty_no_results, query)
                output.filter == DrawerFilter.All && page == ObsidianPage.OVERVIEW && output.appCount > 0 ->
                    context.getString(R.string.obsidian_overview_empty)
                else -> context.getString(R.string.empty_no_apps)
            }
        }
        subtitle.text = if (query.isBlank()) {
            context.resources.getQuantityString(R.plurals.drawer_app_count, output.appCount, output.appCount)
        } else {
            context.resources.getQuantityString(R.plurals.drawer_result_count, output.appCount, output.appCount)
        }
    }

    private fun setIndexVisible(visible: Boolean) {
        alphaIndex.visibility = if (visible) View.VISIBLE else View.GONE
        val end = dp(
            if (visible) LumaTokens.alphaScrubberVisualWidth + LumaTokens.spaceSM
            else LumaTokens.screenMarginHorizontal
        )
        if (grid.paddingEnd != end) {
            grid.setPaddingRelative(grid.paddingStart, grid.paddingTop, end, grid.paddingBottom)
        }
    }

    fun bindChips(categories: List<DrawerCategory>, selected: DrawerFilter, showFrequent: Boolean, enabled: Boolean) {
        chips.removeAllViews()
        val visibleFilter = DrawerModel.availableFilter(selected, categories, enabled, showFrequent)
        if (visibleFilter != selected) {
            expectedFilter = visibleFilter
            activeFilter = visibleFilter
            host.onFilterSelected(visibleFilter)
        } else if (expectedFilter == null || expectedFilter == selected) {
            activeFilter = selected
        }
        stylePages()

        // The setting controls category chips only. Keep All and Frequent
        // available so hiding categories never strands the user in a filtered view.
        val entries = ArrayList<Pair<String, DrawerFilter>>()
        entries += context.getString(R.string.drawer_chip_all) to DrawerFilter.All
        if (showFrequent) entries += context.getString(R.string.drawer_chip_frequent) to DrawerFilter.Frequent
        if (enabled) categories.forEach { entries += it.title to DrawerFilter.Category(it) }
        val theme = host.theme()
        for ((label, filter) in entries) {
            val chosen = filter == visibleFilter
            chips.addView(TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                minHeight = dp(48f)
                minWidth = dp(48f)
                setPadding(dp(14f), 0, dp(14f), 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (!theme.usesBlur) 10.5f else LumaTypeScale.labelLargeSp)
                typeface = Typefaces.label(theme.typography)
                letterSpacing = theme.typography.labelTracking
                // Casing follows the label voice (mono-faced labels are set in
                // caps, as in the Luma Mono reference), never the blur material:
                // changing a blur token must not silently re-case the drawer.
                isAllCaps = theme.typography.readoutCaps &&
                    theme.typography.label.family == com.lumaos.pro.launcher.ui.theme.FontFamilyId.SYSTEM_MONO
                // One chip grammar across the launcher: quiet pill at rest,
                // accent-filled when selected (see GlassViews.chip).
                setTextColor(if (theme.isDark) {
                    if (chosen) ObsidianStyle.BLUE_DARK else ObsidianStyle.MUTED_DARK
                } else GlassViews.chipText(theme, chosen))
                background = if (theme.isDark) {
                    val fill = if (chosen) 0xFF20314A.toInt() else ObsidianStyle.SURFACE
                    android.graphics.drawable.InsetDrawable(
                        ThemedDrawables.ripple(0x3386BAFF, ThemedDrawables.surface(fill, dp(10f).toFloat()), dp(10f).toFloat()),
                        0, dp(7f), 0, dp(7f)
                    )
                } else GlassViews.chip(theme, chosen, density)
                if (chosen) typeface = Typefaces.title(theme.typography)
                isSelected = chosen
                contentDescription = if (chosen) context.getString(R.string.drawer_chip_selected, label) else label
                setOnClickListener {
                    activeFilter = filter
                    expectedFilter = filter
                    waitingForAll = false
                    stylePages()
                    val previous = lastSubmission
                    if (previous?.output?.filter == filter && previous.query == searchField.text.toString().trim()) {
                        submit(previous.output, previous.adapter, previous.loading, previous.query, previous.refreshFailed)
                    } else {
                        renderGeneration++
                        currentRows = emptyList()
                        buckets = emptyList()
                        setIndexVisible(false)
                        grid.visibility = View.INVISIBLE
                        emptyView.visibility = View.VISIBLE
                        emptyView.text = context.getString(R.string.drawer_loading_apps)
                    }
                    host.onFilterSelected(filter)
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48f)).apply {
                    marginEnd = dp(8f)
                }
            })
        }
        chipsScroll.visibility = if (searchField.text.isBlank() && entries.size > 1) View.VISIBLE else View.GONE
    }

    private fun updateRetryAvailability(available: Boolean, forceStyle: Boolean = false) {
        if (!forceStyle && retryAvailable == available) return
        retryAvailable = available
        emptyView.isClickable = available
        emptyView.isFocusable = available
        if (!available) {
            emptyView.background = null
            return
        }
        val spec = glassPalette[GlassRole.OVERLAY]
        emptyView.background = GlassViews.drawable(spec, density, ripple = true)
        emptyView.setTextColor(spec.rest.content)
    }

    fun applyTheme(theme: ResolvedTheme, palette: GlassPalette) {
        glassPalette = palette
        val c = theme.colors
        val sheetSpec = palette[GlassRole.SHEET]
        val searchSpec = palette[GlassRole.SEARCH]
        val sheetColors = sheetSpec.rest
        val searchColors = searchSpec.rest

        if (theme.isDark) {
            sheet.background = ThemedDrawables.topRounded(ObsidianStyle.BACKGROUND, dp(24f).toFloat())
            sheet.elevation = dp(12f).toFloat()
            sheet.clipToOutline = false
            searchField.background = ThemedDrawables.surface(ObsidianStyle.SURFACE, dp(14f).toFloat(), ObsidianStyle.EDGE, dp(1f).coerceAtLeast(1))
            searchField.clipToOutline = false
            searchField.elevation = 0f
        } else {
            GlassViews.apply(sheet, sheetSpec, density, ripple = false, clipToOutline = false, depth = LumaTokens.depthDrawer)
            GlassViews.apply(searchField, searchSpec, density, ripple = false, clipToOutline = true, depth = LumaTokens.depthDrawer)
        }
        // Keep home screen visible and progressively blurred behind the glass drawer sheet.
        // The theme's own scrim tone: black on dark themes, warm ink on Ivory,
        // so a light drawer never sits over a muddy grey veil.
        scrim.setBackgroundColor(ColorMath.withAlpha(ColorMath.opaque(c.scrim), if (theme.isDark) 0.28f else 0.16f))
        handle.background = GradientDrawable().apply {
            setColor(if (theme.isDark) ObsidianStyle.EDGE else sheetColors.edgeTop)
            cornerRadius = dp(2f).toFloat()
        }
        val content = if (theme.isDark) ObsidianStyle.CONTENT else sheetColors.content
        val muted = if (theme.isDark) ObsidianStyle.MUTED_DARK else sheetColors.muted
        val searchContent = if (theme.isDark) ObsidianStyle.CONTENT else searchColors.content
        val searchMuted = if (theme.isDark) ObsidianStyle.MUTED_DARK else searchColors.muted
        searchField.setTextColor(searchContent)
        searchField.setHintTextColor(searchMuted)
        clearButton.imageTintList = android.content.res.ColorStateList.valueOf(searchMuted)
        searchField.typeface = Typefaces.body(theme.typography)
        ThemedUiIcons.drawable(context, R.drawable.ic_rx_search, theme.id)?.let { d ->
            d.setTint(searchMuted)
            d.setBounds(0, 0, dp(LumaTokens.iconInline), dp(LumaTokens.iconInline))
            searchField.setCompoundDrawablesRelative(d, null, null, null)
        }
        val eyebrow = sheet.findViewById<TextView>(R.id.drawer_eyebrow)
        // A small, constant blue signature; all functional surfaces keep their
        // theme-derived contrast and the user's chosen accent.
        eyebrow.setTextColor(if (theme.isDark) ObsidianStyle.BLUE_DARK else ObsidianStyle.BLUE_LIGHT)
        eyebrow.typeface = Typefaces.label(theme.typography)
        title.setTextColor(content)
        title.typeface = Typefaces.title(theme.typography)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, maxOf(26f, theme.typeScale.screenTitle))
        val architectural = theme.id == com.lumaos.pro.launcher.ui.theme.ThemeId.MONOLITH ||
            theme.id == com.lumaos.pro.launcher.ui.theme.ThemeId.LUMA_MONO
        title.isAllCaps = architectural
        title.letterSpacing = if (architectural) 0.06f else -0.02f
        subtitle.typeface = Typefaces.body(theme.typography)
        subtitle.setTextColor(muted)
        emptyView.setTextColor(muted)
        emptyView.typeface = Typefaces.body(theme.typography)
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodyMediumSp)
        updateRetryAvailability(retryAvailable, forceStyle = true)
        alphaIndex.setAccentColor(if (theme.isDark) ObsidianStyle.BLUE_DARK else sheetColors.focusRing)
        stylePages()
    }

    /** Body voice for the search field, kept in step with the theme's typography. */
    fun setSearchTypeface(face: android.graphics.Typeface) {
        searchField.typeface = face
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchField.windowToken, 0)
        searchField.clearFocus()
    }

    private fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT)
    }

    @Suppress("unused")
    private fun isSmall(dy: Float) = abs(dy) < dp(4f)
}
