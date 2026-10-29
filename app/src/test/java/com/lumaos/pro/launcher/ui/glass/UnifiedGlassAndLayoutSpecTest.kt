package com.lumaos.pro.launcher.ui.glass

import com.lumaos.pro.launcher.launcher.HomeCellSizing
import com.lumaos.pro.launcher.launcher.model.GridRules
import com.lumaos.pro.launcher.launcher.model.HomePreset
import com.lumaos.pro.launcher.ui.theme.GlassCapabilities
import com.lumaos.pro.launcher.ui.theme.GlassGeometry
import com.lumaos.pro.launcher.ui.theme.GlassMaterials
import com.lumaos.pro.launcher.ui.theme.GlassRole
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import com.lumaos.pro.launcher.ui.theme.ThemeEngine
import com.lumaos.pro.launcher.ui.theme.ThemeEnvironment
import com.lumaos.pro.launcher.ui.theme.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedGlassAndLayoutSpecTest {

    @Test
    fun glassGeometryMatchesApplePrecisionRadiiAndMargins() {
        assertEquals(24f, GlassGeometry.GUTTER_DP, 0.001f)
        assertEquals(88f, GlassGeometry.DOCK_HEIGHT_DP, 0.001f)
        assertEquals(44f, GlassGeometry.SEARCH_HEIGHT_DP, 0.001f)
        assertEquals(28f, GlassGeometry.ROLE_RADIUS_DP[GlassRole.DOCK]!!, 0.001f)
        assertEquals(28f, GlassGeometry.ROLE_RADIUS_DP[GlassRole.SHEET]!!, 0.001f)
        assertEquals(24f, GlassGeometry.ROLE_RADIUS_DP[GlassRole.FOLDER]!!, 0.001f)
        assertEquals(22f, GlassGeometry.ROLE_RADIUS_DP[GlassRole.WIDGET]!!, 0.001f)
        assertEquals(16f, GlassGeometry.ROLE_RADIUS_DP[GlassRole.MENU]!!, 0.001f)
        assertEquals(12f, GlassGeometry.ROLE_RADIUS_DP[GlassRole.SETTINGS_CONTROL]!!, 0.001f)
    }

    @Test
    fun allPresetsDisableDockTextLabelsAndGridMatchesBreakpoint() {
        for (preset in HomePreset.entries) {
            assertFalse("Dock must never show text labels on preset $preset", preset.dockLabels)
        }
        assertEquals(4, GridRules.defaultColumns(390))
        assertEquals(5, GridRules.defaultColumns(410))
        assertEquals(5, GridRules.defaultColumns(430))
    }

    @Test
    fun homeAndDockDefaultSizingYields56dpIconsOnStandardPhone() {
        val metrics = HomeCellSizing.compute(
            widthDp = 412,
            gridAreaHeightDp = 500,
            columns = 5,
            rows = 5,
            iconPrefDp = LumaTokens.iconAppInt,
            showLabels = true,
            textScale = 1f
        )
        assertEquals(56, metrics.iconDp)
        assertEquals(56, metrics.dockIconDp)
        assertEquals(11f, metrics.labelSp, 0.001f)
        assertTrue("Row height (${metrics.cellHeightDp}dp) must be >= 84dp", metrics.cellHeightDp >= 84)
    }

    @Test
    fun glassDepthMappingOrdersHomeBelowDrawerAndModal() {
        val theme = ThemeEngine.resolve(ThemeSettings(), ThemeEnvironment(systemDark = true, minuteOfDay = 0))
        val palette = GlassMaterials.palette(theme, GlassCapabilities.DEFAULT)
        assertTrue(GlassDrawable.defaultDepthForRole(GlassRole.DOCK) < GlassDrawable.defaultDepthForRole(GlassRole.SHEET))
        assertTrue(GlassDrawable.defaultDepthForRole(GlassRole.FOLDER) < GlassDrawable.defaultDepthForRole(GlassRole.MENU))
        assertTrue(palette[GlassRole.DOCK].radiusDp > palette[GlassRole.SEARCH].radiusDp)
    }
}
