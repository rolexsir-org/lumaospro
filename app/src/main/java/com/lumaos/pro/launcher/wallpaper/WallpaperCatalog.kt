package com.lumaos.pro.launcher.wallpaper

import com.lumaos.pro.launcher.ui.theme.WallpaperArt
import com.lumaos.pro.launcher.ui.theme.WallpaperSeeds

/** Curated editorial collections for the 30 LumaOS Pro bundled wallpapers. */
enum class WallpaperCollection(
    val storageKey: String,
    val title: String,
    val subtitle: String
) {
    LIQUID_DREAMS(
        storageKey = "liquid_dreams",
        title = "Liquid Dreams",
        subtitle = "Refracted glass forms and prismatic caustics"
    ),
    AURORA(
        storageKey = "aurora",
        title = "Aurora",
        subtitle = "Polar ribbons and nocturnal atmospheric light"
    ),
    MIDNIGHT(
        storageKey = "midnight",
        title = "Midnight",
        subtitle = "Deep obsidian geometry with specular edge light"
    ),
    MINIMAL(
        storageKey = "minimal",
        title = "Minimal",
        subtitle = "Architectural sculpture in calm daylight and stone"
    ),
    HIMALAYAN_ESSENCE(
        storageKey = "himalayan_essence",
        title = "Himalayan Essence",
        subtitle = "High-altitude ridges, mirror lakes and alpenglow"
    ),
    DARK_LUXE(
        storageKey = "dark_luxe",
        title = "Dark Luxe",
        subtitle = "Precision machines, nocturnal horizons and warm metal"
    );

    companion object {
        fun fromStorageKey(raw: String?): WallpaperCollection? =
            entries.firstOrNull { it.storageKey == raw }
    }
}

/** Stable IDs shared by the gallery, active selection, and persisted favourites. */
data class WallpaperEntry(
    val id: String,
    val title: String,
    val description: String,
    val art: WallpaperArt? = null,
    val resourceKey: String? = null,
    val collection: WallpaperCollection? = null,
    val mood: String = "",
    val focalX: Float = art?.focalX ?: 0.5f,
    val focalY: Float = art?.focalY ?: 0.5f,
    val precomputedSeeds: WallpaperSeeds? = null
) {
    val isCustom: Boolean get() = art == null && resourceKey == null
}

enum class WallpaperCategory { ALL, LUMA, PHOTOS, FAVORITES }

object WallpaperCatalog {
    private val customName = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")

    private fun curated(
        slug: String,
        title: String,
        description: String,
        collection: WallpaperCollection,
        mood: String,
        focalX: Float,
        focalY: Float,
        primary: Long,
        secondary: Long,
        accent: Long,
        supportsDarkText: Boolean,
        topLum: Float,
        botLum: Float
    ): WallpaperEntry = WallpaperEntry(
        id = "art:$slug",
        title = title,
        description = description,
        art = null,
        resourceKey = slug,
        collection = collection,
        mood = mood,
        focalX = focalX,
        focalY = focalY,
        precomputedSeeds = WallpaperSeeds(
            primary = primary.toInt(),
            secondary = secondary.toInt(),
            supportsDarkText = supportsDarkText,
            accent = accent.toInt(),
            topLuminance = topLum,
            bottomLuminance = botLum
        )
    )

