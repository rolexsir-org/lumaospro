package com.lumaos.pro.launcher.ui.theme

/**
 * LumaOS Pro's bundled wallpaper collection.
 *
 * Each signature theme ships one art-directed wallpaper so its identity is
 * complete out of the box. The artwork is authored for both portrait and
 * landscape (the same resource name in `drawable-nodpi` and
 * `drawable-land-nodpi`), and is always drawn centre-cropped with the stated
 * focal point - never stretched.
 *
 * [supportsDarkText] is a property of the artwork itself, not a guess at
 * runtime: the Zen wallpaper is a light, misty landscape and therefore uses dark
 * text; Aurora and Void are dark and use light text. This is what the theme
 * engine consults when the home foreground sits on the artwork.
 *
 * If an asset is ever missing from a build, [forTheme] still returns the spec and
 * the resource resolver reports failure; the home screen then restores the
 * theme's atmospheric backdrop instead of showing a black square.
 */
enum class WallpaperArt(
    val storageKey: String,
    val theme: ThemeId,
    val displayName: String,
    val character: String,
    /** True when the artwork is light enough for dark text. */
    val supportsDarkText: Boolean,
    /** Normalised focal point used by centre-crop on unusual aspect ratios. */
    val focalX: Float,
    val focalY: Float,
    /** Legibility scrim above and below the composition, as alpha 0..1. */
    val scrimTop: Float,
    val scrimBottom: Float
) {
    AURORA_NIGHT(
        storageKey = "aurora_night",
        theme = ThemeId.AURORA,
        displayName = "Aurora · Nightfall",
        character = "Midnight ribbon light",
        supportsDarkText = false,
        focalX = 0.5f,
        focalY = 0.46f,
        scrimTop = 0.26f,
        scrimBottom = 0.48f
    ),
    ZEN_MORNING(
        storageKey = "zen_morning",
        theme = ThemeId.PORCELAIN,
        displayName = "Zen · Morning Mist",
        character = "Layered hills and negative space",
        supportsDarkText = true,
        focalX = 0.5f,
        focalY = 0.58f,
        scrimTop = 0.20f,
        scrimBottom = 0.30f
    ),
    VOID_FLUX(
        storageKey = "void_flux",
        theme = ThemeId.VECTOR,
        displayName = "Void · Flux",
        character = "Layered violet and cyan geometry",
        supportsDarkText = false,
        focalX = 0.5f,
        focalY = 0.44f,
        scrimTop = 0.24f,
        scrimBottom = 0.50f
    );

    companion object {
        /** The bundled wallpaper for [id], or null for themes without one. */
        fun forTheme(id: ThemeId): WallpaperArt? = entries.firstOrNull { it.theme == id }

        fun fromStorageKey(raw: String?): WallpaperArt? = entries.firstOrNull { it.storageKey == raw }
    }
}
