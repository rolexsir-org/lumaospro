package com.lumaos.pro.launcher.ui.theme

import android.content.Context
import androidx.annotation.DrawableRes
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.wallpaper.WallpaperEntry

/**
 * Maps [WallpaperArt] and curated [WallpaperEntry] keys to their packaged resources.
 *
 * Theme-linked legacy artwork exists in `drawable-nodpi` and `drawable-land-nodpi`;
 * the 30 Wallpaper Studio compositions live in `drawable-nodpi` at native aspect ratio
 * and are focal-point cropped by [com.lumaos.pro.launcher.launcher.home.WallpaperArtView]
 * and the Studio preview without distortion. A missing asset is reported cleanly
 * rather than crashing.
 */
object WallpaperArtResources {

    @DrawableRes
    fun res(art: WallpaperArt): Int = when (art) {
        WallpaperArt.AURORA_NIGHT -> R.drawable.luma_wall_aurora
        WallpaperArt.ZEN_MORNING -> R.drawable.luma_wall_zen
        WallpaperArt.VOID_FLUX -> R.drawable.luma_wall_void
    }

    @DrawableRes
    fun resForKey(resourceKey: String): Int? = when (resourceKey) {
        "01_iridescent_spheres" -> R.drawable.luma_wall_01_iridescent_spheres
        "02_flowing_blue_glass" -> R.drawable.luma_wall_02_flowing_blue_glass
        "03_violet_ribbons" -> R.drawable.luma_wall_03_violet_ribbons
        "04_translucent_sculptures" -> R.drawable.luma_wall_04_translucent_sculptures
        "05_emerald_northern_lights" -> R.drawable.luma_wall_05_emerald_northern_lights
        "06_violet_mountain_aurora" -> R.drawable.luma_wall_06_violet_mountain_aurora
        "07_teal_lights_over_water" -> R.drawable.luma_wall_07_teal_lights_over_water
        "08_pink_cyan_polar_skies" -> R.drawable.luma_wall_08_pink_cyan_polar_skies
        "09_obsidian_glass" -> R.drawable.luma_wall_09_obsidian_glass
        "10_cyan_lit_curves" -> R.drawable.luma_wall_10_cyan_lit_curves
        "11_floating_dark_spheres" -> R.drawable.luma_wall_11_floating_dark_spheres
        "12_glowing_black_ocean_horizon" -> R.drawable.luma_wall_12_glowing_black_ocean_horizon
        "13_sage_geometric_forms" -> R.drawable.luma_wall_13_sage_geometric_forms
        "14_warm_ivory_curves" -> R.drawable.luma_wall_14_warm_ivory_curves
        "15_muted_lavender_sculpture" -> R.drawable.luma_wall_15_muted_lavender_sculpture
        "16_graphite_silver_shapes" -> R.drawable.luma_wall_16_graphite_silver_shapes
        "17_snow_peaks_at_sunrise" -> R.drawable.luma_wall_17_snow_peaks_at_sunrise
        "18_lake" -> R.drawable.luma_wall_18_lake
        "19_moonlit_mountains" -> R.drawable.luma_wall_19_moonlit_mountains
        "20_golden_mist_ridges" -> R.drawable.luma_wall_20_golden_mist_ridges
        "21_midnight_coupe" -> R.drawable.luma_wall_21_midnight_coupe
        "22_golden_skyline" -> R.drawable.luma_wall_22_golden_skyline
        "23_obsidian_and_gold" -> R.drawable.luma_wall_23_obsidian_and_gold
        "24_moonlit_himalayan_lake" -> R.drawable.luma_wall_24_moonlit_himalayan_lake
        "25_black_chronograph" -> R.drawable.luma_wall_25_black_chronograph
        "26_rainy_roadster" -> R.drawable.luma_wall_26_rainy_roadster
        "27_black_tide" -> R.drawable.luma_wall_27_black_tide
        "28_dusk_flight" -> R.drawable.luma_wall_28_dusk_flight
        "29_midnight_motorcycle" -> R.drawable.luma_wall_29_midnight_motorcycle
        "30_milky_way_alpine_lake" -> R.drawable.luma_wall_30_milky_way_alpine_lake
        WallpaperArt.AURORA_NIGHT.storageKey -> R.drawable.luma_wall_aurora
        WallpaperArt.ZEN_MORNING.storageKey -> R.drawable.luma_wall_zen
        WallpaperArt.VOID_FLUX.storageKey -> R.drawable.luma_wall_void
        else -> null
    }

    @DrawableRes
    fun resForEntry(entry: WallpaperEntry): Int? = when {
        entry.art != null -> res(entry.art)
        entry.resourceKey != null -> resForKey(entry.resourceKey)
        else -> null
    }

    fun available(context: Context, art: WallpaperArt): Boolean =
        availableRes(context, res(art))

    fun available(context: Context, entry: WallpaperEntry): Boolean {
        val resId = resForEntry(entry) ?: return false
        return availableRes(context, resId)
    }

    private fun availableRes(context: Context, @DrawableRes resId: Int): Boolean = runCatching {
        context.resources.openRawResource(resId).use { it.read() }
        true
    }.getOrDefault(false)
}
