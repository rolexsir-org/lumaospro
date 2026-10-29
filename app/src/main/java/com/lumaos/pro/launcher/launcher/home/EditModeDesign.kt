package com.lumaos.pro.launcher.launcher.home

/**
 * Home editing — design tokens.
 *
 * Editing is a *contextual* state, not a second screen. There is no banner, no
 * instructional copy and no persistent toolbar: the workspace keeps its resting
 * geometry, and the only chrome is one floating capsule that appears while
 * editing and leaves with it. Every number that chrome uses lives here (and is
 * mirrored 1:1 in `res/values/dimens.xml` as `luma_edit_*`).
 *
 * Rules this file encodes:
 *  - **Nothing reserves space.** Editing never changes the desktop's insets,
 *    the grid's padding, the dock's margins or the clock's position, so the
 *    workspace cannot jump when a session starts or ends.
 *  - **The capsule borrows the search pill's slot.** It is exactly one search
 *    row tall (48dp) and fully rounded, so the two surfaces share one silhouette
 *    and the transition reads as a morph rather than as a new panel.
 *  - **Icon-only, no labels, no paragraphs.** Five features plus Done, each a
 *    44dp target inside the 48dp capsule.
 *  - **Affordances only while they are actionable.** Drop zones appear while an
 *    item is actually being dragged; the remove target only while a drag is in
 *    flight, and it saturates only when the release would remove.
 *  - **Restraint.** Hairlines, one accent, spring motion with no bounce.
 */
object EditModeDesign {

    // ── Screen geometry ────────────────────────────────────────────────────

    /**
     * Left/right margin the drop zones align to: the desktop's own
     * `LumaTokens.screenMarginHorizontal`. The activity overwrites the guide's
     * margins with the resolved value; this constant keeps the XML fallback and
     * the tests on the same number.
     */
    const val screenMarginDp: Float = 24f

    // ── Contextual action capsule ──────────────────────────────────────────

    /** Capsule height. Equal to the search pill row, so the slot never re-measures. */
    const val actionBarHeightDp: Float = 48f

    /** Fully rounded: half the capsule height. */
    const val actionBarRadiusDp: Float = 24f

    /**
     * Horizontal inset only. The capsule keeps its full height for the targets,
     * so every one of them is a 48dp tap target even though the pill is 48dp.
     */
    const val actionBarInsetDp: Float = 2f

    /** One action target: full capsule height, equally weighted across the row. */
    const val actionButtonSizeDp: Float = 48f

    /** Glyph size inside an action target. */
    const val actionIconDp: Float = 22f

    /**
     * Corner radius of an action's press feedback: a squircle inside the pill,
     * not a second pill, so a press never looks like a nested capsule.
     */
    const val actionButtonRadiusDp: Float = 16f

    /** Pressed/selected fill: white at 10%, the only state fill in the capsule. */
    const val actionPressedFillAlpha: Float = 0.10f

    /** Hairline that separates Done from the five feature actions. */
    const val actionDividerAlpha: Float = 0.14f
    const val actionDividerWidthDp: Float = 1f
    const val actionDividerHeightDp: Float = 20f

    /** How far the capsule rises while fading in. */
    const val actionBarRiseDp: Float = 10f

    // ── Remove target ──────────────────────────────────────────────────────

    /** Circular glass target shown while an item is being dragged. */
    const val removeTargetSizeDp: Float = 52f
    const val removeTargetIconDp: Float = 22f
    const val removeTargetTopMarginDp: Float = 14f
    /** Quiet until the drop would actually remove. */
    const val removeTargetRestAlpha: Float = 0.45f
    /** Slight swell when the release would remove. */
    const val removeTargetArmedScale: Float = 1.06f
    /** Defocus of the dragged item while the remove target is armed. */
    const val draggedAlphaWhenRemoving: Float = 0.55f

    // ── Clock / date ───────────────────────────────────────────────────────

    /** Date line in the desktop header; body size per the type ladder. */
    const val dateSp: Float = 14f
    const val dateAlpha: Float = 0.80f

    // ── Home grid drop zones (shown only during a drag) ────────────────────

    /** Gutter between two drop zones (6dp per side). */
    const val gridGutterDp: Float = 12f
    const val slotRadiusDp: Float = 20f
    const val slotBorderDp: Float = 1f
    /** Dashed outline: white at 12%, quieter than the icons it frames. */
    const val slotBorderAlpha: Float = 0.12f
    /** Zone fill: white at 2%. */
    const val slotFillAlpha: Float = 0.02f
    /** Dash rhythm of the drop-zone outline: on / off. */
    const val slotDashOnDp: Float = 5f
    const val slotDashOffDp: Float = 4f

    // ── Empty-page hint pill ───────────────────────────────────────────────

    const val hintTextSp: Float = 14f
    const val hintTextAlpha: Float = 0.85f
    const val hintPaddingVerticalDp: Float = 14f
    const val hintPaddingHorizontalDp: Float = 20f
    const val hintElevationDp: Float = 16f

    // ── Dock (unchanged by editing) ────────────────────────────────────────

    const val dockRadiusDp: Float = 28f

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

    /** Capsule height in pixels — always one search row. */
    fun actionBarHeightPx(density: Float): Int = px(density, actionBarHeightDp)

    /** Remove-target diameter in pixels. */
    fun removeTargetSizePx(density: Float): Int = px(density, removeTargetSizeDp)
}
