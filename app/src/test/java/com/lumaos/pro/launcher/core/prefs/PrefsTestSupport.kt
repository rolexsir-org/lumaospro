package com.lumaos.pro.launcher.core.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

/**
 * Shared harness for the preference persistence tests: a throwaway directory
 * and a real [LumaPrefs] over a real DataStore file, with the store's scope
 * always cancelled and joined before the directory goes away (DataStore
 * refuses two live instances for one file).
 */
internal fun withDir(prefix: String = "lumaos-prefs", block: suspend (File) -> Unit) = runBlocking {
    val dir = Files.createTempDirectory(prefix).toFile()
    try {
        block(dir)
    } finally {
        dir.deleteRecursively()
    }
}

internal suspend fun <T> withStore(dir: File, block: suspend (LumaPrefs) -> T): T {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        val prefs = LumaPrefs(
            PreferenceDataStoreFactory.create(
                corruptionHandler = lumaosPrefsCorruptionHandler,
                scope = scope
            ) { File(dir, "test.preferences_pb") }
        )
        return block(prefs)
    } finally {
        scope.coroutineContext[Job]!!.cancelAndJoin()
    }
}
