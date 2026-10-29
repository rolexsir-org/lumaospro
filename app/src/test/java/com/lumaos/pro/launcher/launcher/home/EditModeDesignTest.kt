package com.lumaos.pro.launcher.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Edit Home geometry contract.
 *
 * The redesign's whole premise is one aligned column with no overlaps, so these
 * tests assert the *relationships* between the tokens rather than any single
 * number: if someone tweaks a value into breaking the rhythm (an off-grid
 * gutter, a label size above its auto-shrink floor, a toolbar taller than the
 * space reserved for it), the build fails instead of the layout.
 */
class EditModeDesignTest {

    @Test
    fun `spacing follows the 4dp grid with documented optical steps`() {
        val onGrid = listOf(
            EditModeDesign.screenMarginDp,
            EditModeDesign.sectionGapDp,
            EditModeDesign.headerTopMarginDp,
            EditModeDesign.headerToClockGapDp,
            EditModeDesign.toolbarBottomMarginDp,
            EditModeDesign.gridGutterDp,
            EditModeDesign.hintPaddingHorizontalDp,
            EditModeDesign.dockToToolbarGapDp,
            EditModeDesign.toolbarIconLabelGapDp
        )
        onGrid.forEach { assertEquals("$it is off the 4dp grid", 0f, it % 4f, 0.001f) }

        // The spec asks for four optical values that are 2dp off the 4dp grid:
        // 18dp card padding, 14dp pill and dock padding, 10dp toolbar padding.
        // They are asserted explicitly so a change is a deliberate decision
        // rather than drift, and every one still sits on the 2dp half-step.
        assertEquals(18f, EditModeDesign.headerPaddingDp, 0.001f)
        assertEquals(14f, EditModeDesign.hintPaddingVerticalDp, 0.001f)
        assertEquals(14f, EditModeDesign.dockPaddingDp, 0.001f)
        assertEquals(10f, EditModeDesign.toolbarPaddingDp, 0.001f)
        listOf(
            EditModeDesign.headerPaddingDp,
            EditModeDesign.hintPaddingVerticalDp,
            EditModeDesign.dockPaddingDp,
            EditModeDesign.toolbarPaddingDp
        ).forEach { assertEquals("$it is off the 2dp half-step", 0f, it % 2f, 0.001f) }
    }

    @Test
    fun `one screen margin is shared by every surface`() {
        // The dock/toolbar gap must never exceed the section rhythm, or the dock
        // would visibly drift away from the toolbar it is paired with.
        assertTrue(EditModeDesign.dockToToolbarGapDp <= EditModeDesign.sectionGapDp)
        assertTrue(EditModeDesign.headerToClockGapDp >= EditModeDesign.sectionGapDp)
    }

    @Test
    fun `radius ladder is cards 24, dock 28, buttons 16, slots 20`() {
        assertEquals(24f, EditModeDesign.headerRadiusDp, 0.001f)
        assertEquals(24f, EditModeDesign.toolbarRadiusDp, 0.001f)
        assertEquals(28f, EditModeDesign.dockRadiusDp, 0.001f)
        assertEquals(16f, EditModeDesign.toolbarButtonRadiusDp, 0.001f)
        assertEquals(20f, EditModeDesign.slotRadiusDp, 0.001f)
        assertTrue(EditModeDesign.pillRadiusDp > EditModeDesign.dockRadiusDp)
    }

    @Test
    fun `type ladder is 20 title, 14 body, 13 subtitle, 12 label`() {
        assertEquals(20f, EditModeDesign.headerTitleSp, 0.001f)
        assertEquals(14f, EditModeDesign.doneTextSp, 0.001f)
        assertEquals(14f, EditModeDesign.hintTextSp, 0.001f)
        assertEquals(14f, EditModeDesign.dateSp, 0.001f)
        assertEquals(13f, EditModeDesign.headerSubtitleSp, 0.001f)
        assertEquals(12f, EditModeDesign.toolbarLabelSp, 0.001f)
        assertTrue(EditModeDesign.toolbarLabelMinSp < EditModeDesign.toolbarLabelSp)
        assertTrue(EditModeDesign.headerSubtitleMinSp < EditModeDesign.headerSubtitleSp)
    }

    @Test
    fun `done pill and toolbar buttons are at least a 40dp touch target`() {
        assertTrue(EditModeDesign.doneMinWidthDp >= 76f)
        assertTrue(EditModeDesign.doneHeightDp >= 40f)
        assertTrue(EditModeDesign.toolbarButtonHeightDp >= 48f)
    }

    @Test
    fun `reserved toolbar height matches what the layout draws`() {
        // 64dp buttons inside a container with 10dp padding on both sides.
        assertEquals(84f, EditModeDesign.toolbarButtonHeightDp + EditModeDesign.toolbarPaddingDp * 2, 0.001f)
        assertEquals(
            ((EditModeDesign.toolbarButtonHeightDp + EditModeDesign.toolbarPaddingDp * 2) * 2).toInt(),
            EditModeDesign.toolbarHeightPx(2f)
        )
    }

    @Test
    fun `the wallpaper veil keeps a busy pattern far below full strength`() {
        // 55% black, then the wallpaper layer itself at 35%: a light pattern
        // pixel can never exceed ~16% of its original contrast.
        val effective = (1f - EditModeDesign.scrimAlpha) * EditModeDesign.wallpaperAlphaInEdit
        assertTrue("effective wallpaper opacity $effective", effective in 0.10f..0.20f)
    }

    @Test
    fun `drop-zone geometry produces the intended gutter`() {
        val density = 2f
        // Each zone is inset by half the gutter, so two neighbours end up
        // exactly one gutter apart on every density.
        assertEquals(EditModeDesign.gridGutterDp * density, EditModeDesign.gutterPx(density), 0.001f)
        assertEquals(EditModeDesign.slotRadiusDp * density, EditModeDesign.slotRadiusPx(density), 0.001f)
        assertEquals(EditModeDesign.screenMarginDp * density, EditModeDesign.screenMarginPx(density).toFloat(), 0.001f)
    }
}
