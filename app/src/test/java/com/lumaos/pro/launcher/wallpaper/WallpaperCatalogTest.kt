package com.lumaos.pro.launcher.wallpaper

import org.junit.Assert.*
import org.junit.Test

class WallpaperCatalogTest {
    private val name = "01234567-89ab-cdef-0123-456789abcdef"
    private val photo = WallpaperEntry("photo:$name", "A photo", "Private")
    private val entries = WallpaperCatalog.bundled() + photo

    @Test fun `catalog reuses the three bundled originals with stable unique ids`() {
        val bundled = WallpaperCatalog.bundled()
        assertEquals(3, bundled.size)
        assertEquals(3, bundled.map { it.id }.toSet().size)
        assertTrue(bundled.all { !it.isCustom && it.id.startsWith("art:") })
    }

    @Test fun `categories and favorites filter actual entries only`() {
        val favorites = setOf(entries.first().id, photo.id, "photo:missing")
        assertEquals(entries, WallpaperCatalog.filter(entries, WallpaperCategory.ALL, favorites))
        assertEquals(entries.dropLast(1), WallpaperCatalog.filter(entries, WallpaperCategory.LUMA, favorites))
        assertEquals(listOf(photo), WallpaperCatalog.filter(entries, WallpaperCategory.PHOTOS, favorites))
        assertEquals(listOf(entries.first(), photo), WallpaperCatalog.filter(entries, WallpaperCategory.FAVORITES, favorites))
        assertTrue(WallpaperCatalog.filter(entries, WallpaperCategory.FAVORITES, emptySet()).isEmpty())
    }

    @Test fun `custom file ids reject traversal and invalid names`() {
        assertEquals("photo:$name", WallpaperCatalog.customId(name))
        assertEquals(name, WallpaperCatalog.customName("photo:$name"))
        listOf("../secret", "$name/../foo", "PHOTO:$name", "photo:../$name", "photo:$name.img", "").forEach {
            assertNull(WallpaperCatalog.customName(it))
        }
        assertNull(WallpaperCatalog.customId("../$name"))
    }

    @Test fun `source bounds reject empty huge and overflow dimensions`() {
        assertTrue(WallpaperLimits.validSource(10_000, 10_000))
        assertFalse(WallpaperLimits.validSource(0, 100))
        assertFalse(WallpaperLimits.validSource(20_001, 1))
        assertFalse(WallpaperLimits.validSource(20_000, 20_000))
        assertFalse(WallpaperLimits.validSource(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun `sampling stays within edge and pixel budget for awkward aspect ratios`() {
        for ((w, h) in listOf(4032 to 3024, 9999 to 400, 20_000 to 1, 10_000 to 10_000, 1 to 1)) {
            val sample = WallpaperLimits.sampleSize(w, h, 720, 720L * 720)
            assertEquals(0, sample and (sample - 1))
            val decodedWidth = (w.toLong() + sample - 1) / sample
            val decodedHeight = (h.toLong() + sample - 1) / sample
            assertTrue(decodedWidth <= 720 && decodedHeight <= 720)
            assertTrue(decodedWidth * decodedHeight <= 720L * 720)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sampling rejects invalid dimensions`() { WallpaperLimits.sampleSize(-1, 10, 100, 10000) }
}
