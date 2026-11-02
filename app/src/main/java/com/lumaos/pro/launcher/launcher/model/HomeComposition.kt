package com.lumaos.pro.launcher.launcher.model

import com.lumaos.pro.launcher.ui.theme.LumaTokens

/**
 * Home-screen composition presets.
 *
 * A preset decides spatial scale, grid density and dock behaviour independently
 * of the theme. Luma Glass has one clock/date focal point in every preset; the
 * old chronometer dial is no longer drawn. Any preset works with any theme and
 * each one is complete without a widget.
 */
enum class ClockStyle {
    /** Legacy storage value: mapped to the refined centred clock. */
    DIAL,
    /** Centred time with date beneath. */
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
    val dockLabels: Boolean,
    /**
     * Whether the date line sits under the clock.
     *
     * Taken from [ClockStyle]'s own contract: `HERO` is "centred time with date
     * beneath", `COMPACT` is "one compact line, maximising room for the grid".
     */
    val showDate: Boolean,
    /**
     * Whether the context/readout line is shown under the header.
     *
     * Only the presets whose own [description] leaves room for it: HORIZON is
     * "a spacious centred clock ... and open wallpaper". GRID exists to
     * maximise grid room and ZEN states "everything else lives in the drawer",
     * so both keep the chrome minimal. INSTRUMENT is the balanced default row
     * count with a dock and likewise keeps the line off.
     *
     * Not persisted: a preset is stored by [storageKey] alone, so adding these
     * fields changes no stored value and needs no migration.
     */
    val showContextLine: Boolean
) {
    INSTRUMENT(
        "instrument", "Balanced",
        "Centred clock, four app rows and a floating glass dock.",
        ClockStyle.HERO, showGrid = true, defaultRows = 4, dock = DockStyle.FLOAT, dockLabels = false,
        showDate = true, showContextLine = false
    ),
    HORIZON(
        "horizon", "Horizon",
        "A spacious centred clock, three rows and open wallpaper.",
        ClockStyle.HERO, showGrid = true, defaultRows = 3, dock = DockStyle.FLOAT, dockLabels = false,
        showDate = true, showContextLine = true
    ),
    GRID(
        "grid", "Grid",
        "Compact clock and a dense, aligned grid for many apps.",
        ClockStyle.COMPACT, showGrid = true, defaultRows = 6, dock = DockStyle.FLOAT, dockLabels = false,
        showDate = false, showContextLine = false
    ),
    ZEN(
        "zen", "Zen",
        "Clock, date and dock only. Everything else lives in the drawer.",
        ClockStyle.HERO, showGrid = false, defaultRows = 4, dock = DockStyle.FLOAT, dockLabels = false,
        showDate = true, showContextLine = false
    );

    companion object {
        val DEFAULT = HORIZON
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
        widthDp >= LumaTokens.gridWideBreakpointDp -> LumaTokens.gridColumnsWide
        else -> LumaTokens.gridColumnsNarrow
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