    private val BUNDLED: List<WallpaperEntry> = listOf(
        // 01–04: Liquid Dreams
        curated(
            slug = "01_iridescent_spheres",
            title = "Iridescent Spheres",
            description = "Floating liquid-glass orbs with sapphire and violet caustics suspended in deep space.",
            collection = WallpaperCollection.LIQUID_DREAMS,
            mood = "Prismatic Glass",
            focalX = 0.50f,
            focalY = 0.64f,
            primary = 0xFF020726,
            secondary = 0xFF855FA6,
            accent = 0xFF283878,
            supportsDarkText = false,
            topLum = 0.00f,
            botLum = 0.06f
        ),
        curated(
            slug = "02_flowing_blue_glass",
            title = "Flowing Blue Glass",
            description = "Sculpted cobalt ribbons refracting icy cyan light through dark abyssal depth.",
            collection = WallpaperCollection.LIQUID_DREAMS,
            mood = "Cobalt Refraction",
            focalX = 0.54f,
            focalY = 0.56f,
            primary = 0xFF000517,
            secondary = 0xFF98E5F8,
            accent = 0xFF0D4C8C,
            supportsDarkText = false,
            topLum = 0.01f,
            botLum = 0.04f
        ),
        curated(
            slug = "03_violet_ribbons",
            title = "Violet Ribbons",
            description = "Silken amethyst waves folded across a midnight indigo field with rose highlights.",
            collection = WallpaperCollection.LIQUID_DREAMS,
            mood = "Amethyst Silk",
            focalX = 0.51f,
            focalY = 0.59f,
            primary = 0xFF090127,
            secondary = 0xFFC371A1,
            accent = 0xFF422777,
            supportsDarkText = false,
            topLum = 0.01f,
            botLum = 0.02f
        ),
        curated(
            slug = "04_translucent_sculptures",
            title = "Translucent Sculptures",
            description = "Layered glass monoliths catching cold lavender and sapphire rim light.",
            collection = WallpaperCollection.LIQUID_DREAMS,
            mood = "Crystal Monolith",
            focalX = 0.48f,
            focalY = 0.72f,
            primary = 0xFF000108,
            secondary = 0xFFC8B0E1,
            accent = 0xFF254A7A,
            supportsDarkText = false,
            topLum = 0.00f,
            botLum = 0.13f
        ),

        // 05–08: Aurora
        curated(
            slug = "05_emerald_northern_lights",
            title = "Emerald Northern Lights",
            description = "Curtains of boreal emerald sweeping quietly above a dark northern horizon.",
            collection = WallpaperCollection.AURORA,
            mood = "Boreal Emerald",
            focalX = 0.54f,
            focalY = 0.75f,
            primary = 0xFF000707,
            secondary = 0xFF022527,
            accent = 0xFF239655,
            supportsDarkText = false,
            topLum = 0.00f,
            botLum = 0.11f
        ),
        curated(
            slug = "06_violet_mountain_aurora",
            title = "Violet Mountain Aurora",
            description = "Ultraviolet polar light arcing over shadowed alpine ridgelines.",
            collection = WallpaperCollection.AURORA,
            mood = "Polar Ultraviolet",
            focalX = 0.49f,
            focalY = 0.60f,
            primary = 0xFF0B0C26,
            secondary = 0xFFCA82C9,
            accent = 0xFF5542A3,
            supportsDarkText = false,
            topLum = 0.01f,
            botLum = 0.04f
        ),
        curated(
            slug = "07_teal_lights_over_water",
            title = "Teal Lights Over Water",
            description = "Luminous sea-glass aurora mirrored across still nocturnal water.",
            collection = WallpaperCollection.AURORA,
            mood = "Sea-Glass Calm",
            focalX = 0.50f,
            focalY = 0.56f,
            primary = 0xFF000B18,
            secondary = 0xFF023A4E,
            accent = 0xFF159C95,
            supportsDarkText = false,
            topLum = 0.01f,
            botLum = 0.03f
        ),
        curated(
            slug = "08_pink_cyan_polar_skies",
            title = "Pink & Cyan Polar Skies",
            description = "Glacial cyan and soft rose currents drifting across a star-lit polar sky.",
            collection = WallpaperCollection.AURORA,
            mood = "Glacial Horizon",
            focalX = 0.50f,
            focalY = 0.64f,
            primary = 0xFF010616,
            secondary = 0xFF42B2C3,
            accent = 0xFF1C456C,
            supportsDarkText = false,
            topLum = 0.00f,
            botLum = 0.12f
        ),

        // 09–12: Midnight
        curated(
            slug = "09_obsidian_glass",
            title = "Obsidian Glass",
            description = "Smoked architectural glass planes bevelled with crisp slate reflections.",
            collection = WallpaperCollection.MIDNIGHT,
            mood = "Smoked Slate",
            focalX = 0.55f,
            focalY = 0.57f,
            primary = 0xFF050505,
            secondary = 0xFF121619,
            accent = 0xFF556376,
            supportsDarkText = false,
            topLum = 0.00f,
            botLum = 0.01f
        ),
        curated(
            slug = "10_cyan_lit_curves",
            title = "Cyan-Lit Curves",
            description = "Precision dark contours traced by a single hairline of cold cyan light.",
            collection = WallpaperCollection.MIDNIGHT,
            mood = "Hairline Cyan",
            focalX = 0.54f,
            focalY = 0.56f,
            primary = 0xFF010304,
            secondary = 0xFF0A1217,
            accent = 0xFF175E7A,
            supportsDarkText = false,
            topLum = 0.00f,
            botLum = 0.01f
        ),
        curated(
            slug = "11_floating_dark_spheres",
            title = "Floating Dark Spheres",
            description = "Matte carbon spheres poised in low-key steel blue studio light.",
            collection = WallpaperCollection.MIDNIGHT,
            mood = "Carbon Studio",
            focalX = 0.47f,
            focalY = 0.64f,
            primary = 0xFF010206,
            secondary = 0xFF070C13,
            accent = 0xFF314C6A,
            supportsDarkText = false,
            topLum = 0.00f,
            botLum = 0.01f
        ),
        curated(
            slug = "12_glowing_black_ocean_horizon",
            title = "Glowing Black Ocean",
            description = "An ink-black open sea split by a distant electric-blue horizon band.",
            collection = WallpaperCollection.MIDNIGHT,
            mood = "Abyssal Horizon",
            focalX = 0.50f,
            focalY = 0.53f,
            primary = 0xFF000208,
            secondary = 0xFF020815,
            accent = 0xFF3B7998,
            supportsDarkText = false,
            topLum = 0.00f,
            botLum = 0.00f
        ),

        // 13–16: Minimal
        curated(
            slug = "13_sage_geometric_forms",
            title = "Sage Geometric Forms",
            description = "Sun-washed botanical stone geometry with soft architectural shadows.",
            collection = WallpaperCollection.MINIMAL,
            mood = "Botanical Daylight",
            focalX = 0.64f,
            focalY = 0.45f,
            primary = 0xFFC4C5A5,
            secondary = 0xFFB7B997,
            accent = 0xFF9B9D7B,
            supportsDarkText = true,
            topLum = 0.27f,
            botLum = 0.35f
        ),
        curated(
            slug = "14_warm_ivory_curves",
            title = "Warm Ivory Curves",
            description = "Tactile travertine and warm plaster arcs bathed in quiet morning light.",
            collection = WallpaperCollection.MINIMAL,
            mood = "Warm Travertine",
            focalX = 0.48f,
            focalY = 0.75f,
            primary = 0xFFE9D9C5,
            secondary = 0xFFDDCAB4,
            accent = 0xFFC0AB93,
            supportsDarkText = true,
            topLum = 0.66f,
            botLum = 0.32f
        ),
        curated(
            slug = "15_muted_lavender_sculpture",
            title = "Muted Lavender Sculpture",
            description = "Poised lilac-grey sculptural folds designed for serene daylight legibility.",
            collection = WallpaperCollection.MINIMAL,
            mood = "Serene Lilac",
            focalX = 0.54f,
            focalY = 0.60f,
            primary = 0xFFBCB5CA,
            secondary = 0xFFCAC4D5,
            accent = 0xFFA597B8,
            supportsDarkText = true,
            topLum = 0.57f,
            botLum = 0.39f
        ),
        curated(
            slug = "16_graphite_silver_shapes",
            title = "Graphite & Silver Shapes",
            description = "Brushed titanium and matte graphite forms with restrained monochrome poise.",
            collection = WallpaperCollection.MINIMAL,
            mood = "Brushed Titanium",
            focalX = 0.68f,
            focalY = 0.68f,
            primary = 0xFF151618,
            secondary = 0xFF585859,
            accent = 0xFF353638,
            supportsDarkText = false,
            topLum = 0.00f,
            botLum = 0.07f
        ),

        // 17–20: Himalayan Essence
        curated(
            slug = "17_snow_peaks_at_sunrise",
            title = "Snow Peaks at Sunrise",
            description = "First alpenglow warming high Himalayan snow crests above indigo valleys.",
            collection = WallpaperCollection.HIMALAYAN_ESSENCE,
            mood = "First Alpenglow",
            focalX = 0.48f,
            focalY = 0.75f,
            primary = 0xFF031C35,
            secondary = 0xFF23456A,
            accent = 0xFFF3BCA1,
            supportsDarkText = false,
            topLum = 0.56f,
            botLum = 0.05f
        ),
        curated(
            slug = "18_lake",
            title = "Alpine Mirror Lake",
            description = "Still glacial water reflecting twilight slate peaks and warm dusk clouds.",
            collection = WallpaperCollection.HIMALAYAN_ESSENCE,
            mood = "Glacial Dusk",
            focalX = 0.53f,
            focalY = 0.54f,
            primary = 0xFF1A3755,
            secondary = 0xFFE0AB8B,
            accent = 0xFF365476,
            supportsDarkText = false,
            topLum = 0.06f,
            botLum = 0.11f
        ),
        curated(
            slug = "19_moonlit_mountains",
            title = "Moonlit Mountains",
            description = "High-altitude granite ridges bathed in clear silver-blue moonlight.",
            collection = WallpaperCollection.HIMALAYAN_ESSENCE,
            mood = "Silver Summit",
            focalX = 0.49f,
            focalY = 0.60f,
            primary = 0xFF062857,
            secondary = 0xFF041837,
            accent = 0xFF174174,
            supportsDarkText = false,
            topLum = 0.02f,
            botLum = 0.02f
        ),
        curated(
            slug = "20_golden_mist_ridges",
            title = "Golden Mist Ridges",
            description = "Layered valley mist gilded by amber dawn light over dark cedar slopes.",
            collection = WallpaperCollection.HIMALAYAN_ESSENCE,
            mood = "Amber Valley Mist",
            focalX = 0.50f,
            focalY = 0.75f,
            primary = 0xFF28333C,
            secondary = 0xFF283740,
            accent = 0xFFDFAD72,
            supportsDarkText = false,
            topLum = 0.48f,
            botLum = 0.07f
        ),

        // 21–30: Dark Luxe
        curated(
            slug = "21_midnight_coupe",
            title = "Midnight Coupé",
            description = "Coachbuilt grand-touring silhouette under cool studio steel and warm leather.",
            collection = WallpaperCollection.DARK_LUXE,
            mood = "Grand Tourer",
            focalX = 0.52f,
            focalY = 0.63f,
            primary = 0xFF090A0B,
            secondary = 0xFF614534,
            accent = 0xFF2F5584,
            supportsDarkText = false,
            topLum = 0.01f,
            botLum = 0.06f
        ),
        curated(
            slug = "22_golden_skyline",
            title = "Golden Skyline",
            description = "Nocturnal architectural towers punctuated by warm champagne window light.",
            collection = WallpaperCollection.DARK_LUXE,
            mood = "Champagne Nocturne",
            focalX = 0.50f,
            focalY = 0.58f,
            primary = 0xFF050709,
            secondary = 0xFF14171A,
            accent = 0xFF785F45,
            supportsDarkText = false,
            topLum = 0.00f,
            botLum = 0.01f
        ),
        curated(
            slug = "23_obsidian_and_gold",
            title = "Obsidian & Gold",
            description = "Jet-black sculpted facets inlaid with brushed warm gold highlights.",
            collection = WallpaperCollection.DARK_LUXE,
            mood = "Gilded Obsidian",
            focalX = 0.43f,
            focalY = 0.58f,
            primary = 0xFF060708,
            secondary = 0xFF141617,
            accent = 0xFFAB9C8E,
            supportsDarkText = false,
            topLum = 0.01f,
            botLum = 0.01f
        ),
        curated(
            slug = "24_moonlit_himalayan_lake",
            title = "Moonlit Himalayan Tarn",
            description = "Starlit high-mountain tarn with a quiet copper horizon reflection.",
            collection = WallpaperCollection.DARK_LUXE,
            mood = "Nocturnal Tarn",
            focalX = 0.60f,
            focalY = 0.53f,
            primary = 0xFF040607,
            secondary = 0xFFD7AB92,
            accent = 0xFF233244,
            supportsDarkText = false,
            topLum = 0.01f,
            botLum = 0.03f
        ),
        curated(
            slug = "25_black_chronograph",
            title = "Black Chronograph",
            description = "Haute-horlogerie dark dial macro with diamond-cut gunmetal indices.",
            collection = WallpaperCollection.DARK_LUXE,
            mood = "Horology Macro",
            focalX = 0.48f,
            focalY = 0.52f,
            primary = 0xFF040608,
            secondary = 0xFF46494D,
            accent = 0xFF36393C,
            supportsDarkText = false,
            topLum = 0.00f,
            botLum = 0.01f
        ),
        curated(
            slug = "26_rainy_roadster",
            title = "Rainy Roadster",
            description = "Wet asphalt reflections and crimson tail-lamp glow in midnight rain.",
            collection = WallpaperCollection.DARK_LUXE,
            mood = "Crimson Rain",
            focalX = 0.34f,
            focalY = 0.55f,
            primary = 0xFF050708,
            secondary = 0xFF5C6C79,
            accent = 0xFF72161F,
            supportsDarkText = false,
            topLum = 0.01f,
            botLum = 0.03f
        ),
        curated(
            slug = "27_black_tide",
            title = "Black Tide",
            description = "Dark ocean swell catching warm bronze twilight along velvet-black crests.",
            collection = WallpaperCollection.DARK_LUXE,
            mood = "Bronze Swell",
            focalX = 0.52f,
            focalY = 0.54f,
            primary = 0xFF0D1115,
            secondary = 0xFF6A7080,
            accent = 0xFFA5795C,
            supportsDarkText = false,
            topLum = 0.02f,
            botLum = 0.03f
        ),
        curated(
            slug = "28_dusk_flight",
            title = "Dusk Flight",
            description = "Above-the-clouds twilight wingline glowing with warm ember afterlight.",
            collection = WallpaperCollection.DARK_LUXE,
            mood = "Ember Altitude",
            focalX = 0.46f,
            focalY = 0.48f,
            primary = 0xFF030201,
            secondary = 0xFFFDC17E,
            accent = 0xFFCE7D6B,
            supportsDarkText = false,
            topLum = 0.02f,
            botLum = 0.01f
        ),
        curated(
            slug = "29_midnight_motorcycle",
            title = "Midnight Café Racer",
            description = "Sculpted dark alloy tank and warm exhaust patina under night lamps.",
            collection = WallpaperCollection.DARK_LUXE,
            mood = "Dark Alloy",
            focalX = 0.49f,
            focalY = 0.75f,
            primary = 0xFF060909,
            secondary = 0xFF695041,
            accent = 0xFF5A7481,
            supportsDarkText = false,
            topLum = 0.01f,
            botLum = 0.05f
        ),
        curated(
            slug = "30_milky_way_alpine_lake",
            title = "Milky Way Alpine Lake",
            description = "Galactic core rising in warm amber dust above a mirror-still alpine lake.",
            collection = WallpaperCollection.DARK_LUXE,
            mood = "Galactic Core",
            focalX = 0.46f,
            focalY = 0.63f,
            primary = 0xFF020406,
            secondary = 0xFF394451,
            accent = 0xFF80553C,
            supportsDarkText = false,
            topLum = 0.00f,
            botLum = 0.01f
        )
    )

