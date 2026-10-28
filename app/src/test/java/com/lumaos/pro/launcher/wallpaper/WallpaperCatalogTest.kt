package com.lumaos.pro.launcher.wallpaper

import com.lumaos.pro.launcher.ui.theme.WallpaperArt
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class WallpaperCatalogTest {
    private val name = "01234567-89ab-cdef-0123-456789abcdef"
    private val photo = WallpaperEntry("photo:$name", "A photo", "Private")
    private val entries = WallpaperCatalog.bundled() + photo

    @Test fun `catalog ships all 30 unique curated wallpapers across 6 collections with distinct assets`() {
        val bundled = WallpaperCatalog.bundled()
        assertEquals(30, bundled.size)
        assertEquals(30, bundled.map { it.id }.toSet().size)
        assertEquals(30, bundled.map { it.title }.toSet().size)
        assertEquals(30, bundled.map { it.description }.toSet().size)
        assertEquals(30, bundled.mapNotNull { it.resourceKey }.toSet().size)
        assertEquals(WallpaperCollection.entries.toSet(), bundled.mapNotNull { it.collection }.toSet())
        assertTrue(bundled.all { !it.isCustom && it.id.startsWith("art:") && it.mood.isNotBlank() })
        assertTrue(bundled.all { it.focalX in 0f..1f && it.focalY in 0f..1f && it.precomputedSeeds != null })

        val resDirCandidates = listOf(
            File("src/main/res/drawable-nodpi"),
            File("app/src/main/res/drawable-nodpi")
        )
        val resDir = resDirCandidates.firstOrNull { it.isDirectory }
        assertNotNull("Expected drawable-nodpi directory", resDir)

        val digests = HashSet<String>()
        for (entry in bundled) {
            val file = File(resDir!!, "luma_wall_${entry.resourceKey}.webp")
            assertTrue("Missing asset for ${entry.id}: ${file.path}", file.isFile && file.length() > 1_000L)
            val sha = MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertTrue("Duplicate binary wallpaper asset for ${entry.id}", digests.add(sha))
            assertEquals(entry, WallpaperCatalog.findBundled(entry.id))
        }

        // Legacy v3.2 bundled IDs remain resolvable for existing favourites and backups.
        for (art in WallpaperArt.entries) {
            val legacy = WallpaperCatalog.findBundled("art:${art.storageKey}")
            assertNotNull(legacy)
            assertFalse(legacy!!.isCustom)
        }
    }

    @Test fun `categories collections and favorites filter actual entries only`() {
        val favorites = setOf(entries.first().id, photo.id, "photo:missing")
        assertEquals(entries, WallpaperCatalog.filter(entries, WallpaperCategory.ALL, favorites))
        assertEquals(entries.dropLast(1), WallpaperCatalog.filter(entries, WallpaperCategory.LUMA, favorites))
        assertEquals(listOf(photo), WallpaperCatalog.filter(entries, WallpaperCategory.PHOTOS, favorites))
        assertEquals(listOf(entries.first(), photo), WallpaperCatalog.filter(entries, WallpaperCategory.FAVORITES, favorites))
        assertTrue(WallpaperCatalog.filter(entries, WallpaperCategory.FAVORITES, emptySet()).isEmpty())

        for (col in WallpaperCollection.entries) {
            val byCol = WallpaperCatalog.filter(entries, WallpaperCategory.LUMA, favorites, col)
            assertTrue("Collection $col must not be empty", byCol.isNotEmpty())
            assertTrue(byCol.all { it.collection == col })
        }
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
