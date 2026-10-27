package com.lumaos.pro.launcher.ui.theme

/**
 * LumaOS Design System **v3.2** tokens.
 *
 * This file is the Kotlin transcription of the authored token bundle that ships
 * in the token JSON files under `docs/design/v3.2/tokens` (exported by the LumaOS Studio). It is the
 * single source of numbers for the three v3.2 themes — Signature, Atmosphere and
 * Monolith — and it is deliberately plain Kotlin with no Android imports so
 * `DesignTokensParityTest` can compare it against the JSON on the JVM, pixel for
 * pixel, without a device.
 *
 * Two rules matter here:
 *
 *  - **Components ask for a role, never for a number.** A screen asks for
 *    `theme.radius(RadiusRole.LARGE)` or `LumaDesignTokens.spacing(5)`, not for
 *    26 dp or 16 dp.
 *  - **The JSON is the source of truth.** If a value in this file disagrees with
 *    `docs/design/v3.2/tokens`, the parity test fails. That is what stops the
 *    design bundle in Drive and the shipped app drifting apart.
 *
 * Colour values are stored as packed ARGB `Int`s, the representation the rest of
 * the theme engine and Android itself use. Authored translucent roles
 * (`rgba(255,255,255,.045)`) keep their alpha here and are composited over the
 * theme's own background by `ThemeEngine`; they are never flattened at authoring
 * time, because the same token has to work over the wall and over a solid panel.
 */
object LumaDesignTokens {

    /** Design-system version this file mirrors. */
    const val VERSION = "3.2"

    // ------------------------------------------------------------------ spacing

    /**
     * The one spacing scale: 2 · 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 56 · 72.
     *
     * [spacing] is 1-based so the token name and the Kotlin call match
     * (`sp-5` = `spacing(5)` = 16 dp). Any gap that is not on this scale is a
     * defect, not a detail.
     */
    val SPACING_DP: List<Float> = listOf(2f, 4f, 8f, 12f, 16f, 20f, 24f, 32f, 40f, 56f, 72f)

    /** Spacing token `sp-1` … `sp-11` in dp. Values outside the scale are clamped. */
    fun spacing(step: Int): Float = SPACING_DP[step.coerceIn(1, SPACING_DP.size) - 1]

    const val SP_1 = 2f
    const val SP_2 = 4f
    const val SP_3 = 8f
    const val SP_4 = 12f
    const val SP_5 = 16f
    const val SP_6 = 20f
    const val SP_7 = 24f
    const val SP_8 = 32f
    const val SP_9 = 40f
    const val SP_10 = 56f
    const val SP_11 = 72f

    /** Screen gutter at the standard width class (compact 16, large 22). */
    const val GUTTER_DP = 20f
    const val GUTTER_COMPACT_DP = 16f
    const val GUTTER_LARGE_DP = 22f

    /** Gap between grid rows. */
    const val ROW_GAP_DP = 12f

    /** Gap between sections on settings, studio and drawer. */
    const val SECTION_GAP_DP = 24f

    /** Gap between the dock and the grid above it. */
    const val DOCK_GAP_DP = 40f

    // ------------------------------------------------------------------- motion

    /**
     * Motion tokens. Durations are milliseconds; easings are cubic-bezier control
     * points as (x1, y1, x2, y2). Every transition in the specification uses one
     * of these four durations and one of these three curves.
     */
    object Motion {
        const val DUR_1 = 120
        const val DUR_2 = 200
        const val DUR_3 = 320
        const val DUR_4 = 480
        const val PRESS = 90
        const val LONG_PRESS = 500

        /** standard  cubic-bezier(.22,.61,.36,1) */
        val STANDARD = floatArrayOf(0.22f, 0.61f, 0.36f, 1f)

        /** emphasis  cubic-bezier(.16,.84,.28,1) */
        val EMPHASIS = floatArrayOf(0.16f, 0.84f, 0.28f, 1f)

        /** exit      cubic-bezier(.4,0,1,1) */
        val EXIT = floatArrayOf(0.4f, 0f, 1f, 1f)

        /** Reorder drag only. */
        const val DRAG_STIFFNESS = 480f
        const val DRAG_DAMPING = 34f
    }

    // --------------------------------------------------------------------- type

    /**
     * The shared type scale (sp). Themes differ in family, weight and tracking
     * through [TypeStyle]; the ladder itself never changes between themes.
     */
    val TYPE_SCALE = TokenTypeScale(
        clock = 66f,
        screenTitle = 21f,
        sectionHeading = 14f,
        widgetHeading = 12.5f,
        primary = 13f,
        appLabel = 11f,
        supporting = 10.5f,
        numeric = 10f,
        button = 13.5f,
        kicker = 9.5f
    )

    // ------------------------------------------------------------------- themes