    private val LEGACY: List<WallpaperEntry> = WallpaperArt.entries.map { art ->
        val seeds = when (art) {
            WallpaperArt.AURORA_NIGHT -> WallpaperSeeds(
                primary = 0xFF050E16.toInt(),
                secondary = 0xFF1D6B68.toInt(),
                supportsDarkText = false,
                accent = 0xFF4AD2C0.toInt(),
                topLuminance = 0.03f,
                bottomLuminance = 0.05f
            )
            WallpaperArt.ZEN_MORNING -> WallpaperSeeds(
                primary = 0xFFE8ECE8.toInt(),
                secondary = 0xFFC5CFC5.toInt(),
                supportsDarkText = true,
                accent = 0xFF5F8068.toInt(),
                topLuminance = 0.64f,
                bottomLuminance = 0.56f
            )
            WallpaperArt.VOID_FLUX -> WallpaperSeeds(
                primary = 0xFF07050D.toInt(),
                secondary = 0xFF281C44.toInt(),
                supportsDarkText = false,
                accent = 0xFF8A72E8.toInt(),
                topLuminance = 0.02f,
                bottomLuminance = 0.03f
            )
        }
        WallpaperEntry(
            id = "art:${art.storageKey}",
            title = art.displayName,
            description = art.character,
            art = art,
            mood = "Signature Classic",
            focalX = art.focalX,
            focalY = art.focalY,
            precomputedSeeds = seeds
        )
    }

