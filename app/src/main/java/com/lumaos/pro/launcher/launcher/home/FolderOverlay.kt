package com.lumaos.pro.launcher.launcher.home

import android.content.Context
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
import androidx.recyclerview.widget.RecyclerView
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.apps.AppEntry
import com.lumaos.pro.launcher.launcher.AppEntryAdapter
import com.lumaos.pro.launcher.launcher.CellConfig
import com.lumaos.pro.launcher.launcher.IconLoader
import com.lumaos.pro.launcher.motion.MotionTokens
import com.lumaos.pro.launcher.motion.SpringAnimator
import com.lumaos.pro.launcher.ui.glass.GlassViews
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.LumaTypeScale
import com.lumaos.pro.launcher.ui.theme.ResolvedTheme

/**
 * In-window folder panel. Opens from the folder icon on a spring (so a tap
 * during the opening motion reverses it cleanly), recedes the desktop behind
 * it, and supports inline renaming. Smart folders show their live members and
 * explain where they come from.
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
        fun motion(): com.lumaos.pro.launcher.motion.MotionPolicy
        fun translucency(): Boolean
        fun folderCellConfig(): CellConfig
        fun onFolderDepth(progress: Float)
        fun launchFromFolder(entry: AppEntry, view: View)
        fun showFolderMemberMenu(entry: AppEntry, anchor: View, folderId: String?)
        fun renameFolder(folderId: String, name: String)
    }

    private val dpScale = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * dpScale).toInt()

    private var overlay: FrameLayout? = null
    private var panel: LinearLayout? = null
    private var adapter: AppEntryAdapter? = null
    private var folderId: String? = null
    private var titleField: EditText? = null
    private var originalName: String = ""
    private var spring: SpringAnimator? = null

    val isOpen: Boolean get() = overlay != null

    fun open(name: String, id: String, members: List<AppEntry>, smart: Boolean, anchor: View?) {
        closeImmediately()
        val theme = host.theme()
        val c = theme.colors
        val palette = GlassMaterials.palette(
            theme,
            GlassCapabilities(
                translucency = host.translucency(),
                blur = host.translucency(),
                depth = true
            )
        )
        val folderSpec = palette[GlassRole.FOLDER]
        val colors = folderSpec.rest
        folderId = id
        originalName = name

        val ov = FrameLayout(context)
        val scrim = View(context).apply {
            setBackgroundColor(ColorMath.withAlpha(c.scrim, 0.85f))
            setOnClickListener { close() }
            contentDescription = context.getString(R.string.folder_close_description)
        }
        ov.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val p = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(16f), dp(18f), dp(14f))
            isClickable = true
        }
        GlassViews.apply(p, folderSpec, dpScale, ripple = false)

        if (smart) {
            p.addView(TextView(context).apply {
                text = name
                setTextColor(colors.content)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.titleLargeSp)
                typeface = Typefaces.title(theme.typography)
                letterSpacing = theme.typography.titleTracking
                setPadding(dp(6f), 0, dp(6f), 0)
            })
            p.addView(TextView(context).apply {
                text = context.getString(R.string.smart_folder_hint)
                setTextColor(colors.muted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodySmallSp)
                typeface = Typefaces.body(theme.typography)
                setPadding(dp(6f), dp(2f), dp(6f), dp(8f))
            })
        } else {
            titleField = EditText(context).apply {
                setText(name)
                setTextColor(colors.content)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.titleLargeSp)
                typeface = Typefaces.title(theme.typography)
                letterSpacing = theme.typography.titleTracking
                background = GlassViews.drawable(palette[GlassRole.SEARCH], dpScale, ripple = false)
                setPadding(dp(12f), dp(4f), dp(12f), dp(10f))
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
            p.addView(titleField)
        }

        val columns = if (members.size <= 4) members.size.coerceAtLeast(2) else if (members.size <= 9) 3 else 4
        val list = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, columns)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val a = AppEntryAdapter(
            config = { host.folderCellConfig() },
            iconLoader = iconLoader,
            density = density,
            onClick = { entry, view -> host.launchFromFolder(entry, view) },
            onLongClick = { entry, view -> host.showFolderMemberMenu(entry, view, if (smart) null else folderId) }
        )
        a.submitList(members)
        list.adapter = a
        adapter = a
        p.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (members.isEmpty()) {
            p.addView(TextView(context).apply {
                text = context.getString(R.string.smart_folder_empty)
                setTextColor(colors.muted)
                typeface = Typefaces.body(theme.typography)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LumaTypeScale.bodyMediumSp)
                gravity = Gravity.CENTER
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_rx_folder, 0, 0)
                compoundDrawablePadding = dp(10f)
                setPadding(dp(12f), dp(24f), dp(12f), dp(24f))
            })
        }

        val width = minOf(root.width - dp(40f), dp(420f)).coerceAtLeast(dp(260f))
        val params = FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        ov.addView(p, params)
        root.addView(ov, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay = ov
        panel = p

        // Spring out of the folder icon's position.
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
            p.scaleX = 0.82f + 0.18f * k
            p.scaleY = 0.82f + 0.18f * k
            p.alpha = v.coerceIn(0f, 1f)
            scrim.alpha = v.coerceIn(0f, 1f)
            host.onFolderDepth(v.coerceIn(0f, 1f))
        }
        spring = s
        s.animateTo(1f, MotionTokens.LIVELY, host.motion())
        p.announceForAccessibility(context.getString(R.string.folder_opened, name))
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
        val ov = overlay ?: return
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
        s.animateTo(0f, MotionTokens.SNAPPY, host.motion()) {
            if (overlay === ov) closeImmediately()
        }
    }

    fun closeImmediately() {
        overlay?.let { root.removeView(it) }
        if (overlay != null) host.onFolderDepth(0f)
        overlay = null
        panel = null
        adapter = null
        titleField = null
        folderId = null
        spring?.cancel()
        spring = null
    }

    fun refreshIcon(key: com.lumaos.pro.launcher.core.model.ComponentKey) {
        val a = adapter ?: return
        val pos = a.positionOf(key)
        if (pos >= 0) a.notifyItemChanged(pos)
    }

    fun updateMembers(members: List<AppEntry>) {
        adapter?.submitList(members)
    }
}
