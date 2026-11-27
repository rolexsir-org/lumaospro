package com.lumaos.pro.launcher.notifications

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Grouping is presentation only: expanding never creates or cancels notifications. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationGroupViewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val theme = ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(true, 720))
    private val host = object : NotificationCardHost {
        override fun theme() = theme
        override fun glassPalette() = GlassMaterials.palette(theme, GlassCapabilities.DEFAULT)
        override fun density() = context.resources.displayMetrics.density
        override fun appLabel(packageName: String): String? = null
        override fun appIcon(packageName: String): Drawable? = null
        override fun onOpen(card: NotificationCardModel) = Unit
        override fun onDismiss(card: NotificationCardModel) = Unit
        override fun onReply(card: NotificationCardModel, actionIndex: Int, text: String) = Unit
        override fun onAction(card: NotificationCardModel, actionIndex: Int) = Unit
        override fun onEnableAccess() = Unit
    }

    private fun card(key: String) = NotificationCardModel(
        key = key, packageName = "example.app", appLabel = "Example",
        title = key, body = "Text", collapsedCount = 0, postedAtMillis = 1L,
        ongoing = false, clearable = true, canOpen = true, contentHidden = false,
        actions = emptyList(), reply = null, conversation = false, groupKey = "real-group"
    )

    private fun findText(view: View, text: String): TextView? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findText(view.getChildAt(i), text)?.let { return it }
        }
        return null
    }

    @Test fun groupExpandsAndCollapsesWithoutRemovingChildren() {
        val adapter = NotificationCardAdapter(context, host)
        val cards = listOf(card("one"), card("two"))
        adapter.submit(cards, emptySet())
        assertEquals(1, adapter.itemCount)
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        val expand = context.resources.getQuantityString(R.plurals.notification_group_expand, 2, 2)
        val expandButton = findText(holder.itemView, expand)
        assertNotNull(expandButton)
        expandButton!!.performClick()
        assertEquals(2, adapter.itemCount)
        adapter.onBindViewHolder(holder, 0)
        val collapse = context.resources.getQuantityString(R.plurals.notification_group_collapse, 2, 2)
        findText(holder.itemView, collapse)!!.performClick()
        assertEquals(1, adapter.itemCount)
        adapter.submit(listOf(cards.last()), emptySet())
        assertEquals(1, adapter.itemCount)
    }
}
