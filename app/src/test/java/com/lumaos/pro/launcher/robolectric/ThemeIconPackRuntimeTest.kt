package com.lumaos.pro.launcher.robolectric

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.icons.IconRenderSpec
import com.lumaos.pro.launcher.icons.IconRenderer
import com.lumaos.pro.launcher.icons.LivingIconId
import com.lumaos.pro.launcher.icons.ThemeGlyphDrawable
import com.lumaos.pro.launcher.icons.ThemeIconPack
import com.lumaos.pro.launcher.ui.ThemedUiIcons
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeId
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeIconPackRuntimeTest {
    @Test
    fun everyNonDefaultThemeDrawsDistinctControlAndAppArtwork() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val themes = ThemeId.entries.filterNot { it == ThemeId.SIGNATURE }
        val controlMasks = mutableSetOf<List<Boolean>>()
        val appMasks = mutableSetOf<List<Boolean>>()
        for (id in themes) {
            val pack = checkNotNull(ThemeIconPack.forTheme(id))
            val control = checkNotNull(ThemedUiIcons.drawable(context, R.drawable.ic_rx_search, id))
            controlMasks += alphaMask(draw(control))
            val theme = ThemeEngine.resolve(ThemeSettings().pinned(id), ThemeEnvironment(true, 720))
            val spec = IconRenderSpec.from(theme, 64, depth = false, iconPack = "example.pack")
            assertNull(spec.iconPack)
            appMasks += alphaMask(IconRenderer().render(
                ThemeGlyphDrawable(pack, null, 64, LivingIconId.CAMERA), spec, fromIconPack = false
            ))
        }
        assertEquals(themes.size, controlMasks.size)
        assertEquals(themes.size, appMasks.size)
    }

    @Test
    fun persistentViewRestoresItsOriginalGlyphWhenReturningToSignature() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val view = ImageView(context)
        val layout = android.widget.FrameLayout(context).apply { addView(view) }
        ThemedUiIcons.bind(view, R.drawable.ic_rx_sort, ThemeId.ATMOSPHERE)
        assertTrue(view.drawable is ThemeGlyphDrawable)
        ThemedUiIcons.refresh(layout, ThemeId.SIGNATURE)
        assertFalse(view.drawable is ThemeGlyphDrawable)
        assertEquals(
            alphaMask(draw(checkNotNull(context.getDrawable(R.drawable.ic_rx_sort)))),
            alphaMask(draw(view.drawable))
        )
    }

    private fun draw(drawable: Drawable): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also {
        drawable.setBounds(0, 0, 64, 64)
        drawable.draw(Canvas(it))
    }

    private fun alphaMask(bitmap: Bitmap): List<Boolean> = List(bitmap.width * bitmap.height) { i ->
        bitmap.getPixel(i % bitmap.width, i / bitmap.width) ushr 24 > 127
    }.also { assertTrue(it.any { pixel -> pixel }) }
}
