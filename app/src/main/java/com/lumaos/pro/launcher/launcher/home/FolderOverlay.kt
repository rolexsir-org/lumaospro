package com.lumaos.pro.launcher.launcher.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.launcher.AppEntryAdapter
import com.lumaos.pro.launcher.launcher.CellConfig
import com.lumaos.pro.launcher.launcher.IconLoader
import com.lumaos.pro.launcher.motion.LumaHaptics
import com.lumaos.pro.launcher.motion.LumaSpring
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Centered floating Luma Glass folder popup (Apple-precision 3x3 layout):
 *  - Centered floating [GlassViews] popup (~240dp width, >=280dp height, 24dp radius)
 *  - 3x3 grid inside (max 9 apps per page; horizontal snap pages + subtle dots if >9 apps)
 *  - Centered 17sp SemiBold editable folder title at top
 *  - 40% dark dim behind + progressive desktop blur
 */
class FolderOverlay(
    private val context: Context,
    private val root: FrameLayout,
    private val iconLoader: IconLoader,
    private val density: Int,
    private val host: Host
) {
    interface Host {
        fun theme(): ResolvedTheme
        fun glassPalette(): com.lumaos.pro.launcher.ui.theme.GlassPalette
        fun motion(): com.lumaos.pro.launcher.motion.MotionPolicy
        fun translucency(): Boolean
        fun folderCellConfig(): CellConfig
        fun onFolderDepth(progress: Float)
        fun launchFromFolder(entry: AppEntry, view: View)
        fun showFolderMemberMenu(entry: AppEntry, anchor: View, folderId: String?)
        fun renameFolder(folderId: String, name: String)
    }

    private val dpScale = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * dpScale).roundToInt()

    private var overlay: FrameLayout? = null
    private var scrimView: View? = null
    private var panel: LinearLayout? = null
    private var smartTitleView: TextView? = null
    private var smartHintView: TextView? = null
    private var emptyView: TextView? = null
    private var dotsContainer: LinearLayout? = null
    private var pageAdapters: MutableList<AppEntryAdapter> = mutableListOf()
    private var pagesPager: RecyclerView? = null
    private var isSmartFolder: Boolean = false
    private var folderId: String? = null
    private var titleField: EditText? = null
    private var originalName: String = ""
    private var spring: SpringAnimator? = null
    private var staggerCancel: Runnable? = null
    private var activePageIndex: Int = 0
    private var openState: Boolean = false

    val isOpen: Boolean get() = openState

    private fun ensureLayerMounted(): FrameLayout {
        overlay?.let { return it }
        val ov = FrameLayout(context).apply {
            visibility = View.GONE
            LumaDepthStack.bindLayerElevation(this, LumaTokens.depthFolder, dpScale)
        }
        root.addView(ov, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay = ov
        return ov
    }

    fun open(name: String, id: String, members: List<AppEntry>, smart: Boolean, anchor: View?) {
        closeImmediately()
        val theme = host.theme()
        val palette = host.glassPalette()
        val folderSpec = palette[GlassRole.FOLDER].copy(radiusDp = LumaTokens.radiusFolder)
        val colors = folderSpec.rest
        folderId = id
        originalName = name
        isSmartFolder = smart
        activePageIndex = 0
        openState = true

        val ov = ensureLayerMounted()
        ov.removeAllViews()
        ov.visibility = View.VISIBLE
        val scrim = View(context).apply {
            setBackgroundColor(ColorMath.withAlpha(0xFF000000.toInt(), LumaTokens.folderScrimAlpha))
            setOnClickListener { close() }
            contentDescription = context.getString(R.string.folder_close_description)
        }
        scrimView = scrim
        ov.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val p = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            minimumHeight = dp(LumaTokens.folderMinHeight)
            setPadding(
                dp(LumaTokens.spaceSM),
                dp(LumaTokens.spaceMD),
                dp(LumaTokens.spaceSM),
                dp(LumaTokens.spaceMD)
            )
            isClickable = true
        }
        GlassViews.apply(
            view = p,
            spec = folderSpec,
            density = dpScale,
            ripple = false,
            depth = LumaTokens.depthFolder,
            radiusOverrideDp = LumaTokens.radiusFolder
        )

        if (smart) {
            val st = TextView(context).apply {
                text = name
                gravity = Gravity.CENTER
                setTextColor(colors.content)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeHeadingSp)
                typeface = Typefaces.title(theme.typography)
                setPadding(dp(LumaTokens.spaceXS), 0, dp(LumaTokens.spaceXS), 0)
            }
            val sh = TextView(context).apply {
                text = context.getString(R.string.smart_folder_hint)
                gravity = Gravity.CENTER
                setTextColor(colors.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeMicroSp)
                typeface = Typefaces.body(theme.typography)
                setPadding(dp(LumaTokens.spaceXS), dp(2f), dp(LumaTokens.spaceXS), dp(LumaTokens.spaceXS))
            }
            smartTitleView = st
            smartHintView = sh
            p.addView(st, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            p.addView(sh, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else {
            titleField = EditText(context).apply {
                setText(name)
                gravity = Gravity.CENTER
                setTextColor(colors.content)
                setHintTextColor(colors.muted)
                highlightColor = ColorMath.withAlpha(colors.focusRing, 0.28f)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeHeadingSp)
                typeface = Typefaces.title(theme.typography)
                background = null
                setPadding(dp(LumaTokens.spaceXS), dp(2f), dp(LumaTokens.spaceXS), dp(LumaTokens.spaceSM))
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_DONE
                contentDescription = context.getString(R.string.folder_name_description)
                setOnEditorActionListener { v, actionId, event ->
                    if (actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                        commitName()
                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                            ?.hideSoftInputFromWindow(v.windowToken, 0)
                        v.clearFocus()
                        true
                    } else false
                }
            }
            p.addView(titleField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val contentContainer = FrameLayout(context)
        p.addView(
            contentContainer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val dots = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(LumaTokens.spaceXS), 0, 0)
            visibility = View.GONE
        }
        dotsContainer = dots
        p.addView(dots, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        bindGridPages(contentContainer, members, colors)

        // Centered ~240dp popup (scales slightly only if user increased icon size > 56dp or fontScale > 1.3x).
        val fontBoost = context.resources.configuration.fontScale.coerceIn(1f, 1.35f)
        val iconWidthDp = (host.folderCellConfig().iconSizePx / dpScale).coerceAtLeast(LumaTokens.iconApp)
        val naturalWidthDp = max(
            LumaTokens.folderWidth * fontBoost,
            iconWidthDp * LumaTokens.folderColumns + 72f
        )
        val maxAllowedWidth = (root.width - dp(LumaTokens.screenMarginHorizontal * 2f)).coerceAtLeast(dp(LumaTokens.folderWidth))
        val targetWidth = dp(naturalWidthDp).coerceAtMost(maxAllowedWidth)
        val params = FrameLayout.LayoutParams(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        ov.addView(p, params)
        panel = p

        val anchorLoc = IntArray(2)
        val rootLoc = IntArray(2)
        anchor?.getLocationInWindow(anchorLoc)
        root.getLocationInWindow(rootLoc)
        p.post {
            if (anchor != null) {
                p.pivotX = (anchorLoc[0] - rootLoc[0] + anchor.width / 2f - p.left).coerceIn(0f, p.width.toFloat())
                p.pivotY = (anchorLoc[1] - rootLoc[1] + anchor.height / 2f - p.top).coerceIn(0f, p.height.toFloat())
            }
        }
        val s = SpringAnimator(0f) { v ->
            val k = v.coerceIn(0f, 1.2f)
            p.scaleX = 0.85f + 0.15f * k
            p.scaleY = 0.85f + 0.15f * k
            p.alpha = v.coerceIn(0f, 1f)
            scrim.alpha = v.coerceIn(0f, 1f)
            host.onFolderDepth(v.coerceIn(0f, 1f))
        }
        spring = s
        LumaHaptics.snap(p)
        s.animateTo(1f, MotionTokens.LUMA, host.motion())
        p.post {
            val firstGrid = (contentContainer.getChildAt(0) as? RecyclerView)?.let { rv ->
                if (rv === pagesPager) (rv.getChildAt(0) as? RecyclerView) else rv
            }
            if (firstGrid != null) {
                staggerCancel?.run()
                staggerCancel = LumaSpring.staggerRecyclerView(firstGrid, host.motion())
            }
        }
        p.announceForAccessibility(context.getString(R.string.folder_opened, name))
    }

    private fun bindGridPages(
        container: FrameLayout,
        members: List<AppEntry>,
        colors: com.lumaos.pro.launcher.ui.theme.GlassColors
    ) {
        container.removeAllViews()
        pageAdapters.clear()
        pagesPager = null
        emptyView = null

        if (members.isEmpty()) {
            val empty = TextView(context).apply {
                text = context.getString(R.string.smart_folder_empty)
                setTextColor(colors.muted)
                typeface = Typefaces.body(host.theme().typography)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTokens.typeCaptionSp)
                gravity = Gravity.CENTER
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_rx_folder, 0, 0)
                compoundDrawableTintList = ColorStateList.valueOf(colors.muted)
                compoundDrawablePadding = dp(LumaTokens.spaceXS)
                setPadding(dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXL), dp(LumaTokens.spaceSM), dp(LumaTokens.spaceXL))
            }
            emptyView = empty
            container.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            dotsContainer?.visibility = View.GONE
            return
        }

        val pages = members.chunked(LumaTokens.folderMaxPerPage)
        if (pages.size == 1) {
            val grid = createSingle3x3Grid(pages[0])
            container.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            dotsContainer?.visibility = View.GONE
        } else {
            val pager = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val snap = PagerSnapHelper()
            snap.attachToRecyclerView(pager)
            val pagerAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount(): Int = pages.size
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val pageGrid = RecyclerView(parent.context).apply {
                        layoutParams = RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        layoutManager = GridLayoutManager(parent.context, LumaTokens.folderColumns)
                        overScrollMode = View.OVER_SCROLL_NEVER
                        isNestedScrollingEnabled = false
                    }
                    return object : RecyclerView.ViewHolder(pageGrid) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val pageGrid = holder.itemView as RecyclerView
                    val a = makePageAdapter(pages[position])
                    pageGrid.adapter = a
                }
            }
            pager.adapter = pagerAdapter
            pager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                        val snapView = snap.findSnapView(lm) ?: return
                        val pos = lm.getPosition(snapView)
                        if (pos != RecyclerView.NO_POSITION && pos != activePageIndex) {
                            activePageIndex = pos
                            LumaHaptics.tick(recyclerView)
                            renderDots(pages.size, colors)
                        }
                    }
                }
            })
            pagesPager = pager
            container.addView(pager, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            renderDots(pages.size, colors)
        }
    }

    private fun createSingle3x3Grid(pageItems: List<AppEntry>): RecyclerView {
        val grid = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, LumaTokens.folderColumns)
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = false
        }
        grid.adapter = makePageAdapter(pageItems)
        return grid
    }

    private fun makePageAdapter(pageItems: List<AppEntry>): AppEntryAdapter {
        val a = AppEntryAdapter(
            config = { host.folderCellConfig() },
            iconLoader = iconLoader,
            density = density,
            onClick = { entry, view -> host.launchFromFolder(entry, view) },
            onLongClick = { entry, view -> host.showFolderMemberMenu(entry, view, if (isSmartFolder) null else folderId) }
        )
        a.submitList(pageItems)
        pageAdapters += a
        return a
    }

    private fun renderDots(pageCount: Int, colors: com.lumaos.pro.launcher.ui.theme.GlassColors) {
        val dots = dotsContainer ?: return
        dots.removeAllViews()
        if (pageCount <= 1) {
            dots.visibility = View.GONE
            return
        }
        dots.visibility = View.VISIBLE
        val sizePx = dp(6f)
        val marginPx = dp(LumaTokens.spaceXXS)
        for (i in 0 until pageCount) {
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        if (i == activePageIndex) colors.content
                        else ColorMath.withAlpha(colors.content, 0.30f)
                    )
                }
            }
            dots.addView(dot, LinearLayout.LayoutParams(sizePx, sizePx).apply {
                marginStart = marginPx
                marginEnd = marginPx
            })
        }
    }

    private fun commitName() {
        val id = folderId ?: return
        val name = titleField?.text?.toString()?.trim().orEmpty()
        if (name.isNotEmpty() && name != originalName) {
            host.renameFolder(id, name)
            originalName = name
        }
    }

    fun close() {
        if (!openState) return
        val ov = overlay ?: return
        openState = false
        staggerCancel?.run()
        staggerCancel = null
        commitName()
        titleField?.let { f ->
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(f.windowToken, 0)
        }
        val s = spring
        if (s == null || !host.motion().enabled) {
            closeImmediately()
            return
        }
        s.animateTo(0f, MotionTokens.LUMA, host.motion()) {
            if (!openState && overlay === ov) closeImmediately()
        }
    }

    fun applyTheme() {
        val p = panel ?: return
        val theme = host.theme()
        val palette = host.glassPalette()
        val folderSpec = palette[GlassRole.FOLDER].copy(radiusDp = LumaTokens.radiusFolder)
        val colors = folderSpec.rest
        scrimView?.setBackgroundColor(ColorMath.withAlpha(0xFF000000.toInt(), LumaTokens.folderScrimAlpha))
        GlassViews.apply(
            view = p,
            spec = folderSpec,
            density = dpScale,
            ripple = false,
            depth = LumaTokens.depthFolder,
            radiusOverrideDp = LumaTokens.radiusFolder
        )
        smartTitleView?.apply {
            setTextColor(colors.content)
            typeface = Typefaces.title(theme.typography)
        }
        smartHintView?.apply {
            setTextColor(colors.muted)
            typeface = Typefaces.body(theme.typography)
        }
        titleField?.apply {
            setTextColor(colors.content)
            setHintTextColor(colors.muted)
            highlightColor = ColorMath.withAlpha(colors.focusRing, 0.28f)
            typeface = Typefaces.title(theme.typography)
        }
        emptyView?.apply {
            setTextColor(colors.muted)
            compoundDrawableTintList = ColorStateList.valueOf(colors.muted)
            typeface = Typefaces.body(theme.typography)
        }
        pageAdapters.forEach { a -> a.notifyItemRangeChanged(0, a.itemCount) }
    }

    fun closeImmediately() {
        openState = false
        staggerCancel?.run()
        staggerCancel = null
        spring?.cancel()
        spring = null
        overlay?.let { ov ->
            ov.removeAllViews()
            ov.visibility = View.GONE
        }
        host.onFolderDepth(0f)
        scrimView = null
        panel = null
        smartTitleView = null
        smartHintView = null
        emptyView = null
        dotsContainer = null
        pageAdapters.clear()
        pagesPager = null
        titleField = null
        folderId = null
    }

    fun refreshIcon(key: ComponentKey) {
        pageAdapters.forEach { a ->
            val pos = a.positionOf(key)
            if (pos >= 0) a.notifyItemChanged(pos)
        }
    }

    fun refreshAll() {
        pageAdapters.forEach { a -> a.notifyItemRangeChanged(0, a.itemCount) }
    }

    fun updateMembers(members: List<AppEntry>) {
        val p = panel ?: return
        val contentContainer = (0 until p.childCount)
            .map { p.getChildAt(it) }
            .firstOrNull { it is FrameLayout } as? FrameLayout ?: return
        val colors = host.glassPalette()[GlassRole.FOLDER].rest
        activePageIndex = activePageIndex.coerceAtMost(((members.size - 1).coerceAtLeast(0)) / LumaTokens.folderMaxPerPage)
        bindGridPages(contentContainer, members, colors)
    }
}
