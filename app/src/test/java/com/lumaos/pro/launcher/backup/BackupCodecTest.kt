package com.lumaos.pro.launcher.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    private val sample: Map<String, Any> = mapOf(
        "theme_id" to "aurora",
        "grid_columns" to 5,
        "text_scale" to 1.2f,
        "show_labels" to false,
        "accent_override" to 0xFF3DF2E0.toInt(),
        "hidden_apps_work" to "0:com.game/com.game.Main",
        "pinned_apps_work" to "0:com.chat/com.chat.Main|0:com.mail/com.mail.Main",
        "drawer_presentation" to "list",
        "widgets_enabled" to true,
        "gesture_swipe_left" to "open_studio",
        "home_layout_personal" to "v2\nD|A|0:com.a/com.a.Main\nP|W|12|p/c|2,A|0:com.b/com.b.Main\n",
        "last_action_at" to "x=1",
        "context_dnd_managed" to true,
        "totally_unknown" to "x"
    )

    @Test fun `known settings round trip with their types`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sample, "0.2.0", 123L))
        assertEquals("aurora", decoded.values["theme_id"])
        assertEquals(5, decoded.values["grid_columns"])
        assertEquals(1.2f, decoded.values["text_scale"] as Float, 0.0001f)
        assertEquals(false, decoded.values["show_labels"])
        assertEquals(0xFF3DF2E0.toInt(), decoded.values["accent_override"])
        assertEquals("0:com.game/com.game.Main", decoded.values["hidden_apps_work"])
        assertEquals("0:com.chat/com.chat.Main|0:com.mail/com.mail.Main", decoded.values["pinned_apps_work"])
        assertEquals("list", decoded.values["drawer_presentation"])
        assertEquals(true, decoded.values["widgets_enabled"])
        assertEquals("open_studio", decoded.values["gesture_swipe_left"])
        assertEquals(123L, decoded.createdAtMillis)
        assertEquals("0.2.0", decoded.appVersion)
    }

    @Test fun `device state and unknown keys are never exported`() {
        val text = BackupCodec.encode(sample, "0.2.0", 1L)
        assertFalse(text.contains("last_action_at"))
        assertFalse(text.contains("context_dnd_managed"))
        assertFalse(text.contains("totally_unknown"))
    }

    @Test fun `dynamic wallpaper colors and bundled art id round trip while local photo ids stay excluded`() {
        val bundledSnapshot = sample + mapOf(
            "dynamic_wallpaper_colors" to true,
            "active_wallpaper_id" to "art:17_snow_peaks_at_sunrise",
            "active_wallpaper_system_id" to 42,
            "cached_wallpaper_seeds" to "art:17_snow_peaks_at_sunrise|1|2|3|0|4|0.5|0.1"
        )
        val encodedBundled = BackupCodec.encode(bundledSnapshot, "0.3.0", 99L)
        val decodedBundled = BackupCodec.decode(encodedBundled)
        assertEquals(true, decodedBundled.values["dynamic_wallpaper_colors"])
        assertEquals("art:17_snow_peaks_at_sunrise", decodedBundled.values["active_wallpaper_id"])
        assertFalse(encodedBundled.contains("active_wallpaper_system_id"))
        assertFalse(encodedBundled.contains("cached_wallpaper_seeds"))

        val photoSnapshot = sample + mapOf(
            "active_wallpaper_id" to "photo:01234567-89ab-cdef-0123-456789abcdef"
        )
        val decodedPhoto = BackupCodec.decode(BackupCodec.encode(photoSnapshot, "0.3.0", 100L))
        assertFalse(decodedPhoto.values.containsKey("active_wallpaper_id"))
    }

    @Test fun `widgets are stripped from exported layouts`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sample, "0.2.0", 1L))
        val layout = decoded.values["home_layout_personal"] as String
        assertFalse(layout.contains("W|"))
        assertTrue(layout.contains("com.b"))
    }

    @Test fun `type mismatches and unknown keys are skipped on restore`() {
        val doc = """{"format":"lumaos-backup","version":1,"settings":{
            "grid_columns":{"t":"s","v":"5"},
            "show_labels":{"t":"b","v":true},
            "evil":{"t":"s","v":"x"},
            "grid_rows":{"t":"i","v":99999999999}
        }}"""
        val decoded = BackupCodec.decode(doc)
        assertEquals(mapOf<String, Any>("show_labels" to true), decoded.values)
        assertEquals(setOf("grid_columns", "evil", "grid_rows"), decoded.skippedKeys.toSet())
    }

    @Test fun `pre-rebrand backup files remain restorable`() {
        val legacy = """{"format":"rolexm-backup","version":1,"settings":{
            "grid_columns":{"t":"i","v":5}
        }}"""
        assertEquals(5, BackupCodec.decode(legacy).values["grid_columns"])
    }

    @Test fun `foreign or future files are rejected`() {
        for (doc in listOf("not json", "{}", """{"format":"other","version":1,"settings":{}}""",
            """{"format":"lumaos-backup","version":99,"settings":{}}""")) {
            try {
                BackupCodec.decode(doc)
                fail("accepted $doc")
            } catch (expected: BackupCodec.InvalidBackup) {
            }
        }
    }

    @Test fun `mini json round trips nested values and escapes`() {
        val value = mapOf("s" to "quote\" back\\ nl\n tab\t é", "n" to listOf(1L, 2.5, true, null), "m" to mapOf("k" to "v"))
        assertEquals(value, MiniJson.parse(MiniJson.write(value)))
    }

    @Test fun `mini json rejects malformed input`() {
        for (bad in listOf("{", "[1,]", "{\"a\" 1}", "tru", "\"unterminated", "{} x")) {
            try {
                MiniJson.parse(bad)
                fail("accepted $bad")
            } catch (expected: MiniJson.ParseException) {
            }
        }
    }
}
