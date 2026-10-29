package com.lumaos.pro.launcher.launcher.home

import com.lumaos.pro.launcher.ui.theme.LumaTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LumaDepthStackTest {

    @Test
    fun restingHomeLayerHasIdentityTransformsAndZeroBlur() {
        val frame = LumaDepthStack.solve(LumaDepthStack.Inputs(density = 2f))
        assertEquals(1f, frame.wallpaperScale, 0.001f)
        assertEquals(0f, frame.wallpaperTranslationX, 0.001f)
        assertEquals(0f, frame.wallpaperTranslationY, 0.001f)
        assertEquals(1f, frame.desktopScale, 0.001f)
        assertEquals(1f, frame.desktopAlpha, 0.001f)
        assertEquals(0f, frame.desktopBlurRadiusPx, 0.001f)
    }

    @Test
    fun openingDrawerProgressivelyRecedesAndBlursHomeWithParallax() {
        val mid = LumaDepthStack.solve(LumaDepthStack.Inputs(drawerProgress = 0.5f, density = 2f, inMotion = true))
        val full = LumaDepthStack.solve(LumaDepthStack.Inputs(drawerProgress = 1.0f, density = 2f, inMotion = false))

        assertTrue(mid.desktopScale < 1f && full.desktopScale < mid.desktopScale)
        assertEquals(0.94f, full.desktopScale, 0.001f)
        assertTrue(mid.desktopAlpha < 1f && full.desktopAlpha < mid.desktopAlpha)
        assertTrue(full.wallpaperScale > 1f)
        // Parallax: desktop translates further upward than wallpaper
        assertTrue(full.desktopTranslationY < full.wallpaperTranslationY)
        assertTrue(full.wallpaperTranslationY < 0f)
        // Transition 50% blur budget during motion vs full 40dp * 2 = 80px at rest
        assertEquals(20f, mid.desktopBlurRadiusPx, 0.5f)
        assertEquals(80f, full.desktopBlurRadiusPx, 0.5f)
    }

    @Test
    fun horizontalSwipeAppliesOpposingParallaxBetweenDesktopAndWallpaper() {
        val frame = LumaDepthStack.solve(LumaDepthStack.Inputs(parallaxX = 0.25f, density = 3f))
        assertTrue(frame.desktopTranslationX > 0f)
        assertTrue(frame.wallpaperTranslationX < 0f)
    }

    @Test
    fun canonicalLayerDepthOrderIsStrictlyIncreasing() {
        assertTrue(LumaTokens.depthWallpaper < LumaTokens.depthHome)
        assertTrue(LumaTokens.depthHome < LumaTokens.depthFolder)
        assertTrue(LumaTokens.depthFolder < LumaTokens.depthDrawer)
        assertTrue(LumaTokens.depthDrawer < LumaTokens.depthModal)
        assertTrue(LumaTokens.depthModal < LumaTokens.depthLaunchPortal)
    }
}