    private val BY_ID: Map<String, WallpaperEntry> =
        (BUNDLED + LEGACY).associateBy { it.id }

    /** The 30 curated LumaOS Pro wallpapers organized across 6 editorial collections. */
    fun bundled(): List<WallpaperEntry> = BUNDLED

    /** Legacy v3.2 theme-linked artworks kept resolvable for existing favourites/backups. */
    fun legacyBundled(): List<WallpaperEntry> = LEGACY

    /** Finds a bundled or legacy wallpaper entry by its stable `art:...` identifier. */
    fun findBundled(id: String?): WallpaperEntry? = id?.let { BY_ID[it] }

    fun customId(name: String): String? = name.takeIf(customName::matches)?.let { "photo:$it" }
    fun customName(id: String): String? = id.removePrefix("photo:")
        .takeIf { id.startsWith("photo:") && customName.matches(it) }

    fun filter(
        entries: List<WallpaperEntry>,
        category: WallpaperCategory,
        favorites: Set<String>,
        collection: WallpaperCollection? = null
    ): List<WallpaperEntry> = entries.filter { entry ->
        val matchesCategory = when (category) {
            WallpaperCategory.ALL -> true
            WallpaperCategory.LUMA -> !entry.isCustom
            WallpaperCategory.PHOTOS -> entry.isCustom
            WallpaperCategory.FAVORITES -> entry.id in favorites
        }
        val matchesCollection = collection == null ||
            category == WallpaperCategory.PHOTOS ||
            entry.collection == collection
        matchesCategory && matchesCollection
    }
}

/** Conservative bounds for untrusted image files and decoded bitmap memory. */
object WallpaperLimits {
    const val MAX_IMPORTS = 12
    const val MAX_FILE_BYTES = 12L * 1024 * 1024
    const val MAX_SOURCE_EDGE = 20_000
    const val MAX_SOURCE_PIXELS = 100_000_000L
    const val MAX_APPLY_EDGE = 2560
    const val MAX_APPLY_PIXELS = 5_000_000L

    fun validSource(width: Int, height: Int): Boolean = width in 1..MAX_SOURCE_EDGE &&
        height in 1..MAX_SOURCE_EDGE && width.toLong() * height <= MAX_SOURCE_PIXELS

    /** Power of two accepted by BitmapFactory; rounded up dimensions stay in budget. */
    fun sampleSize(width: Int, height: Int, maxEdge: Int, maxPixels: Long): Int {
        require(validSource(width, height) && maxEdge > 0 && maxPixels > 0)
        var sample = 1
        fun dimension(n: Int) = (n.toLong() + sample - 1) / sample
        while (dimension(width) > maxEdge || dimension(height) > maxEdge ||
            dimension(width) * dimension(height) > maxPixels) sample *= 2
        return sample
    }
}