    /** Signature — refined, timeless, expensive. Midnight ramp, indigo-violet. */
    val SIGNATURE = ThemeTokens(
        key = "signature",
        version = VERSION,
        colors = TokenColors(
            background0 = 0xFF06070B.toInt(),
            background1 = 0xFF0A0C12.toInt(),
            background2 = 0xFF0F1219.toInt(),
            surface1 = translucent(0xFFFFFFFF.toInt(), 0.045f),
            surface2 = translucent(0xFFFFFFFF.toInt(), 0.072f),
            surface3 = translucent(0xFFFFFFFF.toInt(), 0.105f),
            surfaceSolid = 0xFF14171F.toInt(),
            stroke1 = translucent(0xFFFFFFFF.toInt(), 0.085f),
            stroke2 = translucent(0xFFFFFFFF.toInt(), 0.17f),
            text1 = 0xFFF2F4F9.toInt(),
            text2 = 0xFFA8AEBC.toInt(),
            text3 = 0xFF717886.toInt(),
            accent = 0xFF7C86FF.toInt(),
            accent2 = 0xFFA78BFA.toInt(),
            accentSoft = translucent(0xFF7C86FF.toInt(), 0.15f),
            ok = 0xFF4FD1A5.toInt(),
            warn = 0xFFF0C25C.toInt(),
            danger = 0xFFFF7070.toInt()
        ),
        shape = TokenShape(
            extraLarge = 34f,
            large = 26f,
            medium = 16f,
            small = 10f,
            extraSmall = 6f,
            dockRadius = 34f,
            tileRadius = 24f,
            tileSize = 58f
        ),
        material = TokenMaterial(
            blurDp = 26f,
            saturation = 1.35f,
            shadow2 = "0 12px 32px -12px rgba(0,0,0,.75), inset 0 1px 0 rgba(255,255,255,.07)",
            shadow3 = "0 28px 60px -24px rgba(0,0,0,.85), inset 0 1px 0 rgba(255,255,255,.09)"
        ),
        type = TYPE_SCALE,
        clockSp = 64f,
        appLabelSp = 10.5f,
    )

    /** Atmosphere — immersive, cinematic, adaptive. Wallpaper-driven, aurora mint. */
    val ATMOSPHERE = ThemeTokens(
        key = "atmosphere",
        version = VERSION,
        colors = TokenColors(
            background0 = 0xFF04131A.toInt(),
            background1 = 0xFF061A22.toInt(),
            background2 = 0xFF0A2430.toInt(),
            surface1 = translucent(0xFFB4F0FF.toInt(), 0.055f),
            surface2 = translucent(0xFFB4F0FF.toInt(), 0.09f),
            surface3 = translucent(0xFFB4F0FF.toInt(), 0.13f),
            surfaceSolid = 0xFF0B2029.toInt(),
            stroke1 = translucent(0xFFA0EBFF.toInt(), 0.13f),
            stroke2 = translucent(0xFFA0EBFF.toInt(), 0.26f),
            text1 = 0xFFEAF7F6.toInt(),
            text2 = 0xFFA3C0C6.toInt(),
            text3 = 0xFF6E8C94.toInt(),
            accent = 0xFF46E0B0.toInt(),
            accent2 = 0xFF7FB6FF.toInt(),
            accentSoft = translucent(0xFF46E0B0.toInt(), 0.16f),
            ok = 0xFF46E0B0.toInt(),
            warn = 0xFFFFC46B.toInt(),
            danger = 0xFFFF7A85.toInt()
        ),
        shape = TokenShape(
            extraLarge = 38f,
            large = 30f,
            medium = 20f,
            small = 12f,
            extraSmall = 8f,
            dockRadius = 36f,
            tileRadius = 26f,
            tileSize = 58f
        ),
        material = TokenMaterial(
            blurDp = 34f,
            saturation = 1.65f,
            shadow2 = "0 18px 44px -18px rgba(0,20,30,.85), inset 0 1px 0 rgba(190,245,255,.18)",
            shadow3 = "0 34px 70px -26px rgba(0,0,0,.8), inset 0 1px 0 rgba(190,245,255,.22)"
        ),
        type = TYPE_SCALE,
        clockSp = 58f,
        appLabelSp = 11f,
    )

