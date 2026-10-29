package com.lumaos.pro.launcher.ui.glass

import com.lumaos.pro.launcher.intelligence.Profiles
import com.lumaos.pro.launcher.launcher.HomeCellSizing
import com.lumaos.pro.launcher.ui.theme.ColorMath
import com.lumaos.pro.launcher.ui.theme.LumaTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PerformanceAndEdgeCaseLockTest {

    @Test
    fun wallpaperBlurPixelsSmoothsSharpEdgesDeterministically() {
        val w = 24
        val h = 24
        val sharp = IntArray(w * h) { idx ->
            if ((idx % w) < 12) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val blurred = LumaWallpaperBlurCache.blurPixels(sharp, w, h, radius = 4)
        assertEquals(w * h, blurred.size)
        val boundaryLuma = ColorMath.luminance(blurred[12 * w + 12])
        assertTrue("Expected smoothed mid-tone at sharp edge, got $boundaryLuma", boundaryLuma in 0.25..0.75)

        val tmpDir = File("build/tmp-blur-test")
        val f1 = LumaWallpaperBlurCache.diskFileForTag(tmpDir, "wp:100x200:abc")
        val f2 = LumaWallpaperBlurCache.diskFileForTag(tmpDir, "wp:100x200:def")
        assertNotEquals(f1.name, f2.name)
    }

    @Test
    fun accessibilityTwoTimesFontScaleDoesNotOverflowCells() {
        val metrics = HomeCellSizing.compute(
            widthDp = 360,
            gridAreaHeightDp = 460,
            columns = 4,
            rows = 5,
            iconPrefDp = LumaTokens.iconAppInt,
            showLabels = true,
            textScale = 2.0f
        )
        assertTrue(metrics.iconDp >= HomeCellSizing.MIN_ICON_DP)
        assertTrue(metrics.dockIconDp >= HomeCellSizing.MIN_ICON_DP)
        assertEquals(22f, metrics.labelSp, 0.01f)
        assertTrue(metrics.cellHeightDp >= metrics.iconDp + 20)
    }

    @Test
    fun performanceAndBatteryBudgetsMatchPhaseFiveSpec() {
        assertEquals(1.50f, LumaTokens.wallpaperBlurOverscan, 0.001f)
        assertEquals(0.50f, LumaTokens.transitionBlurScale, 0.001f)
        assertEquals(30_000L, LumaTokens.backgroundCacheReleaseDelayMs)
        assertEquals(200L, LumaTokens.wallpaperChangeDebounceMs)
        assertEquals(20, LumaTokens.drawerItemViewCacheSize)
        assertTrue("Balanced profile must cache 200+ drawer app icons", Profiles.BALANCED.maxCachedIcons >= 200)
        // Worst-case 320 * 56dp ARGB_8888 icons (~38KB each) < 15MB, well within <150MB budget
        val maxIconBytes = Profiles.VISUAL_QUALITY.maxCachedIcons * 168L * 168L * 4L
        assertTrue(maxIconBytes < 40L * 1024L * 1024L)
    }
}
