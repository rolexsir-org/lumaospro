package com.lumaos.pro.launcher.ui.theme

import android.content.Context
import androidx.annotation.DrawableRes
import com.lumaos.pro.launcher.R

/**
 * Maps [WallpaperArt] to its packaged resource.
 *
 * The same resource name exists in `drawable-nodpi` (portrait) and
 * `drawable-land-nodpi` (landscape), so Android selects the correct artwork for
 * the current configuration and LumaOS Pro never has to crop a portrait image
 * into a landscape frame. A missing asset is reported as `false` rather than
 * crashing; the home screen then restores the theme's atmospheric backdrop.
 */
object WallpaperArtResources {

    @DrawableRes
    fun res(art: WallpaperArt): Int = when (art) {
        WallpaperArt.AURORA_NIGHT -> R.drawable.luma_wall_aurora
        WallpaperArt.ZEN_MORNING -> R.drawable.luma_wall_zen
        WallpaperArt.VOID_FLUX -> R.drawable.luma_wall_void
    }

    fun available(context: Context, art: WallpaperArt): Boolean = runCatching {
        context.resources.openRawResource(res(art)).use { it.read() }
        true
    }.getOrDefault(false)
}
