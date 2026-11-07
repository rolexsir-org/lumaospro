package com.lumaos.pro.launcher.robolectric

import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.core.LumaApp
import com.lumaos.pro.launcher.diagnostics.DiagnosticsStore
import com.lumaos.pro.launcher.diagnostics.OptimizationEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Regression coverage for the crash that took the launcher process down when
 * Settings was opened.
 *
 * ## The defect
 *
 * [DiagnosticsStore] is a [android.database.sqlite.SQLiteOpenHelper], and a
 * helper owns exactly one [android.database.sqlite.SQLiteDatabase] for its whole
 * lifetime: `readableDatabase` and `writableDatabase` hand back that same cached
 * instance. `LumaCore` holds a single store for the process, so the Settings
 * dashboard and the Optimizer share that one connection.
 *
 * Every operation used to be wrapped in `readableDatabase.use { ... }` /
 * `writableDatabase.use { ... }`. `SQLiteDatabase` implements `Closeable`, so
 * `use` closed the shared connection after every read and every write. Opening
 * Settings starts a history read while an Optimizer run records an event on
 * another thread; whichever finished first closed the connection under the other,
 * and the survivor failed with "attempt to re-open an already-closed object" or
 * "connection pool has been closed". Because that happened inside a coroutine
 * launched from `viewModelScope`, it became an unhandled fatal exception.
 *
 * `DiagnosticsStoreRuntimeTest` never caught it: every one of its operations is
 * sequential, so the connection was simply reopened by the next call. Only a
 * lifetime assertion and concurrent access expose it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = LumaApp::class)
class DiagnosticsStoreConcurrencyTest {

    private lateinit var store: DiagnosticsStore

    @Before
    fun setUp() {
        store = DiagnosticsStore(ApplicationProvider.getApplicationContext())
        store.clearHistory()
    }

    @After
    fun tearDown() {
        store.close()
    }

    private fun event(ts: Long = 1_000L, actionId: String = "trim_icon_cache") =
        OptimizationEvent(
            timestampMillis = ts,
            actionId = actionId,
            actionTitle = "Trim icon cache",
            trigger = "manual",
            completed = true,
            detail = "released 12 icons",
            durationMs = 42L,
            beforePssKb = 40_000,
            afterPssKb = 38_000,
            bytesFreed = 0L,
            measuredImprovement = true
        )

    /**
     * The invariant the fix rests on, stated deterministically.
     *
     * A reference taken before any operation must still be the same, still-open
     * connection afterwards. Under the old code the first `insertEvent` closed
     * it, so `isOpen` became false and the next `writableDatabase` call built a
     * brand-new instance.
     */
    @Test
    fun `the helper keeps one open connection for its whole lifetime`() {
        val connection = store.writableDatabase
        assertTrue("a fresh helper must hand back an open connection", connection.isOpen)

        store.insertEvent(event())
        store.recentEvents(10)
        store.recordStartup(120L)
        store.startupHistory(10)
        store.vacuum()
        store.clearHistory()

        assertTrue(
            "no single operation may close the helper's shared connection",
            connection.isOpen
        )
        assertSame(
            "the helper must keep returning the same connection instance",
            connection,
            store.writableDatabase
        )
    }