    /** Monolith — architectural, bold, exact. Stone neutrals, vermilion, no blur. */
    val MONOLITH = ThemeTokens(
        key = "monolith",
        version = VERSION,
        colors = TokenColors(
            background0 = 0xFF0B0B0A.toInt(),
            background1 = 0xFF111110.toInt(),
            background2 = 0xFF171715.toInt(),
            surface1 = translucent(0xFFF5F3EE.toInt(), 0.045f),
            surface2 = translucent(0xFFF5F3EE.toInt(), 0.075f),
            surface3 = translucent(0xFFF5F3EE.toInt(), 0.11f),
            surfaceSolid = 0xFF1A1A17.toInt(),
            stroke1 = translucent(0xFFF5F3EE.toInt(), 0.13f),
            stroke2 = translucent(0xFFF5F3EE.toInt(), 0.24f),
            text1 = 0xFFF4F2EC.toInt(),
            text2 = 0xFFA5A29A.toInt(),
            text3 = 0xFF726F68.toInt(),
            accent = 0xFFFF5A2B.toInt(),
            accent2 = 0xFFFF5A2B.toInt(),
            accentSoft = translucent(0xFFFF5A2B.toInt(), 0.14f),
            ok = 0xFF9BD16A.toInt(),
            warn = 0xFFF2C14E.toInt(),
            danger = 0xFFFF5A2B.toInt()
        ),
        shape = TokenShape(
            extraLarge = 8f,
            large = 4f,
            medium = 2f,
            small = 2f,
            extraSmall = 2f,
            dockRadius = 4f,
            tileRadius = 10f,
            tileSize = 58f
        ),
        material = TokenMaterial(
            // Blur is a material with a theme value; Monolith's value is zero.
            blurDp = 0f,
            saturation = 1f,
            shadow2 = "0 2px 0 rgba(0,0,0,.5), inset 0 1px 0 rgba(255,255,255,.06)",
            shadow3 = "0 24px 60px -28px rgba(0,0,0,.9)"
        ),
        type = TYPE_SCALE,
        clockSp = 84f,
        appLabelSp = 9f,
    )

    val ALL: List<ThemeTokens> = listOf(SIGNATURE, ATMOSPHERE, MONOLITH)

    fun forKey(key: String?): ThemeTokens? = ALL.firstOrNull { it.key == key }

    /**
     * Authored translucent role as a packed ARGB colour.
     *
     * Kept as a function rather than a literal so the exported JSON
     * (`rgba(255,255,255,.045)`) and this file round-trip through exactly the
     * same rounding.
     */
    private fun translucent(rgb: Int, alpha: Float): Int = ColorMath.withAlpha(rgb, alpha)
}

/** Colour roles authored per theme. Names mirror the exported token JSON. */
data class TokenColors(
    val background0: Int,
    val background1: Int,
    val background2: Int,
    /** Rows and tiles: translucent over the wall on purpose. */
    val surface1: Int,
    /** Controls. */
    val surface2: Int,
    /** Active/selected. */
    val surface3: Int,
    /** Opaque fallback where blur is unavailable or disabled. */
    val surfaceSolid: Int,
    val stroke1: Int,
    val stroke2: Int,
    val text1: Int,
    val text2: Int,
    val text3: Int,
    val accent: Int,
    val accent2: Int,
    val accentSoft: Int,
    val ok: Int,
    val warn: Int,
    val danger: Int
)

/** Corner geometry, in dp. Radii never mix inside a theme. */
data class TokenShape(
    val extraLarge: Float,
    val large: Float,
    val medium: Float,
    val small: Float,
    val extraSmall: Float,
    val dockRadius: Float,
    val tileRadius: Float,
    val tileSize: Float
)

/**
 * Surface material. [blurDp] is 0 for a theme that does not use blur at all;
 * [saturation] is the multiplier applied to the blurred backdrop (1 = none).
 * Shadow tokens are kept in their authored CSS form as documentation for the
 * Compose/View elevation mapping.
 */
data class TokenMaterial(
    val blurDp: Float,
    val saturation: Float,
    val shadow2: String,
    val shadow3: String
) {
    val usesBlur: Boolean get() = blurDp > 0f
}

/** The shared ten-role type ladder, in sp. */
data class TokenTypeScale(
    val clock: Float,
    val screenTitle: Float,
    val sectionHeading: Float,
    val widgetHeading: Float,
    val primary: Float,
    val appLabel: Float,
    val supporting: Float,
    val numeric: Float,
    val button: Float,
    val kicker: Float
)

/**
 * A complete authored theme: colour, shape, material and type.
 *
 * Clock weight and tracking are deliberately **not** duplicated here: they live in
 * the theme's [TypeStyle] voice (`precision`, `ambient`, `monograph`), which already
 * carried them before v3.2. Only the per-theme clock *size* is a token, because the
 * specification resizes that one role per identity (64 / 58 / 84).
 */
data class ThemeTokens(
    val key: String,
    val version: String,
    val colors: TokenColors,
    val shape: TokenShape,
    val material: TokenMaterial,
    val type: TokenTypeScale,
    /** Clock size for this theme (64 / 58 / 84 in v3.2). */
    val clockSp: Float,
    /** App-label size for this theme (10.5 / 11 / 9 in v3.2). */
    val appLabelSp: Float
)
