package com.lumaos.pro.launcher.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPaletteExtractorTest {

    @Test
    fun `empty or malformed pixel buffers return safe fallback seeds without crashing`() {
        assertEquals(
            WallpaperPaletteExtractor.FALLBACK_SEEDS,
            WallpaperPaletteExtractor.extractFromPixels(IntArray(0), 0, 0)
        )
        assertEquals(
            WallpaperPaletteExtractor.FALLBACK_SEEDS,
            WallpaperPaletteExtractor.extractFromPixels(IntArray(10), 4, 4)
        )
        assertEquals(
            WallpaperPaletteExtractor.FALLBACK_SEEDS,
            WallpaperPaletteExtractor.extractFromPixels(IntArray(16), -4, 4)
        )
        assertNull(WallpaperPaletteExtractor.extractFromBitmap(null))
    }

    @Test
    fun `estimateFocalPoint tracks salient subject within safe bounds`() {
        val w = 40
        val h = 40
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (x in 26..34 && y in 26..34) 0xFFF2B880.toInt() else 0xFF05070B.toInt()
        }
        val (fx, fy) = WallpaperPaletteExtractor.estimateFocalPoint(pixels, w, h, 0xFF05070B.toInt())
        assertTrue("Focal X shifts toward subject ($fx)", fx > 0.60f && fx <= 0.75f)
        assertTrue("Focal Y shifts toward subject ($fy)", fy > 0.60f && fy <= 0.75f)
    }

    @Test
    fun `dark obsidian scene with thin warm gold rim extracts dark dominant and warm accent`() {
        val w = 32
        val h = 48
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (y in 20..23 && x in 8..24) 0xFFD4AF6A.toInt() else 0xFF060709.toInt()
        }
        val seeds = WallpaperPaletteExtractor.extractFromPixels(pixels, w, h)
        assertNotNull(seeds)
        assertEquals(false, seeds!!.supportsDarkText)
        assertTrue("Dominant stays dark", ColorMath.luminance(seeds.primary) < 0.05)
        assertNotNull(seeds.accent)
        // Warm gold has higher red than blue.
        assertTrue("Accent captures warm gold highlight", ColorMath.red(seeds.accent!!) > ColorMath.blue(seeds.accent!!) + 30)
    }

    @Test
    fun `warm ivory daylight scene reports supportsDarkText true`() {
        val w = 24
        val h = 36
        val pixels = IntArray(w * h) { idx ->
            if ((idx % w + idx / w) % 3 == 0) 0xFFEADBC8.toInt() else 0xFFDCC9B2.toInt()
        }
        val seeds = WallpaperPaletteExtractor.extractFromPixels(pixels, w, h)
        assertNotNull(seeds)
        assertEquals(true, seeds!!.supportsDarkText)
        assertTrue(seeds.topLuminance!! > 0.45f)
        assertTrue(seeds.bottomLuminance!! > 0.45f)
    }

    @Test
    fun `monochrome graphite scene preserves achromatic character without false chroma`() {
        val w = 24
        val h = 36
        val pixels = IntArray(w * h) { idx ->
            val y = idx / w
            when {
                y < 12 -> 0xFF141517.toInt()
                y < 24 -> 0xFF2B2C2E.toInt()
                else -> 0xFF565759.toInt()
            }
        }
        val seeds = WallpaperPaletteExtractor.extractFromPixels(pixels, w, h)
        assertNotNull(seeds)
        assertEquals(false, seeds!!.supportsDarkText)
        val accent = seeds.accent!!
        val spread = maxOf(ColorMath.red(accent), ColorMath.green(accent), ColorMath.blue(accent)) -
            minOf(ColorMath.red(accent), ColorMath.green(accent), ColorMath.blue(accent))
        assertTrue("Monochrome accent stays low-chroma (spread=$spread)", spread <= 12)
    }

    @Test
    fun `split horizon sunlit sky over dark mountain base measures distinct top and bottom luminance`() {
        val w = 32
        val h = 60
        val pixels = IntArray(w * h) { idx ->
            val y = idx / w
            if (y < 16) 0xFFF3C3A8.toInt() else 0xFF061B33.toInt()
        }
        val seeds = WallpaperPaletteExtractor.extractFromPixels(pixels, w, h)
        assertNotNull(seeds)
        assertEquals(false, seeds!!.supportsDarkText)
        assertTrue("Dominant reflects dark mountain body, not bright sky strip", ColorMath.luminance(seeds.primary) < 0.12)
        assertTrue("Top band captures bright sky", seeds.topLuminance!! > 0.50f)
        assertTrue("Bottom band captures dark base", seeds.bottomLuminance!! < 0.08f)
    }

    @Test
    fun `wallpaper seeds encode and decode round trip with source key`() {
        val original = WallpaperSeeds(
            primary = 0xFF031C35.toInt(),
            secondary = 0xFF23456A.toInt(),
            tertiary = 0xFF102030.toInt(),
            supportsDarkText = false,
            accent = 0xFFF3BCA1.toInt(),
            topLuminance = 0.56f,
            bottomLuminance = 0.05f
        )
        val encoded = original.encode("art:17_snow_peaks_at_sunrise")
        val decoded = WallpaperSeeds.decode(encoded)
        assertNotNull(decoded)
        assertEquals("art:17_snow_peaks_at_sunrise", decoded!!.first)
        assertEquals(original.primary, decoded.second.primary)
        assertEquals(original.secondary, decoded.second.secondary)
        assertEquals(original.tertiary, decoded.second.tertiary)
        assertEquals(original.supportsDarkText, decoded.second.supportsDarkText)
        assertEquals(original.accent, decoded.second.accent)
        assertEquals(original.topLuminance!!, decoded.second.topLuminance!!, 0.001f)
        assertEquals(original.bottomLuminance!!, decoded.second.bottomLuminance!!, 0.001f)
        assertNull(WallpaperSeeds.decode(null))
        assertNull(WallpaperSeeds.decode("corrupt"))
    }
}
