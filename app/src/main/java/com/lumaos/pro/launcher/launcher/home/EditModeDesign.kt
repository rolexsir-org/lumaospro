package com.lumaos.pro.launcher.launcher.home

/**
 * Edit Home — design tokens.
 *
 * Every number the edit-mode chrome uses lives here (and is mirrored 1:1 in
 * `res/values/dimens.xml` as `luma_edit_*`), so the layout can be re-tuned
 * without hunting through XML, the controller or the placement guide.
 *
 * Rules this file encodes:
 *  - one screen margin (16dp) shared by the header card, the grid, the dock and
 *    the toolbar, so every left/right edge lines up;
 *  - one vertical rhythm (12dp) between the major sections;
 *  - one radius ladder: cards 24dp, dock 28dp, toolbar buttons 16dp, slots 20dp,
 *    pills fully rounded;
 *  - one type hierarchy: 20 titles / 14 body / 13 subtitle / 12 labels;
 *  - the wallpaper is heavily veiled in edit mode so text never fights it.
 */
object EditModeDesign {

    // ── Screen geometry ────────────────────────────────────────────────────

    /** Left/right margin used by every edit-mode surface, grid cells included. */
    const val screenMarginDp: Float = 16f

    /** Vertical gap between the major sections (header → clock → grid → dock → toolbar). */
    const val sectionGapDp: Float = 12f

    /** Space above the header card, under the status bar. */
    const val headerTopMarginDp: Float = 12f

    /** Clear space the clock/date keeps below the header card. Never overlapped. */
    const val headerToClockGapDp: Float = 16f

    /** Safe-area breathing room under the toolbar, above the gesture bar. */
    const val toolbarBottomMarginDp: Float = 16f

    // ── Header card ────────────────────────────────────────────────────────

    const val headerRadiusDp: Float = 24f
    const val headerPaddingDp: Float = 18f
    const val headerElevationDp: Float = 12f
    const val headerTitleSp: Float = 20f
    const val headerSubtitleSp: Float = 13f
    /** Auto-shrink floor for the subtitle, so it never needs a third line. */
    const val headerSubtitleMinSp: Float = 10f
    /** Subtitle line box, so two lines never collide with the title. */
    const val headerSubtitleLineSp: Float = 18f
    /** Secondary text = white at 65% (theme content colour at this alpha). */
    const val secondaryTextAlpha: Float = 0.65f
    const val headerSubtitleMaxLines: Int = 2

    /** "Done" pill: 76 × 40dp, fully rounded, 14sp Medium. */
    const val doneMinWidthDp: Float = 76f
    const val doneHeightDp: Float = 40f
    const val doneTextSp: Float = 14f

    // ── Clock / date ───────────────────────────────────────────────────────

    /** Date line in edit mode; body size per the type ladder. */
    const val dateSp: Float = 14f
    const val dateAlpha: Float = 0.80f

    // ── Home grid drop zones ───────────────────────────────────────────────

    /** Gutter between two drop zones (6dp per side). */
    const val gridGutterDp: Float = 12f
    const val slotRadiusDp: Float = 20f
    const val slotBorderDp: Float = 1f
    const val slotBorderAlpha: Float = 0.15f
    const val slotFillAlpha: Float = 0.03f
    /** Dash rhythm of the drop-zone outline: on / off. */
    const val slotDashOnDp: Float = 5f
    const val slotDashOffDp: Float = 4f

    // ── Hint pill ("Swipe up to explore your apps") ────────────────────────

    const val hintTextSp: Float = 14f
    const val hintTextAlpha: Float = 0.85f
    const val hintPaddingVerticalDp: Float = 14f
    const val hintPaddingHorizontalDp: Float = 20f
    const val hintElevationDp: Float = 16f

    // ── Dock ───────────────────────────────────────────────────────────────

    const val dockRadiusDp: Float = 28f
    const val dockPaddingDp: Float = 14f
    /** Clear gap between the dock and the toolbar below it. */
    const val dockToToolbarGapDp: Float = 12f

    // ── Bottom edit toolbar ────────────────────────────────────────────────

    const val toolbarRadiusDp: Float = 24f
    const val toolbarPaddingDp: Float = 10f
    const val toolbarButtonHeightDp: Float = 64f
    const val toolbarButtonRadiusDp: Float = 16f
    const val toolbarIconDp: Float = 22f
    const val toolbarLabelSp: Float = 12f
    /** Auto-shrink floor, so "Wallpaper" always stays on one line. */
    const val toolbarLabelMinSp: Float = 9f
    const val toolbarIconLabelGapDp: Float = 4f
    /** Selected / pressed state fill: white at 8%. */
    const val toolbarSelectedFillAlpha: Float = 0.08f
    /** Hairline that only appears on the selected button. */
    const val toolbarSelectedBorderAlpha: Float = 0.08f
    const val toolbarButtonGapDp: Float = 2f

    // ── Backdrop treatment ─────────────────────────────────────────────────

    /** 55% black veil above the wallpaper (and only the wallpaper). */
    const val scrimAlpha: Float = 0.55f
    /**
     * Wallpaper layer opacity in edit mode. 0.35 × the 45% that survives the
     * veil lands a busy pattern at ~16% — enough to keep the identity, never
     * enough to compete with text.
     */
    const val wallpaperAlphaInEdit: Float = 0.35f
    /** Slight defocus of the wallpaper while editing (API 31+). */
    const val wallpaperBlurDp: Float = 12f

    // ── Motion ─────────────────────────────────────────────────────────────

    /** Chrome travel while fading in/out: 200–250ms, no bounce. */
    const val chromeTravelDp: Float = 12f
    const val chromeEnterScale: Float = 0.97f

    /** Fully rounded: any radius at or above half the shortest side reads as a pill. */
    const val pillRadiusDp: Float = 999f

    /** Density helper: dp → px. */
    fun px(density: Float, dp: Float): Int = (dp * density).toInt()

    /** Grid gutter between slots, in pixels, one side. */
    fun gutterPx(density: Float): Float = px(density, gridGutterDp).toFloat()

    /** Radius of a drop zone, in pixels. */
    fun slotRadiusPx(density: Float): Float = px(density, slotRadiusDp).toFloat()

    /** Screen margin, in pixels. */
    fun screenMarginPx(density: Float): Int = px(density, screenMarginDp)

    /** Section rhythm, in pixels. */
    fun sectionGapPx(density: Float): Int = px(density, sectionGapDp)

    /** Toolbar visual height: button height + the container's own padding. */
    fun toolbarHeightPx(density: Float): Int =
        px(density, toolbarButtonHeightDp + toolbarPaddingDp * 2f)

    /** Dock visual height for the current icon size, kept on the 4dp grid. */
    fun dockHeightPx(density: Float, iconDp: Float): Int =
        px(density, iconDp + dockPaddingDp * 2f)
}
