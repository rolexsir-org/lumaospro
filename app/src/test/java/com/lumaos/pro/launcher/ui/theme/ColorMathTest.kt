package com.lumaos.pro.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorMathTest {

    @Test fun `contrast of black on white is 21 to 1`() {
        assertEquals(21.0, ColorMath.contrast(ColorMath.BLACK, ColorMath.WHITE), 0.01)
        assertEquals(1.0, ColorMath.contrast(0xFF777777.toInt(), 0xFF777777.toInt()), 0.0001)
    }

    @Test fun `luminance matches the WCAG reference for mid grey`() {
        // #777777 has relative luminance ~0.184 per the WCAG formula.
        assertEquals(0.184, ColorMath.luminance(0xFF777777.toInt()), 0.002)
    }

    @Test fun `composite blends alpha over an opaque background`() {
        val half = ColorMath.withAlpha(ColorMath.WHITE, 0.5f)
        val result = ColorMath.composite(half, ColorMath.BLACK)
        assertTrue(abs(ColorMath.red(result) - 128) <= 1)
        assertEquals(255, ColorMath.alpha(result))
    }

    @Test fun `ensureContrast reaches the requested ratio and keeps alpha`() {
        val bg = 0xFF202124.toInt()
        val weak = 0x80404448.toInt()
        val fixed = ColorMath.ensureContrast(weak, bg, 4.5)
        assertTrue(ColorMath.contrast(ColorMath.opaque(fixed), bg) >= 4.5)
        assertEquals(0x80, ColorMath.alpha(fixed))
    }

    @Test fun `ensureContrast leaves already-legible colours untouched`() {
        assertEquals(ColorMath.WHITE, ColorMath.ensureContrast(ColorMath.WHITE, ColorMath.BLACK, 7.0))
    }

    @Test fun `ensureContrast moves towards black on light backgrounds`() {
        val fixed = ColorMath.ensureContrast(0xFFB0B0B0.toInt(), 0xFFF4F2EC.toInt(), 7.0)
        assertTrue(ColorMath.luminance(fixed) < ColorMath.luminance(0xFFB0B0B0.toInt()))
        assertTrue(ColorMath.contrast(fixed, 0xFFF4F2EC.toInt()) >= 7.0)
    }

    @Test fun `oklch round trip is lossless within one step per channel`() {
        val samples = listOf(0xFF3DF2E0, 0xFFE2B66B, 0xFF000000, 0xFFFFFFFF, 0xFF3F5B45, 0xFFA78BFA, 0xFF808080)
        for (s in samples) {
            val c = s.toInt()
            val back = Oklch.fromArgb(c).toArgb()
            assertTrue("R ${ColorMath.toHex(c)}", abs(ColorMath.red(back) - ColorMath.red(c)) <= 1)
            assertTrue("G ${ColorMath.toHex(c)}", abs(ColorMath.green(back) - ColorMath.green(c)) <= 1)
            assertTrue("B ${ColorMath.toHex(c)}", abs(ColorMath.blue(back) - ColorMath.blue(c)) <= 1)
        }
    }

    @Test fun `out-of-gamut oklch colours are mapped into sRGB without changing lightness much`() {
        val wild = Oklch(0.7, 0.5, 2.0)
        val argb = wild.toArgb()
        val back = Oklch.fromArgb(argb)
        assertEquals(0.7, back.l, 0.02)
    }

    @Test fun `hex parsing accepts six and eight digits`() {
        assertEquals(0xFF3DF2E0.toInt(), ColorMath.parseHex("#3DF2E0"))
        assertEquals(0x803DF2E0.toInt(), ColorMath.parseHex("803DF2E0"))
        assertNull(ColorMath.parseHex("xyz"))
        assertNull(ColorMath.parseHex("#12345"))
    }

    @Test fun `isDark classifies by better text contrast`() {
        assertTrue(ColorMath.isDark(0xFF101214.toInt()))
        assertFalse(ColorMath.isDark(0xFFF4F2EC.toInt()))
    }

    @Test fun `lerp hits both endpoints`() {
        assertEquals(ColorMath.BLACK, ColorMath.lerp(ColorMath.BLACK, ColorMath.WHITE, 0f))
        assertEquals(ColorMath.WHITE, ColorMath.lerp(ColorMath.BLACK, ColorMath.WHITE, 1f))
    }
}
