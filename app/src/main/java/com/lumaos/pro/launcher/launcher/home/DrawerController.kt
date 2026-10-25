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
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.theme.LumaComponents
import com.lumaos.pro.launcher.ui.theme.ColorMath
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

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    var isOpen = false
        private set
    var progress = 0f
        private set
    private var buckets: List<Pair<Char, Int>> = emptyList()
    private var currentRows: List<DrawerRow> = emptyList()
    private var retryAvailable = false
    var currentSort: DrawerSort = DrawerSort.ALPHABETICAL

    private val spring = SpringAnimator(0f, valueThreshold = 0.0015f) { p -> applyProgress(p) }

    init {
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
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
        ensureVisible()
        val wasOpen = isOpen
        isOpen = true
        if (!wasOpen) host.onDrawerOpened()
        // Wait one layout pass when the sheet was GONE, so travel is real.
        sheet.post {
            if (!isOpen) return@post
            spring.animateTo(1f, MotionTokens.SNAPPY, host.motion())
            if (focusSearch) {
                searchField.requestFocus()
                showKeyboard()
            }
        }
    }

    fun close() {
        if (!isOpen && container.visibility != View.VISIBLE) return
        isOpen = false
        hideKeyboard()
        spring.animateTo(0f, MotionTokens.SNAPPY, host.motion()) { finishClose() }
    }

    /** Immediately closed (returning home with the Home button). */
    fun closeInstantly() {
        isOpen = false
        hideKeyboard()
        spring.snapTo(0f)
        finishClose()
    }

    private fun finishClose() {
        if (container.visibility == View.GONE) return
        container.visibility = View.GONE
        if (searchField.text.isNotEmpty()) searchField.setText("")
        host.onFilterSelected(DrawerFilter.All)
        grid.scrollToPosition(0)
        host.onDrawerClosed()
    }

    // ------------------------------------------------------------ dragging

    fun startTracking() {
        ensureVisible()
        spring.cancel()
    }

    /** [delta] in pixels: negative moves the sheet up (opening). */
    fun trackBy(deltaPx: Float) {
        val p = (progress - deltaPx / sheetTravel()).coerceIn(-0.02f, 1.02f)
        spring.track(p)
    }

    /** Settles after a drag; [velocityY] in px/s, negative = upward fling. */
    fun release(velocityY: Float) {
        val v = -velocityY / sheetTravel()
        val target = when {
            v > 1.2f -> 1f
            v < -1.2f -> 0f
            progress > 0.42f -> 1f
            else -> 0f
        }
        spring.setVelocity(v)
        if (target == 1f) {
            val wasOpen = isOpen
            isOpen = true
            if (!wasOpen) host.onDrawerOpened()
            spring.animateTo(1f, MotionTokens.SNAPPY, host.motion())
        } else {
            isOpen = false
            hideKeyboard()
            spring.animateTo(0f, MotionTokens.SNAPPY, host.motion()) { finishClose() }
        }
    }

    /** True when a downward drag on the drawer should close it rather than scroll. */
    fun canDragClose(): Boolean = isOpen && !grid.canScrollVertically(-1)

    // ------------------------------------------------------------- content

    fun submit(
        output: DrawerModel.Output,
        adapter: DrawerAdapter,
        loading: Boolean,
        query: String,
        refreshFailed: Boolean = false
    ) {
        currentRows = output.rows
        buckets = output.buckets
        adapter.submitList(output.rows)
        alphaIndex.letters = output.buckets.map { it.first }
        alphaIndex.visibility = if (output.buckets.size > 3 && query.isBlank()) View.VISIBLE else View.GONE
        val empty = output.rows.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        updateRetryAvailability(empty && !loading && refreshFailed)
        emptyView.text = when {
            loading -> context.getString(R.string.drawer_loading_apps)
            refreshFailed -> context.getString(R.string.drawer_retry_apps)
            query.isNotBlank() -> context.getString(R.string.empty_no_results, query)
            else -> context.getString(R.string.empty_no_apps)
        }
        subtitle.text = if (query.isBlank()) {
            context.resources.getQuantityString(R.plurals.drawer_app_count, output.appCount, output.appCount)
        } else {
            context.resources.getQuantityString(R.plurals.drawer_result_count, output.appCount, output.appCount)
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
                setTextColor(if (chosen) c.onAccentContainer else c.onSurfaceMuted)
                background = ThemedDrawables.ripple(
                    c.ripple,
                    ThemedDrawables.surface(
                        if (chosen) c.accentContainer else c.surface2,
                        theme.corner(LumaComponents.of(theme).shapes.chipDp) * density,
                        if (chosen) 0 else c.outline, if (chosen) 0 else dp(1f)
                    ),
                    theme.corner(LumaComponents.of(theme).shapes.chipDp) * density
                )
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
        val theme = host.theme()
        val c = theme.colors
        val radius = theme.corner(LumaComponents.of(theme).shapes.chipDp) * density
        emptyView.background = ThemedDrawables.ripple(
            c.ripple,
            ThemedDrawables.surface(c.surface2, radius, c.outline, 1),
            radius
        )
    }

    fun applyTheme(theme: ResolvedTheme, translucency: Boolean) {
        val c = theme.colors
        val shapes = LumaComponents.of(theme).shapes
        val radius = theme.corner(shapes.sheetDp) * density
        sheet.background = ThemedDrawables.topRounded(
            ColorMath.withAlpha(c.surface0, theme.surfaceAlpha(translucency)), radius
        )
        scrim.setBackgroundColor(c.scrim)
        handle.background = ThemedDrawables.surface(c.outlineStrong, dp(2f).toFloat())
        searchField.background = ThemedDrawables.surface(
            c.surface1, theme.corner(shapes.searchDp) * density, c.outline, dp(1f)
        )
        searchField.setTextColor(c.onSurface)
        searchField.setHintTextColor(c.onSurfaceMuted)
        clearButton.imageTintList = android.content.res.ColorStateList.valueOf(c.onSurfaceMuted)
        searchField.typeface = Typefaces.label(theme.typography)
        ContextCompat.getDrawable(context, R.drawable.ic_rx_search)?.mutate()?.let { d ->
            d.setTint(c.onSurfaceMuted)
            d.setBounds(0, 0, dp(20f), dp(20f))
            searchField.setCompoundDrawablesRelative(d, null, null, null)
        }
        title.setTextColor(c.onSurface)
        title.typeface = Typefaces.title(theme.typography)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.titleLargeSp)
        subtitle.typeface = Typefaces.body(theme.typography)
        subtitle.setTextColor(c.onSurfaceMuted)
        emptyView.setTextColor(c.onSurfaceMuted)
        emptyView.typeface = Typefaces.body(theme.typography)
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodyMediumSp)
        updateRetryAvailability(retryAvailable, forceStyle = true)
        alphaIndex.setAccentColor(c.accent)
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
