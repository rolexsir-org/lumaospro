package com.lumaos.pro.launcher.ui.theme

/**
 * LumaOS Pro's typography system.
 *
 * One type scale is shared by every screen and theme (see [LumaTypeScale]); a
 * theme changes the *voice* used to speak it - family, weight and tracking -
 * never the hierarchy. That is what makes Aurora, Zen and Void feel distinct
 * while staying recognisably the same product.
 *
 * The bundled families are Inter (Aurora, Zen and all body copy) and Space
 * Grotesk (Void display and headings), both SIL Open Font License 1.1. Their
 * licence files ship in `assets/licenses` and are readable in Settings → About.
 * Every face has a platform fallback, so text remains legible if an asset is
 * ever removed from a build.
 *
 * Plain Kotlin with no Android imports: the scale and its contrast/ordering
 * guarantees are unit tested on the JVM.
 */

/** A typeface family with a platform fallback of the same temperament. */
enum class FontFamilyId(val platformFallback: String) {
    INTER("sans-serif"),
    SPACE_GROTESK("sans-serif"),
    SYSTEM_SANS("sans-serif"),
    SYSTEM_SERIF("serif"),
    SYSTEM_MONO("monospace");

    /** Human-readable name for the design reference and Studio. */
    val displayName: String
        get() = when (this) {
            INTER -> "Inter"
            SPACE_GROTESK -> "Space Grotesk"
            SYSTEM_SANS -> "System Sans"
            SYSTEM_SERIF -> "System Serif"
            SYSTEM_MONO -> "System Mono"
        }

    /** True when the family ships with the app rather than coming from the platform. */
    val bundled: Boolean get() = this == INTER || this == SPACE_GROTESK
}

/** The five weights LumaOS Pro uses. Bold beyond this is deliberately unavailable. */
enum class FontWeightToken(val weight: Int, val displayName: String) {
    LIGHT(300, "Light"),
    REGULAR(400, "Regular"),
    MEDIUM(500, "Medium"),
    SEMIBOLD(600, "Semibold");

    companion object {
        fun nearest(weight: Int): FontWeightToken =
            entries.minByOrNull { kotlin.math.abs(it.weight - weight) } ?: REGULAR
    }
}

/**
 * One face: a family plus a weight. [assetName] is the font resource stem for
 * bundled families; platform families resolve through the typeface fallback.
 */
data class FontFace(val family: FontFamilyId, val weight: FontWeightToken) {
    val assetName: String
        get() = when (family) {
            FontFamilyId.INTER -> "inter_${weight.name.lowercase()}"
            FontFamilyId.SPACE_GROTESK -> "space_grotesk_${weight.name.lowercase()}"
            else -> ""
        }
}

/**
 * Theme-specific typographic voice.
 *
 * Aurora (PRECISION): refined Inter, light display numerals, semibold headings,
 * open-tracked instrument labels.
 * Zen (SERENE): calm Inter, medium headings, tighter metadata and a quieter
 * clock with generous breathing room.
 * Void (GROTESK): geometric Space Grotesk for display and headings, Inter for
 * body copy so long text stays readable, crisp uppercase readouts.
 *
 * SERIF and MONO are retained as explicit fallback voices for existing user
 * choices and old backups; no signature theme uses them. Their storage keys are
 * stable, so no saved preference is reset.
 */
