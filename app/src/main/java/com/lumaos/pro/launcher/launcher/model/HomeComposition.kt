package com.lumaos.pro.launcher.launcher.model

/**
 * Home-screen composition presets.
 *
 * A preset decides the spatial arrangement - how the chronometer is drawn,
 * whether the icon grid is shown, how many rows it starts with and how the
 * dock sits - independently of the theme. Any preset works with any theme,
 * and each one is complete without a single widget.
 */
enum class ClockStyle {
    /** Chronometer dial (day-progress ring, battery arc, alarm marker) beside the time. */
    DIAL,
    /** Large centred time with date and readouts beneath. */
    HERO,
    /** One compact line, maximising room for the grid. */
    COMPACT
}

enum class DockStyle(val storageKey: String) {
    /** Full-width plate with a fine rim highlight. */
    BEZEL("bezel"),
    /** Inset floating capsule. */
    FLOAT("float"),
    /** No plate; icons sit on the backdrop. */
    MINIMAL("minimal");

    companion object {
        fun fromStorageKey(raw: String?): DockStyle? = entries.firstOrNull { it.storageKey == raw }
    }
}

enum class HomePreset(
    val storageKey: String,
    val displayName: String,
    val description: String,
    val clock: ClockStyle,
    val showGrid: Boolean,
    val defaultRows: Int,
    val dock: DockStyle,
    val dockLabels: Boolean
) {
    INSTRUMENT(
        "instrument", "Instrument",
        "Chronometer dial with live readouts above a calm grid.",
        ClockStyle.DIAL, showGrid = true, defaultRows = 4, dock = DockStyle.BEZEL, dockLabels = false
    ),
    HORIZON(
        "horizon", "Horizon",
        "A large centred time over open space; fewer, bigger rows.",
        ClockStyle.HERO, showGrid = true, defaultRows = 3, dock = DockStyle.FLOAT, dockLabels = false
    ),
    GRID(
        "grid", "Grid",
        "Compact time line and a dense grid for many apps.",
        ClockStyle.COMPACT, showGrid = true, defaultRows = 6, dock = DockStyle.BEZEL, dockLabels = false
    ),
    ZEN(
        "zen", "Zen",
        "Time, readouts and dock only. Everything else lives in the drawer.",
        ClockStyle.HERO, showGrid = false, defaultRows = 4, dock = DockStyle.MINIMAL, dockLabels = true
    );

    companion object {
        val DEFAULT = INSTRUMENT
        fun fromStorageKey(raw: String?): HomePreset = entries.firstOrNull { it.storageKey == raw } ?: DEFAULT
    }
}

/**
 * Responsive grid rules. Pure so they are unit tested; the activity feeds in
 * the real window size.
 */
object GridRules {

    /** Default column count for a window [widthDp] wide (phones 4, large phones 5, tablets/unfolded 6). */
    fun defaultColumns(widthDp: Int): Int = when {
        widthDp >= 840 -> 7
        widthDp >= 600 -> 6
        widthDp >= 412 -> 5
        else -> 4
    }

    /**
     * User columns are honoured but clamped so a cell never drops below the
     * 48dp minimum touch target plus gutters.
     */
    fun effectiveColumns(userColumns: Int, widthDp: Int, minCellDp: Int = 64): Int {
        val max = (widthDp / minCellDp).coerceAtLeast(3)
        return userColumns.coerceIn(3, max)
    }

    /** Landscape and wide windows place the chronometer beside the grid instead of above it. */
    fun useSideBySide(widthDp: Int, heightDp: Int): Boolean = widthDp > heightDp && widthDp >= 560
}
