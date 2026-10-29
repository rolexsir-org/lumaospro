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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.apps.DrawerCategory
import com.lumaos.pro.launcher.core.apps.DrawerSort
import com.lumaos.pro.launcher.launcher.model.DrawerFilter
import com.lumaos.pro.launcher.launcher.model.DrawerModel
import com.lumaos.pro.launcher.launcher.model.DrawerRow
import com.lumaos.pro.launcher.launcher.view.AlphaIndexView
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.LumaSpring
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
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
    }

    private fun sheetTravel(): Float {
        val h = sheet.height.takeIf { it > 0 } ?: container.rootView.height
        return h.toFloat().coerceAtLeast(1f)
    }

    private fun applyProgress(p: Float) {
        progress = p
        val clamped = p.coerceIn(0f, 1f)
        sheet.translationY = (1f - p) * sheetTravel()
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
            spring.animateTo(1f, MotionTokens.LUMA, host.motion())
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
        spring.animateTo(0f, MotionTokens.LUMA, host.motion()) { finishClose() }
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
        if (searchField.text.isNotEmpty()) searchField.setText("")
        host.onFilterSelected(DrawerFilter.All)
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
        spring.setVelocity(v)
        if (target == 1f) {
            val wasOpen = isOpen
            isOpen = true
            LumaHaptics.snap(sheet)
            if (!wasOpen) host.onDrawerOpened()
            spring.animateTo(1f, MotionTokens.LUMA, host.motion()) {
                if (focusSearchWhenOpened && isOpen) {
                    searchField.requestFocus()
                    showKeyboard()
                }
            }
        } else {
            if (isOpen) LumaHaptics.snap(sheet)
            isOpen = false
            hideKeyboard()
            spring.animateTo(0f, MotionTokens.LUMA, host.motion()) { finishClose() }
        }
    }

    /** True when a downward drag on the drawer should close it rather than scroll. */
    fun canDragClose(): Boolean = (isOpen || spring.isRunning) && !grid.canScrollVertically(-1)

    // ------------------------------------------------------------- content

    fun submit(
        output: DrawerModel.Output,
        adapter: DrawerAdapter,
        loading: Boolean,
        query: String,
        refreshFailed: Boolean = false
    ) {
        // combine() can briefly pair a new query with the previous model. Wait
        // for matching results and for the adapter's diff to actually commit.
        if (output.query != query.trim()) return
        adapter.submitList(output.rows) {
            if (searchField.text.toString().trim() != output.query) return@submitList
            currentRows = output.rows
            buckets = output.buckets
            alphaIndex.letters = output.buckets.map { it.first }
            setIndexVisible(output.buckets.size > 3 && query.isBlank())
            grid.visibility = View.VISIBLE
            val empty = output.rows.isEmpty()
            emptyView.visibility = if (empty) View.VISIBLE else View.GONE
            updateRetryAvailability(empty && !loading && refreshFailed)
            emptyView.text = when {
                loading -> context.getString(R.string.drawer_loading_apps)
                refreshFailed -> context.getString(R.string.drawer_retry_apps)
                query.isNotBlank() -> context.getString(R.string.empty_no_results, query)
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
        if (visibleFilter != selected) host.onFilterSelected(visibleFilter)

        // The setting controls category chips only. Keep All and Frequent
        // available so hiding categories never strands the user in a filtered view.
        val entries = ArrayList<Pair<String, DrawerFilter>>()
        entries += context.getString(R.string.drawer_chip_all) to DrawerFilter.All
        if (showFrequent) entries += context.getString(R.string.drawer_chip_frequent) to DrawerFilter.Frequent
        if (enabled) categories.forEach { entries += it.title to DrawerFilter.Category(it) }
        val theme = host.theme()
        val c = theme.colors
        for ((label, filter) in entries) {
            val chosen = filter == visibleFilter
            chips.addView(TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                minHeight = dp(48f)
                minWidth = dp(48f)
                setPadding(dp(14f), 0, dp(14f), 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.labelLargeSp)
                typeface = Typefaces.label(theme.typography)
                letterSpacing = theme.typography.labelTracking
                val chipSpec = glassPalette[GlassRole.SETTINGS_CONTROL]
                val chipColors = if (chosen) chipSpec.states.selected else chipSpec.rest
                setTextColor(chipColors.content)
                background = GlassViews.drawable(chipSpec, density, ripple = true)
                isSelected = chosen
                contentDescription = if (chosen) context.getString(R.string.drawer_chip_selected, label) else label
                setOnClickListener { host.onFilterSelected(filter) }
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

        GlassViews.apply(sheet, sheetSpec, density, ripple = false, clipToOutline = false, depth = LumaTokens.depthDrawer)
        GlassViews.apply(searchField, searchSpec, density, ripple = false, clipToOutline = true, depth = LumaTokens.depthDrawer)
        // Keep home screen visible and progressively blurred behind the glass drawer sheet.
        scrim.setBackgroundColor(ColorMath.withAlpha(0xFF000000.toInt(), 0.28f))
        handle.background = GradientDrawable().apply {
            setColor(sheetColors.edgeTop)
            cornerRadius = dp(2f).toFloat()
        }
        searchField.setTextColor(searchColors.content)
        searchField.setHintTextColor(searchColors.muted)
        clearButton.imageTintList = android.content.res.ColorStateList.valueOf(searchColors.muted)
        searchField.typeface = Typefaces.body(theme.typography)
        ContextCompat.getDrawable(context, R.drawable.ic_rx_search)?.mutate()?.let { d ->
            d.setTint(searchColors.muted)
            d.setBounds(0, 0, dp(LumaTokens.iconInline), dp(LumaTokens.iconInline))
            searchField.setCompoundDrawablesRelative(d, null, null, null)
        }
        title.setTextColor(sheetColors.content)
        title.typeface = Typefaces.title(theme.typography)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeTitleSp)
        subtitle.typeface = Typefaces.body(theme.typography)
        subtitle.setTextColor(sheetColors.muted)
        emptyView.setTextColor(sheetColors.muted)
        emptyView.typeface = Typefaces.body(theme.typography)
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodyMediumSp)
        updateRetryAvailability(retryAvailable, forceStyle = true)
        alphaIndex.setAccentColor(sheetColors.focusRing)
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