enum class TypeStyle(
    val storageKey: String,
    /** Typeface, size and tracking for the home clock. */
    val clock: FontFace,
    val clockSizeSp: Float,
    val clockTracking: Float,
    /** Titles: drawer, folders, dialogs and section headings. */
    val title: FontFace,
    val titleTracking: Float,
    /** Body copy and application labels. */
    val body: FontFace,
    /** Compact labels and controls. */
    val label: FontFace,
    val labelTracking: Float,
    /** Small instrument readouts (date, profile, captions). */
    val readout: FontFace,
    val readoutCaps: Boolean,
    val readoutTracking: Float
) {
    PRECISION(
        storageKey = "precision",
        clock = FontFace(FontFamilyId.INTER, FontWeightToken.LIGHT),
        clockSizeSp = 66f,
        clockTracking = -0.035f,
        title = FontFace(FontFamilyId.INTER, FontWeightToken.SEMIBOLD),
        titleTracking = -0.02f,
        body = FontFace(FontFamilyId.INTER, FontWeightToken.REGULAR),
        label = FontFace(FontFamilyId.INTER, FontWeightToken.MEDIUM),
        labelTracking = 0.005f,
        readout = FontFace(FontFamilyId.INTER, FontWeightToken.MEDIUM),
        readoutCaps = true,
        readoutTracking = 0.14f
    ),
    SERENE(
        storageKey = "serene",
        clock = FontFace(FontFamilyId.INTER, FontWeightToken.LIGHT),
        clockSizeSp = 58f,
        clockTracking = -0.015f,
        title = FontFace(FontFamilyId.INTER, FontWeightToken.MEDIUM),
        titleTracking = -0.008f,
        body = FontFace(FontFamilyId.INTER, FontWeightToken.REGULAR),
        label = FontFace(FontFamilyId.INTER, FontWeightToken.MEDIUM),
        labelTracking = 0.01f,
        readout = FontFace(FontFamilyId.INTER, FontWeightToken.MEDIUM),
        readoutCaps = false,
        readoutTracking = 0.035f
    ),
    GROTESK(
        storageKey = "grotesk",
        clock = FontFace(FontFamilyId.SPACE_GROTESK, FontWeightToken.SEMIBOLD),
        clockSizeSp = 68f,
        clockTracking = -0.025f,
        title = FontFace(FontFamilyId.SPACE_GROTESK, FontWeightToken.SEMIBOLD),
        titleTracking = -0.02f,
        body = FontFace(FontFamilyId.INTER, FontWeightToken.REGULAR),
        label = FontFace(FontFamilyId.INTER, FontWeightToken.MEDIUM),
        labelTracking = 0.008f,
        readout = FontFace(FontFamilyId.SPACE_GROTESK, FontWeightToken.MEDIUM),
        readoutCaps = true,
        readoutTracking = 0.16f
    ),
    SERIF(
        storageKey = "serif",
        clock = FontFace(FontFamilyId.SYSTEM_SERIF, FontWeightToken.LIGHT),
        clockSizeSp = 62f,
        clockTracking = -0.03f,
        title = FontFace(FontFamilyId.SYSTEM_SERIF, FontWeightToken.MEDIUM),
        titleTracking = -0.01f,
        body = FontFace(FontFamilyId.SYSTEM_SANS, FontWeightToken.REGULAR),
        label = FontFace(FontFamilyId.SYSTEM_SANS, FontWeightToken.MEDIUM),
        labelTracking = 0.005f,
        readout = FontFace(FontFamilyId.SYSTEM_SANS, FontWeightToken.MEDIUM),
        readoutCaps = false,
        readoutTracking = 0.02f
    ),
    MONO(
        storageKey = "mono",
        clock = FontFace(FontFamilyId.SYSTEM_MONO, FontWeightToken.REGULAR),
        clockSizeSp = 60f,
        clockTracking = -0.04f,
        title = FontFace(FontFamilyId.SYSTEM_MONO, FontWeightToken.MEDIUM),
        titleTracking = 0f,
        body = FontFace(FontFamilyId.INTER, FontWeightToken.REGULAR),
        label = FontFace(FontFamilyId.INTER, FontWeightToken.MEDIUM),
        labelTracking = 0.01f,
        readout = FontFace(FontFamilyId.SYSTEM_MONO, FontWeightToken.REGULAR),
        readoutCaps = true,
        readoutTracking = 0.12f
    );

    /** The obvious description used in the Studio and design reference. */
    val character: String
        get() = when (this) {
            PRECISION -> "Quiet luxury"
            SERENE -> "Modern serenity"
            GROTESK -> "Futuristic expression"
            SERIF -> "Editorial fallback"
            MONO -> "Technical fallback"
        }

    companion object {
        fun fromStorageKey(raw: String?): TypeStyle? = entries.firstOrNull { it.storageKey == raw }

        /** Voices offered as first-class choices; fallbacks stay persisted but are not promoted. */
        val SIGNATURE: List<TypeStyle> = listOf(PRECISION, SERENE, GROTESK)
    }
}

/**
 * The single type scale. Sizes are in sp so Android's font-scale setting scales
 * every role, including inside Material components. Line heights are given as
 * multipliers and converted to fixed sp by the Compose and View bindings, which
 * keeps vertical rhythm identical on both.
 */
object LumaTypeScale {

    // Display - the home clock and large numerals.
    const val displayLargeSp = 72f
    const val displayMediumSp = 64f
    const val displaySmallSp = 52f

    // Headings - screen titles and section leads.
    const val headlineLargeSp = 30f
    const val headlineMediumSp = 26f
    const val headlineSmallSp = 23f

    // Titles - dialogs, sheets, folders, row titles.
    const val titleLargeSp = 21f
    const val titleMediumSp = 18f
    const val titleSmallSp = 15.5f

    // Body - settings copy, descriptions, menus.
    const val bodyLargeSp = 16f
    const val bodyMediumSp = 14f
    const val bodySmallSp = 12.5f

    // Labels and captions - controls, readouts, metadata.
    const val labelLargeSp = 13.5f
    const val labelMediumSp = 12f
    const val labelSmallSp = 11f

    /** Application labels on the home screen and in the drawer. */
    const val appLabelSp = 12f

    const val displayLineHeight = 1.02f
    const val headingLineHeight = 1.18f
    const val titleLineHeight = 1.24f
    const val bodyLineHeight = 1.5f
    const val compactBodyLineHeight = 1.42f
    const val labelLineHeight = 1.3f

    /** Line height in sp for a size and multiplier. */
    fun lineHeight(sizeSp: Float, multiplier: Float): Float = sizeSp * multiplier

    /** Baselines may not be closer than the text size; guards hand-edited values. */
    fun safeLineHeight(sizeSp: Float, multiplier: Float): Float =
        lineHeight(sizeSp, multiplier).coerceAtLeast(sizeSp * 1.0f)
}

/** Letter spacing presets for roles that are not theme-voiced. */
object LumaTracking {
    const val display = -0.03f
    const val heading = -0.018f
    const val title = -0.012f
    const val body = 0f
    const val label = 0.01f
    const val overline = 0.14f
}
