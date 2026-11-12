package com.lumaos.pro.launcher.backup

import com.lumaos.pro.launcher.backup.BackupCodec.ValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBackupTest {

    @Test fun `context engine keys are backed up with the right types`() {
        assertEquals(ValueType.INT, BackupCodec.expectedType("last_page_work"))
        assertEquals(ValueType.STRING, BackupCodec.expectedType("drawer_hidden_study"))
        assertEquals(ValueType.BOOLEAN, BackupCodec.expectedType("focus_enabled_work"))
        assertEquals(ValueType.STRING, BackupCodec.expectedType("focus_restricted_study"))
        assertEquals(ValueType.STRING, BackupCodec.expectedType("context_wallpaper_sleep"))
        assertEquals(ValueType.STRING, BackupCodec.expectedType("backdrop_personal"))
    }

    @Test fun `manual override and system wallpaper ids are device-local and never backed up`() {
        assertNull(BackupCodec.expectedType("context_manual_profile"))
        assertNull(BackupCodec.expectedType("context_manual_until"))
        assertNull(BackupCodec.expectedType("context_wallpaper_sysid_sleep"))
    }

    @Test fun `unlock length is backed up but the live unlock is transient`() {
        assertEquals(ValueType.INT, BackupCodec.expectedType("focus_unlock_minutes"))
        assertNull(BackupCodec.expectedType("focus_unlock_until"))
        assertNull(BackupCodec.expectedType("focus_unlock_profile"))
    }

    @Test fun `per-context wallpaper keys never collide with the shared favorites set`() {
        // wallpaper_favorites must stay unrestorable: restoreFromBackup clears
        // every restorable-but-absent key, so a collision would wipe favorites
        // on every restore.
        assertNull(BackupCodec.expectedType("wallpaper_favorites"))
    }

    @Test fun `context keys survive an export import round trip`() {
        val values = mapOf<String, Any>(
            "drawer_hidden_work" to "0:com.example.game/com.example.game.Play",
            "focus_enabled_work" to true,
            "focus_restricted_work" to "0:com.example.game/com.example.game.Play",
            "context_wallpaper_work" to "art:aurora",
            "context_wallpaper_study" to "photo:01234567-89ab-cdef-0123-456789abcdef",
            "context_wallpaper_sysid_work" to 42,
            "last_page_sleep" to 2,
            "focus_unlock_minutes" to 15,
            "focus_unlock_until" to 999L,
            "context_manual_profile" to "SLEEP",
            "context_manual_until" to 12345L
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(values, "1.2.0-test", 0L))
        assertEquals("0:com.example.game/com.example.game.Play", decoded.values["drawer_hidden_work"])
        assertEquals(true, decoded.values["focus_enabled_work"])
        assertEquals("art:aurora", decoded.values["context_wallpaper_work"])
        assertEquals(2, decoded.values["last_page_sleep"])
        assertEquals(15, decoded.values["focus_unlock_minutes"])
        assertTrue("live unlock must not round trip", "focus_unlock_until" !in decoded.values)
        assertTrue("manual override must not round trip", "context_manual_profile" !in decoded.values)
        assertTrue("manual override must not round trip", "context_manual_until" !in decoded.values)
        assertTrue("device-local photo ids stay excluded", "context_wallpaper_study" !in decoded.values)
        assertTrue("system wallpaper ids stay excluded", "context_wallpaper_sysid_work" !in decoded.values)
    }
}
