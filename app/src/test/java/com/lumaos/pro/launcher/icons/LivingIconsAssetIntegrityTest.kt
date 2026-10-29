package com.lumaos.pro.launcher.icons

import com.lumaos.pro.launcher.backup.MiniJson
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the physical integrity, SHA-256 digests, PNG RGBA transparency
 * headers, and manifest consistency of all 30 bundled LumaOS Pro Living Icons.
 */
class LivingIconsAssetIntegrityTest {

    private fun assetsRoot(): File {
        val candidates = listOf(
            File("src/main/assets/living_icons"),
            File("app/src/main/assets/living_icons")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate src/main/assets/living_icons from ${File(".").absolutePath}")
    }

    @Test
    fun `all 30 Living Icons exist as 1024x1024 8-bit RGBA PNGs matching verified SHA-256`() {
        val dir = assetsRoot()
        assertEquals(30, LivingIconId.entries.size)
        assertEquals(20, LivingIconId.byCategory(LivingIconCategory.CORE).size)
        assertEquals(10, LivingIconId.byCategory(LivingIconCategory.POPULAR).size)

        for (icon in LivingIconId.entries) {
            val file = File(dir, icon.filename)
            assertTrue("Missing Living Icon asset: ${file.path}", file.isFile)
            val bytes = file.readBytes()
            assertTrue("Asset unexpectedly small: ${icon.filename}", bytes.size > 100_000)

            // PNG magic header: 89 50 4E 47 0D 0A 1A 0A
            val pngSig = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            )
            assertTrue(
                "Invalid PNG signature in ${icon.filename}",
                bytes.copyOfRange(0, 8).contentEquals(pngSig)
            )

            // IHDR chunk starts at byte 8: [4-byte length=13]['IHDR'][width:4][height:4][bitDepth:1][colorType:1]
            val ihdrType = String(bytes, 12, 4, Charsets.US_ASCII)
            assertEquals("IHDR", ihdrType)
            val width = ByteBuffer.wrap(bytes, 16, 4).int
            val height = ByteBuffer.wrap(bytes, 20, 4).int
            val bitDepth = bytes[24].toInt() and 0xFF
            val colorType = bytes[25].toInt() and 0xFF
            assertEquals("Expected 1024px width for ${icon.filename}", 1024, width)
            assertEquals("Expected 1024px height for ${icon.filename}", 1024, height)
            assertEquals("Expected 8-bit channels for ${icon.filename}", 8, bitDepth)
            assertEquals("Expected RGBA (colorType 6) for ${icon.filename}", 6, colorType)

            val actualSha = sha256Hex(bytes)
            assertEquals("SHA-256 mismatch for ${icon.filename}", icon.sha256, actualSha)

            // Optical silhouette bounds sanity
            assertTrue(icon.visibleLeft in 80..140)
            assertTrue(icon.visibleTop in 80..140)
            assertTrue(icon.visibleRight in 884..944)
            assertTrue(icon.visibleBottom in 884..944)
            assertEquals(0.5f, icon.centerXFraction, 0.005f)
            assertEquals(0.5f, icon.centerYFraction, 0.005f)
            assertTrue(icon.visibleSpanFraction in 0.78f..0.82f)
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `manifest json matches LivingIconId catalog 1-to-1`() {
        val dir = assetsRoot()
        val manifestFile = File(dir, "manifest.json")
        assertTrue("Missing manifest.json", manifestFile.isFile)
        val root = MiniJson.parse(manifestFile.readText(Charsets.UTF_8)) as Map<String, Any?>
        assertEquals("complete-30", root["release"])

        val assets = root["assets"] as List<Map<String, Any?>>
        assertEquals(30, assets.size)

        val seenIds = HashSet<LivingIconId>()
        for (entry in assets) {
            val filename = entry["filename"] as String
            val idStr = filename.removeSuffix(".png")
            val catalogEntry = LivingIconId.fromStorageKey(idStr)
            assertNotNull("Manifest asset '$filename' missing from LivingIconId", catalogEntry)
            catalogEntry!!
            assertTrue("Duplicate manifest asset '$filename'", seenIds.add(catalogEntry))
            assertEquals(filename, catalogEntry.filename)
            assertEquals(entry["sha256"], catalogEntry.sha256)
            assertEquals(
                entry["category"],
                catalogEntry.category.name.lowercase()
            )
            val diskSize = File(dir, filename).length()
            assertEquals((entry["size_bytes"] as Number).toLong(), diskSize)
        }
        assertEquals(LivingIconId.entries.toSet(), seenIds)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }
}
