package com.lumaos.pro.launcher.ui.theme

import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Guards the bundled theme wallpapers on the JVM: every [WallpaperArt] maps to a
 * drawable that ships, and the light/dark property the engine trusts for home
 * text matches the real pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeArtAssetsTest {

    private fun firstDir(vararg candidates: String): File? = candidates.map(::File).firstOrNull { it.isDirectory }

    private val resDir = firstDir("src/main/res/drawable-nodpi", "app/src/main/res/drawable-nodpi")
    private val source = listOf(
        "src/main/java/com/lumaos/pro/launcher/ui/theme/WallpaperResources.kt",
        "app/src/main/java/com/lumaos/pro/launcher/ui/theme/WallpaperResources.kt"
    ).map(::File).firstOrNull { it.isFile }

    /** `WallpaperArt.X -> R.drawable.y` pairs from [WallpaperArtResources.res]. */
    private fun mapping(): Map<String, String> {
        assertNotNull("WallpaperResources.kt", source)
        return Regex("""WallpaperArt\.([A-Z_]+)\s*->\s*R\.drawable\.([a-z0-9_]+)""")
            .findAll(source!!.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun assetFor(name: String): File? =
        listOf("png", "webp", "jpg").map { File(resDir, "$name.$it") }.firstOrNull { it.isFile }

    @Test fun `every wallpaper art maps to a shipped drawable`() {
        assertNotNull("drawable-nodpi", resDir)
        val map = mapping()
        for (art in WallpaperArt.entries) {
            val name = map[art.name]
            assertNotNull("${art.name} has a res() mapping", name)
            val file = assetFor(name!!)
            assertNotNull("${art.name} -> $name ships in drawable-nodpi", file)
            assertTrue("${art.name} asset is not a stub", file!!.length() > 10_000L)
        }
        assertEquals("one drawable per art", WallpaperArt.entries.size, map.values.toSet().size)
    }

    @Test fun `theme wallpapers are full-bleed portrait art`() {
        val map = mapping()
        for (art in WallpaperArt.entries) {
            val file = assetFor(map.getValue(art.name)) ?: continue
            if (file.extension != "png") continue
            val img = BitmapFactory.decodeFile(file.absolutePath)
            assertNotNull("${file.name} decodes", img)
            assertTrue("${file.name} is portrait (${img.width}x${img.height})", img.height > img.width)
            assertTrue("${file.name} is at least 1080 wide", img.width >= 1080)
        }
    }

    @Test fun `declared text polarity matches the artwork pixels`() {
        val map = mapping()
        for (art in WallpaperArt.entries) {
            val file = assetFor(map.getValue(art.name)) ?: continue
            if (file.extension != "png") continue
            val img = BitmapFactory.decodeFile(file.absolutePath)
            assertNotNull("${file.name} decodes", img)
            // Mean relative luminance over the clock/date band and the dock band,
            // where home text and glyphs sit.
            var sum = 0.0
            var n = 0
            for (band in listOf(0.06..0.30, 0.80..0.97)) {
                var y = (img.height * band.start).toInt()
                while (y < img.height * band.endInclusive) {
                    var x = 0
                    while (x < img.width) {
                        sum += ColorMath.luminance(ColorMath.opaque(img.getPixel(x, y)))
                        n++
                        x += 16
                    }
                    y += 16
                }
            }
            val mean = sum / n
            if (art.supportsDarkText) {
                assertTrue("${art.name} declares dark text but text bands are dark (L=$mean)", mean > 0.30)
            } else {
                assertTrue("${art.name} declares light text but text bands are light (L=$mean)", mean < 0.30)
            }
            assertEquals("${art.name} seeds agree with the art", art.supportsDarkText, art.seeds.supportsDarkText)
        }
    }

    @Test fun `retired aurora asset is gone and its key still resolves`() {
        assertNotNull(resDir)
        assertFalse(File(resDir, "luma_wall_aurora.webp").exists())
        assertEquals(WallpaperArt.AURORA_NIGHT, WallpaperArt.fromStorageKey("aurora_night"))
        assertEquals("theme_wall_aurora_borealis", mapping()["AURORA_NIGHT"])
    }
}