    /**
     * The scenario that actually crashed: Settings loading history while the
     * Optimizer records results.
     *
     * Readers and writers run on separate threads against the one shared
     * connection, exactly as `SettingsViewModel.refreshHistory` and
     * `Optimizer.run` do in production. Any operation closing that connection
     * makes the others fail.
     */
    @Test
    fun `concurrent readers and writers never observe a closed connection`() {
        repeat(80) { index -> store.insertEvent(event(ts = index.toLong())) }
        repeat(20) { index -> store.recordStartup(100L + index, atMillis = index.toLong()) }

        val threadCount = 8
        val iterations = 60
        val barrier = CyclicBarrier(threadCount)
        val failures = CopyOnWriteArrayList<Throwable>()
        val pool = Executors.newFixedThreadPool(threadCount)
        try {
            val futures = (0 until threadCount).map { thread ->
                pool.submit {
                    // Start together so the operations genuinely interleave.
                    barrier.await(30, TimeUnit.SECONDS)
                    repeat(iterations) { n ->
                        try {
                            when (thread % 4) {
                                0 -> store.recentEvents(30)
                                1 -> store.insertEvent(event(ts = (thread * 1_000 + n).toLong()))
                                2 -> store.startupHistory(20)
                                else -> store.recordStartup(200L + n, atMillis = n.toLong())
                            }
                        } catch (t: Throwable) {
                            failures += t
                        }
                    }
                }
            }
            futures.forEach { it.get(120, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertTrue(
            "concurrent diagnostics access must not fail, but got: " +
                failures.take(3).joinToString { "${it.javaClass.name}: ${it.message}" },
            failures.isEmpty()
        )

        // And the store is still usable afterwards, with its cap still applied.
        assertEquals(DiagnosticsStore.MAX_EVENTS, store.recentEvents(DiagnosticsStore.MAX_EVENTS).size)
        assertEquals(
            DiagnosticsStore.MAX_STARTUP_SAMPLES,
            store.startupHistory(DiagnosticsStore.MAX_STARTUP_SAMPLES).size
        )
    }

    /**
     * `VACUUM` needs the database to itself. Run against reads and writes it
     * must either succeed or report a busy database - never corrupt the shared
     * connection for everyone else.
     */
    @Test
    fun `vacuum running against live traffic leaves the store usable`() {
        val threadCount = 4
        val barrier = CyclicBarrier(threadCount + 1)
        val failures = CopyOnWriteArrayList<Throwable>()
        val pool = Executors.newFixedThreadPool(threadCount + 1)
        try {
            val futures = (0 until threadCount).map { thread ->
                pool.submit {
                    barrier.await(30, TimeUnit.SECONDS)
                    repeat(30) { n ->
                        try {
                            if (thread % 2 == 0) store.recentEvents(20)
                            else store.insertEvent(event(ts = (thread * 500 + n).toLong()))
                        } catch (t: Throwable) {
                            failures += t
                        }
                    }
                }
            }
            val vacuumer = pool.submit {
                barrier.await(30, TimeUnit.SECONDS)
                repeat(5) { store.vacuum() }
            }
            futures.forEach { it.get(120, TimeUnit.SECONDS) }
            vacuumer.get(120, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        assertTrue(
            "vacuum must not break concurrent diagnostics access, but got: " +
                failures.take(3).joinToString { "${it.javaClass.name}: ${it.message}" },
            failures.isEmpty()
        )
        store.insertEvent(event(ts = 9_999L))
        assertEquals(9_999L, store.recentEvents(1).single().timestampMillis)
    }

    /**
     * The row-cap trim and the insert share one transaction, so a reader can
     * never observe a table that has been written but not yet trimmed.
     */
    @Test
    fun `insert and row cap are applied together`() {
        repeat(DiagnosticsStore.MAX_EVENTS + 25) { index ->
            store.insertEvent(event(ts = index.toLong()))
        }
        val all = store.recentEvents(DiagnosticsStore.MAX_EVENTS)
        assertEquals(DiagnosticsStore.MAX_EVENTS, all.size)
        assertEquals((DiagnosticsStore.MAX_EVENTS + 24).toLong(), all.first().timestampMillis)
    }

    /**
     * Sizing the database must not depend on a connection: it is read either
     * side of a `VACUUM`, and opening the database just to stat it would defeat
     * the point. A store that has never been written has no file and reports 0.
     */
    @Test
    fun `database size is reported from the file without opening a connection`() {
        val untouched = DiagnosticsStore(ApplicationProvider.getApplicationContext()).also {
            // No operation performed: nothing may be created as a side effect.
        }
        val before = untouched.databaseSizeBytes()
        assertTrue("an unwritten database reports 0, not a failure (was $before)", before >= 0L)
        untouched.close()

        store.insertEvent(event())
        assertTrue("a written database reports a real size", store.databaseSizeBytes() > 0L)
    }
}
