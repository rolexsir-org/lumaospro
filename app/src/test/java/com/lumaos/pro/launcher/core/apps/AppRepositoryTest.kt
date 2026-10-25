package com.lumaos.pro.launcher.core.apps

import android.content.Context
import android.content.pm.LauncherActivityInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppRepositoryTest {

    @Test
    fun `a system query failure is distinct from a successful empty app list`() {
        var attempts = 0
        val repository = AppRepository(
            ApplicationProvider.getApplicationContext<Context>(),
            activityListQuery = {
                attempts++
                if (attempts == 1) emptyList<LauncherActivityInfo>()
                else throw IllegalStateException("temporary LauncherApps failure")
            }
        )

        val emptyButComplete = repository.refreshSnapshot()
        val failed = repository.refreshSnapshot()

        assertTrue(emptyButComplete.successful)
        assertFalse(failed.successful)
        assertEquals(emptyButComplete.apps, failed.apps)
    }
}
