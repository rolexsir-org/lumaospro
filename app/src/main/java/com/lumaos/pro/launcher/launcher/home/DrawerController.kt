package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.launcher.model.DrawerFilter
import com.lumaos.pro.launcher.launcher.model.DrawerModel
import com.lumaos.pro.launcher.launcher.model.DrawerRow
import com.lumaos.pro.launcher.launcher.model.ObsidianProjection
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
    private val emptyView: TextView,
    private val title: TextView,
    private val subtitle: TextView,
    private val handle: View,
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
        fun launchFirst(rows: List<DrawerRow>): Boolean
        fun retryApps()
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
    private var currentRows: List<DrawerRow> = emptyList()
    private var observedQuery = searchField.text?.toString().orEmpty()
    private var retryAvailable = false
    private var renderGeneration = 0

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
                    renderGeneration++
                    grid.visibility = View.INVISIBLE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = context.getString(R.string.drawer_searching_apps)
                    updateRetryAvailability(false)
                }
                clearButton.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
                host.onQueryChanged(q)
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
            val spec = MotionTokens.DRAWER
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
        val spec = MotionTokens.DRAWER
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
        val spec = MotionTokens.DRAWER
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

    fun submit(
        output: DrawerModel.Output,
        adapter: DrawerAdapter,
        loading: Boolean,
        query: String,
        refreshFailed: Boolean = false
    ) {
        // Ignore obsolete emissions while the background model catches up.
        if (output.query != query.trim() || searchField.text.toString().trim() != output.query ||
            output.filter != DrawerFilter.All) return
        val projected = ObsidianProjection.project(output)
        val generation = ++renderGeneration
        adapter.submitList(projected.rows) {
            if (generation != renderGeneration || searchField.text.toString().trim() != output.query) return@submitList
            currentRows = projected.rows
            grid.visibility = View.VISIBLE
            val empty = projected.rows.isEmpty()
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
            context.resources.getQuantityString(R.plurals.drawer_app_count, projected.appCount, projected.appCount)
        } else {
            context.resources.getQuantityString(R.plurals.drawer_result_count, projected.rows.size, projected.rows.size)
        }
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

        // The sheet and search field always use the theme's glass material, so
        // every theme - light, dark or custom - reads as intentionally designed
        // rather than a fixed dark surface. The same material grammar the home
        // screen uses (GlassRole.SHEET / SEARCH) drives blur, elevation and edge.
        GlassViews.apply(sheet, sheetSpec, density, ripple = false, clipToOutline = false, depth = LumaTokens.depthDrawer)
        GlassViews.apply(searchField, searchSpec, density, ripple = false, clipToOutline = true, depth = LumaTokens.depthDrawer)
        // Keep home screen visible and progressively blurred behind the glass drawer sheet.
        // The theme's own scrim tone (black on dark themes, warm ink on Ivory)
        // keeps text legible over the backdrop for every theme.
        scrim.setBackgroundColor(ColorMath.withAlpha(ColorMath.opaque(c.scrim), if (theme.isDark) 0.28f else 0.16f))
        handle.background = GradientDrawable().apply {
            setColor(sheetColors.edgeTop)
            cornerRadius = dp(2f).toFloat()
        }
        val content = sheetColors.content
        val muted = sheetColors.muted
        val searchContent = searchColors.content
        val searchMuted = searchColors.muted
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
        // The eyebrow carries the active theme's accent so it stays in step with
        // every other themed surface instead of a fixed signature colour.
        eyebrow.setTextColor(c.accent)
        eyebrow.typeface = Typefaces.label(theme.typography)
        title.setTextColor(content)
        title.typeface = Typefaces.title(theme.typography)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, maxOf(26f, theme.typeScale.screenTitle))
        // The drawer header names the active theme rather than a fixed word, so
        // it stays truthful for every LumaOS theme (Obsidian, Ivory, Signature,
        // Atmosphere, Monolith, Luma Glass, Luma Mono) and updates on switch.
        title.text = theme.displayName
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
