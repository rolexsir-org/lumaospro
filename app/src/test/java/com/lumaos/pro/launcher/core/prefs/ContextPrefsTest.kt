package com.lumaos.pro.launcher.core.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lumaos.pro.launcher.context.ContextProfile
import com.lumaos.pro.launcher.context.ManualOverride
import com.lumaos.pro.launcher.core.model.ComponentKey
import com.lumaos.pro.launcher.ui.theme.Backdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Each context owns independent persisted state: visibility, focus, pages,
 * wallpapers and the manual override. Uses a real DataStore on a temp file,
 * like the corruption recovery test.
 */
class ContextPrefsTest {

    private val mail = ComponentKey("com.example.mail", "com.example.mail.Main")
    private val game = ComponentKey("com.example.game", "com.example.game.Play")

    private fun tempPrefs(): Pair<LumaPrefs, CoroutineScope> {
        val dir = Files.createTempDirectory("luma-prefs-context").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = LumaPrefs(
            PreferenceDataStoreFactory.create(
                corruptionHandler = lumaosPrefsCorruptionHandler,
                scope = scope
            ) { File(dir, "test.preferences_pb") }
        )
        return prefs to scope
    }

    @Test fun `visibility is independent per context`() = runBlocking {
        val (prefs, scope) = tempPrefs()
        try {
            prefs.setAppVisible(ContextProfile.WORK, game, false)
            assertEquals(setOf(game), prefs.drawerHidden(ContextProfile.WORK).first())
            assertTrue(prefs.drawerHidden(ContextProfile.STUDY).first().isEmpty())
            assertTrue(prefs.drawerHidden(ContextProfile.PERSONAL).first().isEmpty())

            prefs.setAppVisible(ContextProfile.WORK, game, true)
            assertTrue(prefs.drawerHidden(ContextProfile.WORK).first().isEmpty())

            prefs.setDrawerHidden(ContextProfile.SLEEP, setOf(mail, game))
            assertEquals(setOf(mail, game), prefs.drawerHidden(ContextProfile.SLEEP).first())
            assertTrue(prefs.drawerHidden(ContextProfile.WORK).first().isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test fun `focus state is independent per context`() = runBlocking {
        val (prefs, scope) = tempPrefs()
        try {
            assertFalse(prefs.focusEnabled(ContextProfile.WORK).first())
            prefs.setFocusEnabled(ContextProfile.WORK, true)
            prefs.setAppRestricted(ContextProfile.WORK, game, true)
            assertTrue(prefs.focusEnabled(ContextProfile.WORK).first())
            assertEquals(setOf(game), prefs.focusRestricted(ContextProfile.WORK).first())
            assertFalse(prefs.focusEnabled(ContextProfile.STUDY).first())
            assertTrue(prefs.focusRestricted(ContextProfile.STUDY).first().isEmpty())

            prefs.setAppRestricted(ContextProfile.WORK, game, false)
            assertTrue(prefs.focusRestricted(ContextProfile.WORK).first().isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test fun `last page is independent per context`() = runBlocking {
        val (prefs, scope) = tempPrefs()
        try {
            assertEquals(0, prefs.lastPage(ContextProfile.WORK).first())
            prefs.setLastPage(ContextProfile.WORK, 3)
            assertEquals(3, prefs.lastPage(ContextProfile.WORK).first())
            assertEquals(0, prefs.lastPage(ContextProfile.STUDY).first())
        } finally {
            scope.cancel()
        }
    }

    @Test fun `wallpaper and backdrop overrides fall back independently`() = runBlocking {
        val (prefs, scope) = tempPrefs()
        try {
            assertNull(prefs.wallpaperFor(ContextProfile.WORK).first())
            assertNull(prefs.backdropFor(ContextProfile.WORK).first())

            prefs.recordAppliedWallpaperFor(ContextProfile.WORK, "art:aurora", null, Backdrop.LUMA)
            assertEquals("art:aurora", prefs.wallpaperFor(ContextProfile.WORK).first())
            assertEquals(Backdrop.LUMA, prefs.backdropFor(ContextProfile.WORK).first())
            assertNull(prefs.wallpaperFor(ContextProfile.STUDY).first())
            assertNull(prefs.backdropFor(ContextProfile.STUDY).first())

            prefs.clearWallpaperForIfMatches(ContextProfile.WORK, "art:other")
            assertEquals("art:aurora", prefs.wallpaperFor(ContextProfile.WORK).first())
            prefs.clearWallpaperForIfMatches(ContextProfile.WORK, "art:aurora")
            assertNull(prefs.wallpaperFor(ContextProfile.WORK).first())
        } finally {
            scope.cancel()
        }
    }

    @Test fun `clearing the wallpaper id keeps the backdrop choice`() = runBlocking {
        val (prefs, scope) = tempPrefs()
        try {
            // A device-target apply: the context follows the shared image again
            // while keeping its own backdrop.
            prefs.recordAppliedWallpaperFor(ContextProfile.WORK, "art:aurora", null, Backdrop.LUMA)
            prefs.setBackdropFor(ContextProfile.WORK, Backdrop.WALLPAPER)
            prefs.clearWallpaperIdFor(ContextProfile.WORK)
            assertNull(prefs.wallpaperFor(ContextProfile.WORK).first())
            assertNull(prefs.wallpaperSysIdFor(ContextProfile.WORK).first())
            assertEquals(Backdrop.WALLPAPER, prefs.backdropFor(ContextProfile.WORK).first())
        } finally {
            scope.cancel()
        }
    }

    @Test fun `emergency unlock round trips and clears`() = runBlocking {
        val (prefs, scope) = tempPrefs()
        try {
            assertEquals(0L, prefs.focusUnlockUntil().first())
            assertNull(prefs.focusUnlockProfile().first())
            prefs.setFocusUnlock(ContextProfile.STUDY, 999L)
            assertEquals(999L, prefs.focusUnlockUntil().first())
            assertEquals(ContextProfile.STUDY, prefs.focusUnlockProfile().first())
            prefs.clearFocusUnlock()
            assertEquals(0L, prefs.focusUnlockUntil().first())
            assertNull(prefs.focusUnlockProfile().first())
        } finally {
            scope.cancel()
        }
    }

    @Test fun `unlock length defaults to five minutes and stays in range`() = runBlocking {
        val (prefs, scope) = tempPrefs()
        try {
            assertEquals(5, prefs.focusUnlockMinutes().first())
            prefs.setFocusUnlockMinutes(15)
            assertEquals(15, prefs.focusUnlockMinutes().first())
            prefs.setFocusUnlockMinutes(10_000)
            assertEquals(60, prefs.focusUnlockMinutes().first())
            prefs.setFocusUnlockMinutes(-3)
            assertEquals(1, prefs.focusUnlockMinutes().first())
        } finally {
            scope.cancel()
        }
    }

    @Test fun `manual override round trips and clears`() = runBlocking {
        val (prefs, scope) = tempPrefs()
        try {
            assertNull(prefs.manualOverride.first())
            prefs.setManualOverride(ContextProfile.SLEEP, 12345L)
            assertEquals(ManualOverride(ContextProfile.SLEEP, 12345L), prefs.manualOverride.first())
            prefs.clearManualOverride()
            assertNull(prefs.manualOverride.first())
        } finally {
            scope.cancel()
        }
    }

    @Test fun `retain drops uninstalled keys but keeps the rest`() = runBlocking {
        val (prefs, scope) = tempPrefs()
        try {
            prefs.setDrawerHidden(ContextProfile.WORK, setOf(mail, game))
            prefs.setFocusRestricted(ContextProfile.WORK, setOf(game))
            prefs.retainContextKeys(ContextProfile.WORK, setOf(mail))
            assertEquals(setOf(mail), prefs.drawerHidden(ContextProfile.WORK).first())
            assertTrue(prefs.focusRestricted(ContextProfile.WORK).first().isEmpty())
        } finally {
            scope.cancel()
        }
    }
}
