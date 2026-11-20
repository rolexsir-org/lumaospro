package com.lumaos.pro.launcher.launcher.home

import com.lumaos.pro.launcher.ui.theme.ColorMath
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsidianStyleTest {
    @Test fun `dark palette text and accent are readable on their surfaces`() {
        assertTrue(ColorMath.contrast(ObsidianStyle.CONTENT, ObsidianStyle.BACKGROUND) >= 4.5)
        assertTrue(ColorMath.contrast(ObsidianStyle.MUTED_DARK, ObsidianStyle.BACKGROUND) >= 4.5)
        assertTrue(ColorMath.contrast(ObsidianStyle.MUTED_DARK, ObsidianStyle.SURFACE) >= 4.5)
        assertTrue(ColorMath.contrast(ObsidianStyle.BLUE_DARK, ObsidianStyle.BACKGROUND) >= 4.5)
        assertTrue(ColorMath.contrast(ObsidianStyle.BLUE_LIGHT, ColorMath.WHITE) >= 4.5)
        assertTrue(ColorMath.contrast(ObsidianStyle.BLUE_DARK, 0xFF20314A.toInt()) >= 4.5)
    }
}
