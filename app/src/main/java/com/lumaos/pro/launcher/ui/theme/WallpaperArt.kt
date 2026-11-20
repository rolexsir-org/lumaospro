package com.lumaos.pro.launcher.ui.theme

/**
 * LumaOS Pro's theme wallpapers.
 *
 * Each of the seven approved Phase 1 themes ships its own wallpaper, rendered from
 * the approved home-screen proof (approval log 2026-11-19): Mono "Paper & Ink" / "Darkroom",
 * Glass "Prism", Monolith "Basalt & Signal", Atmosphere "Canopy" / "First Light",
 * Aurora "Borealis", Ivory "Porcelain" and Obsidian "Onyx & Ember". Zen and Void
 * keep the artwork they already had. Signature, Graphite and Chroma have none:
 * Signature paints its own authored backdrop and the other two keep the theme
 * atmosphere.
 *
 * Two themes have a day/night pair. The [daylight] variant replaces the theme's
 * primary art when the system is in light mode (Mono Paper & Ink, Atmosphere First
 * Light); [forTheme] without a mode always returns the primary (night) art.
 *
 * Artwork is drawn centre-cropped with the stated focal point, never stretched.
 * The pre-3.2 artwork is also available landscape (`drawable-land-nodpi`). The
 * Phase 2 theme wallpapers ship in portrait only, because their compositions are
 * built to be cropped to any aspect ratio.
 *
 * [supportsDarkText] belongs to the artwork itself rather than being guessed at
 * runtime: the engine reads it to choose the home foreground over the artwork.
 * [seeds] is the measured optical profile (dominant colour, band luminance and
 * the approved accent). The theme engine uses it whenever the theme's own art is
 * the backdrop, so adaptive colour and legibility scrims follow the art on screen
 * instead of the hidden system wallpaper.
 *
 * Storage keys are persisted (favourites, backups, context wallpapers). Never
 * rename one: replacing artwork keeps the key and changes only the resource.
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
    val scrimBottom: Float,
    /** Measured optical profile of the artwork. */
    val seeds: WallpaperSeeds,
    /**
     * null: the theme's only artwork. true: the light-mode half of a pair.
     * false: the dark-mode (primary) half of a pair.
     */
    val daylight: Boolean? = null
) {
    /** Aurora "Borealis" (approved 2026-11-19). The key keeps the historic Nightfall id. */
    AURORA_NIGHT(
        storageKey = "aurora_night",
        theme = ThemeId.AURORA,
        displayName = "Aurora · Borealis",
        character = "Deep-space indigo under soft aurora light fields",
        supportsDarkText = false,
        focalX = 0.5f,
        focalY = 0.40f,
        scrimTop = 0.22f,
        scrimBottom = 0.40f,
        seeds = WallpaperSeeds(
            primary = 0xFF2F2E52.toInt(),
            secondary = 0xFF3AD8C8.toInt(),
            tertiary = 0xFFD85AF0.toInt(),
            supportsDarkText = false,
            accent = 0xFF8A5AF0.toInt(),
            topLuminance = 0.06f,
            bottomLuminance = 0.02f
        )
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
        scrimBottom = 0.30f,
        seeds = WallpaperSeeds(
            primary = 0xFFE8ECE8.toInt(),
            secondary = 0xFFC5CFC5.toInt(),
            supportsDarkText = true,
            accent = 0xFF5F8068.toInt(),
            topLuminance = 0.64f,
            bottomLuminance = 0.56f
        )
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
        scrimBottom = 0.50f,
        seeds = WallpaperSeeds(
            primary = 0xFF07050D.toInt(),
            secondary = 0xFF281C44.toInt(),
            supportsDarkText = false,
            accent = 0xFF8A72E8.toInt(),
            topLuminance = 0.02f,
            bottomLuminance = 0.03f
        )
    ),

    // ------------------------------------------------ Phase 2 theme wallpapers
    // Appended after the historic entries so existing ordinals never move.

    /** Luma Mono, night: "Darkroom" carbon void stage. */
    MONO_DARKROOM(
        storageKey = "mono_darkroom",
        theme = ThemeId.LUMA_MONO,
        displayName = "Mono · Darkroom",
        character = "Carbon void stage with a single overhead light",
        supportsDarkText = false,
        focalX = 0.5f,
        focalY = 0.20f,
        scrimTop = 0.12f,
        scrimBottom = 0.24f,
        seeds = WallpaperSeeds(
            primary = 0xFF121216.toInt(),
            secondary = 0xFF22222A.toInt(),
            supportsDarkText = false,
            accent = 0xFFF2F2EE.toInt(),
            topLuminance = 0.01f,
            bottomLuminance = 0.00f
        ),
        daylight = false
    ),

    /** Luma Mono, day: "Paper & Ink" light paper stage. */
    MONO_PAPER(
        storageKey = "mono_paper",
        theme = ThemeId.LUMA_MONO,
        displayName = "Mono · Paper & Ink",
        character = "Warm-white paper stage with fine grain",
        supportsDarkText = true,
        focalX = 0.5f,
        focalY = 0.20f,
        scrimTop = 0.10f,
        scrimBottom = 0.16f,
        seeds = WallpaperSeeds(
            primary = 0xFFF2F2EF.toInt(),
            secondary = 0xFFEAEAE7.toInt(),
            supportsDarkText = true,
            accent = 0xFF1A1A1C.toInt(),
            topLuminance = 0.95f,
            bottomLuminance = 0.83f
        ),
        daylight = true
    ),

    /** Luma Glass "Prism": clear glass over deep water. */
    GLASS_PRISM(
        storageKey = "glass_prism",
        theme = ThemeId.LUMA_GLASS,
        displayName = "Glass · Prism",
        character = "Deep water lit by a slow caustic bloom",
        supportsDarkText = false,
        focalX = 0.62f,
        focalY = 0.30f,
        scrimTop = 0.18f,
        scrimBottom = 0.34f,
        seeds = WallpaperSeeds(
            primary = 0xFF172630.toInt(),
            secondary = 0xFF1F3541.toInt(),
            tertiary = 0xFF2A4F6B.toInt(),
            supportsDarkText = false,
            accent = 0xFF6FA8E8.toInt(),
            topLuminance = 0.03f,
            bottomLuminance = 0.01f
        )
    ),

    /** Monolith "Basalt & Signal": board-formed concrete. */
    MONOLITH_BASALT(
        storageKey = "monolith_basalt",
        theme = ThemeId.MONOLITH,
        displayName = "Monolith · Basalt & Signal",
        character = "Board-formed concrete under a single sun",
        supportsDarkText = true,
        focalX = 0.5f,
        focalY = 0.5f,
        scrimTop = 0.14f,
        scrimBottom = 0.20f,
        seeds = WallpaperSeeds(
            primary = 0xFFCAC9C0.toInt(),
            secondary = 0xFFC0BEB5.toInt(),
            supportsDarkText = true,
            accent = 0xFFE04E26.toInt(),
            topLuminance = 0.67f,
            bottomLuminance = 0.51f
        )
    ),

    /** Atmosphere, night (primary): "Canopy" forest light. */
    ATMOSPHERE_CANOPY(
        storageKey = "atmosphere_canopy",
        theme = ThemeId.ATMOSPHERE,
        displayName = "Atmosphere · Canopy",
        character = "Forest shafts, dappled pools and fireflies",
        supportsDarkText = false,
        focalX = 0.40f,
        focalY = 0.40f,
        scrimTop = 0.18f,
        scrimBottom = 0.34f,
        seeds = WallpaperSeeds(
            primary = 0xFF1C271E.toInt(),
            secondary = 0xFF2A3D30.toInt(),
            tertiary = 0xFFD8C87A.toInt(),
            supportsDarkText = false,
            accent = 0xFFC8D89A.toInt(),
            topLuminance = 0.03f,
            bottomLuminance = 0.01f
        ),
        daylight = false
    ),

    /** Atmosphere, day: "First Light" dawn mist with faint canopy shafts. */
    ATMOSPHERE_FIRST_LIGHT(
        storageKey = "atmosphere_first_light",
        theme = ThemeId.ATMOSPHERE,
        displayName = "Atmosphere · First Light",
        character = "Dawn mist, soft bokeh and faint canopy shafts",
        supportsDarkText = true,
        focalX = 0.5f,
        focalY = 0.16f,
        scrimTop = 0.12f,
        scrimBottom = 0.18f,
        seeds = WallpaperSeeds(
            primary = 0xFFE8E5E2.toInt(),
            secondary = 0xFFDCE4EA.toInt(),
            tertiary = 0xFFD8E2D6.toInt(),
            supportsDarkText = true,
            accent = 0xFFF0C9B4.toInt(),
            topLuminance = 0.79f,
            bottomLuminance = 0.78f
        ),
        daylight = true
    ),

    /** Ivory "Porcelain": warm ivory with a diffused daylight wash. */
    IVORY_PORCELAIN(
        storageKey = "ivory_porcelain",
        theme = ThemeId.IVORY,
        displayName = "Ivory · Porcelain",
        character = "Warm ivory under a diffused daylight wash",
        supportsDarkText = true,
        focalX = 0.5f,
        focalY = 0.24f,
        scrimTop = 0.08f,
        scrimBottom = 0.14f,
        seeds = WallpaperSeeds(
            primary = 0xFFF6F2EA.toInt(),
            secondary = 0xFFEFE9DC.toInt(),
            supportsDarkText = true,
            accent = 0xFF3A5F8A.toInt(),
            topLuminance = 0.94f,
            bottomLuminance = 0.86f
        )
    ),

    /** Obsidian "Onyx & Ember": true-black OLED field. */
    OBSIDIAN_ONYX(
        storageKey = "obsidian_onyx",
        theme = ThemeId.OBSIDIAN,
        displayName = "Obsidian · Onyx & Ember",
        character = "True-black onyx with a distant ember glow",
        supportsDarkText = false,
        focalX = 0.5f,
        focalY = 0.6f,
        scrimTop = 0.10f,
        scrimBottom = 0.20f,
        seeds = WallpaperSeeds(
            primary = 0xFF050507.toInt(),
            secondary = 0xFF16161A.toInt(),
            supportsDarkText = false,
            accent = 0xFFE8A33D.toInt(),
            topLuminance = 0.00f,
            bottomLuminance = 0.00f
        )
    );

    companion object {
        /** The primary (night or only) wallpaper for [id], or null for themes without one. */
        fun forTheme(id: ThemeId): WallpaperArt? =
            entries.firstOrNull { it.theme == id && it.daylight != true }

        /**
         * The wallpaper for [id] in the current system mode: the daylight half of a
         * pair in light mode, otherwise the primary art.
         */
        fun forTheme(id: ThemeId, systemDark: Boolean): WallpaperArt? {
            if (!systemDark) entries.firstOrNull { it.theme == id && it.daylight == true }?.let { return it }
            return forTheme(id)
        }

        /** Every wallpaper that belongs to [id], primary first. */
        fun allForTheme(id: ThemeId): List<WallpaperArt> =
            entries.filter { it.theme == id }.sortedBy { if (it.daylight == true) 1 else 0 }

        fun fromStorageKey(raw: String?): WallpaperArt? = entries.firstOrNull { it.storageKey == raw }
    }
}
