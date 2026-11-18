package com.lumaos.pro.launcher.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.icons.LivingIconId
import com.lumaos.pro.launcher.icons.ThemeGlyphDrawable
import com.lumaos.pro.launcher.icons.ThemeIconPack
import com.lumaos.pro.launcher.ui.theme.ThemeId

/** Shared entry point for launcher-owned glyphs; non-catalog resources pass through. */
object ThemedUiIcons {
    private data class Binding(val res: Int, val theme: ThemeId)

    fun activeTheme(context: Context): ThemeId =
        (context.applicationContext as? LumaApp)?.core?.themes?.theme?.value?.id ?: ThemeId.SIGNATURE

    fun drawable(context: Context, @DrawableRes res: Int, theme: ThemeId = activeTheme(context)): Drawable? {
        val base = ContextCompat.getDrawable(context, res)?.mutate() ?: return null
        val pack = ThemeIconPack.forTheme(theme) ?: return base
        if (!context.resources.getResourceEntryName(res).startsWith("ic_rx_")) return base
        val symbol = when (res) {
            R.drawable.ic_rx_camera -> LivingIconId.CAMERA
            R.drawable.ic_rx_settings -> LivingIconId.SETTINGS
            R.drawable.ic_rx_folder -> LivingIconId.FILES
            R.drawable.ic_rx_store -> LivingIconId.APP_STORE
            R.drawable.ic_rx_voice -> LivingIconId.RECORDER
            R.drawable.ic_rx_web -> LivingIconId.BROWSER
            else -> null
        }
        return ThemeGlyphDrawable(pack, base, base.intrinsicWidth, symbol)
    }

    fun bind(view: ImageView, @DrawableRes res: Int, theme: ThemeId = activeTheme(view.context)) {
        view.setImageDrawable(drawable(view.context, res, theme))
        view.setTag(R.id.themed_icon_binding, Binding(res, theme))
    }

    /** Rebind persistent controls when the resolved pack changes. */
    fun refresh(root: View, theme: ThemeId) {
        if (root is ImageView) {
            val binding = root.getTag(R.id.themed_icon_binding) as? Binding
            if (binding != null && binding.theme != theme) bind(root, binding.res, theme)
        }
        if (root is ViewGroup) for (i in 0 until root.childCount) refresh(root.getChildAt(i), theme)
    }
}
