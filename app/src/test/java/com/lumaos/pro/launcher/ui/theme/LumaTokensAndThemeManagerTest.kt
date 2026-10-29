package com.lumaos.pro.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LumaTokensAndThemeManagerTest {

    @Test
    fun tokensMatchUnifiedApplePrecisionSpecification() {
        assertEquals(24f, LumaTokens.screenMarginHorizontal, 0.001f)
        assertEquals(56f, LumaTokens.iconApp, 0.001f)
        assertEquals(88f, LumaTokens.dockHeight, 0.001f)
        assertEquals(12f, LumaTokens.dockBottomMargin, 0.001f)
        assertEquals(44f, LumaTokens.searchBarHeight, 0.001f)
        assertEquals(72f, LumaTokens.typeDisplayClockSp, 0.001f)
        assertEquals(11f, LumaTokens.typeAppLabelSp, 0.001f)
        assertEquals(0.85f, LumaTokens.typeAppLabelAlpha, 0.001f)
        assertEquals(0.78f, LumaTokens.springDamping, 0.001f)
        assertEquals(280f, LumaTokens.springStiffness, 0.001f)
        assertEquals(15L, LumaTokens.staggerDelayMs)
    }

    @Test
    fun darkWallpaperExtractsDarkGlassPolarityAndVibrantAccent() {
        val w = 16
        val h = 16
        val pixels = IntArray(w * h) { idx ->
            if (idx % 12 == 0) 0xFF3A8BFF.toInt() else 0xFF0B0E17.toInt()
        }
        val palette = LumaWallpaperPalette.fromPixels(pixels, w, h)
        assertTrue("Expected luminance < 0.4 for dark wallpaper", palette.averageLuminance < 0.4f)
        assertTrue(palette.isDarkGlass)

        // Round-trip DataStore cache encoding
        val decoded = LumaWallpaperPalette.decode(palette.encode())
        assertNotNull(decoded)
        assertEquals(palette.isDarkGlass, decoded!!.isDarkGlass)
        assertEquals(palette.dominantColor, decoded.dominantColor)
        assertEquals(palette.accentColor, decoded.accentColor)
    }

    @Test
    fun brightWallpaperExtractsLightGlassAndAdaptsRegionOpacity() {
        val w = 16
        val h = 16
        // Top half very bright (> 0.7), bottom half deep shadow (< 0.2), overall > 0.4
        val pixels = IntArray(w * h) { idx ->
            val y = idx / w
            if (y < 10) 0xFFF2F5FA.toInt() else 0xFF0A0C12.toInt()
        }
        val palette = LumaWallpaperPalette.fromPixels(pixels, w, h)
        assertTrue(palette.averageLuminance >= 0.4f)
        assertFalse(palette.isDarkGlass)

        val brightRegionLum = palette.sampleRegionLuminance(0f, 0f, 1f, 0.25f)
        val darkRegionLum = palette.sampleRegionLuminance(0f, 0.80f, 1f, 1f)
        assertTrue(brightRegionLum > 0.70f)
        assertTrue(darkRegionLum < 0.20f)

        val alphaOverBright = palette.adaptiveGlassAlpha(isCard = false, depth = LumaTokens.depthHome, regionLuminance = brightRegionLum)
        val alphaOverMid = palette.adaptiveGlassAlpha(isCard = false, depth = LumaTokens.depthHome, regionLuminance = 0.45f)
        val alphaOverDark = palette.adaptiveGlassAlpha(isCard = false, depth = LumaTokens.depthHome, regionLuminance = darkRegionLum)
        assertEquals(alphaOverMid + 0.08f, alphaOverBright, 0.005f)
        assertEquals(alphaOverMid - 0.05f, alphaOverDark, 0.005f)
    }
}
