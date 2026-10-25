package com.lumaos.pro.launcher.core.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshCoalescerTest {

    @Test
    fun `requests during a refresh produce one trailing pass`() = runTest {
        val loading = mutableListOf<Boolean>()
        val releaseFirstPass = CompletableDeferred<Unit>()
        var runs = 0
        val refresh = RefreshCoalescer(
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            onLoadingChanged = { loading.add(it) },
            operation = {
                runs++
                if (runs == 1) releaseFirstPass.await()
            }
        )

        refresh.request()
        runCurrent()
        assertEquals(1, runs)

        // A resume and a package callback can race the same enumeration. They
        // need one new scan, not zero scans and not an unbounded queue.
        refresh.request()
        refresh.request()
        releaseFirstPass.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, runs)
        assertEquals(listOf(true, false), loading)
    }

    @Test
    fun `a failed refresh clears loading and a later request can retry`() = runTest {
        val loading = mutableListOf<Boolean>()
        val failures = mutableListOf<Exception>()
        var runs = 0
        val refresh = RefreshCoalescer(
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            onLoadingChanged = { loading.add(it) },
            operation = {
                runs++
                if (runs == 1) error("temporary failure")
            },
            onFailure = { failures.add(it) }
        )

        refresh.request()
        advanceUntilIdle()
        refresh.request()
        advanceUntilIdle()

        assertEquals(2, runs)
        assertEquals(1, failures.size)
        assertEquals("temporary failure", failures.single().message)
        assertEquals(listOf(true, false, true, false), loading)
    }
}
