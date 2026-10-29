package com.lumaos.pro.launcher.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the home-editing geometry contract.
 *
 * The redesign's premise is that editing adds *no* layout of its own: one
 * floating capsule inside the existing search row, plus affordances that only
 * exist while an item is in the air. These tests assert the relationships
 * between the tokens rather than single numbers, so a change that would make the
 * capsule taller than the row it borrows, or make a target smaller than a
 * finger, fails the build instead of the phone.
 */
class EditModeDesignTest {

    @Test
    fun `the action capsule is exactly one search row tall`() {
        // The capsule and the search pill share one slot, so their heights must
        // match the 48dp row the desktop already lays out. If this drifts the
        // slot re-measures every time a session starts.
        assertEquals(48f, EditModeDesign.actionBarHeightDp, 0.001f)
        assertEquals(48f, EditModeDesign.actionBarHeightPx(1f).toFloat(), 0.001f)
        assertEquals(96f, EditModeDesign.actionBarHeightPx(2f).toFloat(), 0.001f)
    }

    @Test
    fun `the capsule is a pill and its targets fit inside it`() {
        // Fully rounded: half the capsule height.
        assertEquals(EditModeDesign.actionBarHeightDp / 2f, EditModeDesign.actionBarRadiusDp, 0.001f)
        // The target owns the capsule's full height, so it is a 48dp tap target
        // even though the inset eats into the width instead.
        assertEquals(EditModeDesign.actionBarHeightDp, EditModeDesign.actionButtonSizeDp, 0.001f)
        assertTrue(EditModeDesign.actionButtonSizeDp >= 48f)
        // Press feedback is a rounded square inside the target, never a pill
        // larger than the target or a second capsule.
        assertTrue(EditModeDesign.actionButtonRadiusDp <= EditModeDesign.actionButtonSizeDp / 2f)
        assertTrue(EditModeDesign.actionButtonRadiusDp < EditModeDesign.actionBarRadiusDp)
        // The glyph keeps clear space inside the target on both axes.
        assertTrue(EditModeDesign.actionIconDp <= EditModeDesign.actionButtonSizeDp - 16f)
    }

    @Test
    fun `six actions fit a 360dp phone without shrinking a target`() {
        // Screen 360dp - the desktop's two 16dp margins = 328dp of capsule;
        // minus the two insets and the divider, six equal targets must each
        // stay at or above a 48dp touch target.
        val available = 360f - EditModeDesign.screenMarginDp * 2 -
            EditModeDesign.actionBarInsetDp * 2 - EditModeDesign.actionDividerWidthDp
        val perTarget = available / 6f
        assertTrue("per-target width $perTarget", perTarget >= 48f)
    }

    @Test
    fun `the remove target is a circular finger-sized affordance`() {
        assertTrue(EditModeDesign.removeTargetSizeDp >= 48f)
        assertTrue(EditModeDesign.removeTargetIconDp <= EditModeDesign.removeTargetSizeDp - 16f)
        // Quiet until it matters, then slightly larger: state is legible without
        // a label anywhere on screen.
        assertTrue(EditModeDesign.removeTargetRestAlpha in 0.2f..0.7f)
        assertTrue(EditModeDesign.removeTargetArmedScale > 1f)
        assertTrue(EditModeDesign.draggedAlphaWhenRemoving < 1f)
    }

    @Test
    fun `drop zones are quieter than the icons they frame`() {
        assertTrue(EditModeDesign.slotBorderAlpha <= 0.15f)
        assertTrue(EditModeDesign.slotFillAlpha < EditModeDesign.slotBorderAlpha)
        assertEquals(20f, EditModeDesign.slotRadiusDp, 0.001f)
        assertEquals(12f, EditModeDesign.gridGutterDp, 0.001f)
        assertEquals(EditModeDesign.slotBorderDp, 1f, 0.001f)
    }

    @Test
    fun `the capsule rises less than it is tall`() {
        assertTrue(EditModeDesign.actionBarRiseDp < EditModeDesign.actionBarHeightDp)
    }

    @Test
    fun `one screen margin is shared by every surface`() {
        // The drop zones align to the desktop's own margin, at rest and while
        // editing: one value, never a second editing layout.
        assertEquals(24f, EditModeDesign.screenMarginDp, 0.001f)
        assertEquals(28f, EditModeDesign.dockRadiusDp, 0.001f)
        assertTrue(EditModeDesign.pillRadiusDp > EditModeDesign.dockRadiusDp)
    }

    @Test
    fun `the type ladder keeps the clock as the focal point`() {
        assertEquals(14f, EditModeDesign.dateSp, 0.001f)
        assertEquals(14f, EditModeDesign.hintTextSp, 0.001f)
        assertTrue(EditModeDesign.dateAlpha in 0.7f..0.9f)
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
        assertEquals(EditModeDesign.removeTargetSizeDp * density, EditModeDesign.removeTargetSizePx(density).toFloat(), 0.001f)
    }
}
