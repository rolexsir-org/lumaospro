package com.lumaos.pro.launcher.core.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Disaster recovery: a preferences file DataStore cannot parse must not leave
 * the launcher permanently read-only (or crash write call sites). Reads fall
 * back to defaults and — via the production corruption handler — writes work
 * again afterwards.
 */
class PreferencesCorruptionRecoveryTest {

    @Test
    fun `corrupt preferences file recovers to defaults and accepts writes again`() = runBlocking {
        val dir = Files.createTempDirectory("luma-prefs-corrupt").toFile()
        val storeFile = File(dir, "test.preferences_pb")
        // Not a valid preferences proto: simulates an interrupted write / storage fault.
        storeFile.writeBytes(byteArrayOf(0x0A, 0x7F, 0x00, 0x13, 0x37, 0x55, 0x66, 0x7F, 0x01, 0x02))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val prefs = LumaPrefs(
                PreferenceDataStoreFactory.create(
                    corruptionHandler = lumaosPrefsCorruptionHandler,
                    scope = scope
                ) { storeFile }
            )
            // Read path: defaults, no exception.
            assertEquals(LumaPrefs.DEFAULT_GRID_COLUMNS, prefs.gridColumns.first())
            // Write path: previously threw CorruptionException on every edit.
            prefs.setGridColumns(6)
            assertEquals(6, prefs.gridColumns.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `healthy preferences file is never reset by the corruption handler`() = runBlocking {
        val dir = Files.createTempDirectory("luma-prefs-healthy").toFile()
        val storeFile = File(dir, "test.preferences_pb")
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs1 = LumaPrefs(
            PreferenceDataStoreFactory.create(corruptionHandler = lumaosPrefsCorruptionHandler, scope = scope1) { storeFile }
        )
        prefs1.setGridColumns(7)
        scope1.cancel()

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val prefs2 = LumaPrefs(
                PreferenceDataStoreFactory.create(corruptionHandler = lumaosPrefsCorruptionHandler, scope = scope2) { storeFile }
            )
            assertEquals(7, prefs2.gridColumns.first())
        } finally {
            scope2.cancel()
        }
    }
}
