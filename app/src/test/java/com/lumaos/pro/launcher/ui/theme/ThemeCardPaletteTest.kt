package com.lumaos.pro.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCardPaletteTest {
    @Test
    fun `theme card text remains readable on actual opaque gradient stops`() {
        ThemePresets.ALL.forEach { spec ->
            val card = ThemeCardPalette.forSpec(spec)
            listOf(card.background, card.middle, card.end).forEach { background ->
                assertEquals(255, ColorMath.alpha(background))
                assertTrue("${spec.displayName} title", ColorMath.contrast(card.content, background) >= 4.5)
                val renderedMuted = ColorMath.composite(card.muted, background)
                assertTrue("${spec.displayName} caption", ColorMath.contrast(renderedMuted, background) >= 4.5)
            }
        }
    }
}
