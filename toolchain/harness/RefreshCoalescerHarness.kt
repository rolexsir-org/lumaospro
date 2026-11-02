/*
 * Executable harness for the REAL com.lumaos.pro.launcher.core.util.RefreshCoalescer.
 *
 * This file is compiled in the same kotlinc module as
 * app/src/main/java/com/lumaos/pro/launcher/core/util/RefreshCoalescer.kt so the
 * `internal` class is visible, and it is executed on a real JVM. Nothing about the
 * production class is re-implemented, stubbed or replaced here: only the
 * CoroutineScope/CoroutineDispatcher it is handed are supplied, because the project's
 * kotlinx-coroutines-test artifact cannot be downloaded in this environment.
 *
 * Coverage maps to the required properties:
 *   repeat taps / concurrency / mid-work arrival -> coalescing + no lost refreshes
 *   cancellation / failure+retry / lifecycle termination
 *   no uncontrolled background work
 */
@file:Suppress("PackageDirectoryMismatch")

package com.lumaos.pro.launcher.core.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// ---------------------------------------------------------------- assertions

private var passed = 0
private val failures = mutableListOf<String>()

private fun check(name: String, condition: Boolean, detail: () -> String) {
    if (condition) {
        passed++
        println("  PASS  $name")
    } else {
        failures += "$name -> ${detail()}"
        println("  FAIL  $name -> ${detail()}")
    }
}

private fun eq(name: String, expected: Any?, actual: Any?) =
    check(name, expected == actual) { "expected <$expected> but was <$actual>" }

/** Waits for [latch] without ever sleeping on a fixed guess. */
private fun await(latch: CountDownLatch, what: String, millis: Long = 5_000) {
    if (!latch.await(millis, TimeUnit.MILLISECONDS)) {
        throw AssertionError("timed out after ${millis}ms waiting for $what")
    }
}

// ---------------------------------------------------------------- test rig

/**
 * A harness around the production coalescer.
 *
 * `passes` counts completed invocations of the real operation, `overlaps` records
 * whether two passes were ever inside the operation at once, and `started` counts
 * pass entries so an in-flight pass can be observed before it finishes.
 */
private class Rig(
    scope: CoroutineScope,
    dispatcher: ExecutorCoroutineDispatcher,
    failOnPass: Set<Int> = emptySet(),
    failureMessage: String = "temporary failure",
    gate: CompletableDeferred<Unit>? = null,
    gatePasses: Set<Int> = setOf(1),
    gateFor: ((Int) -> CompletableDeferred<Unit>?)? = null
) {
    val passes = AtomicInteger()
    val started = AtomicInteger()
    val overlaps = AtomicInteger()
    val loadingTransitions = ConcurrentLinkedQueue<Boolean>()
    val failuresReported = ConcurrentLinkedQueue<Exception>()
    val cancellationsSeen = AtomicInteger()
    private val inside = AtomicBoolean()

    val coalescer = RefreshCoalescer(
        scope = scope,
        dispatcher = dispatcher,
        onLoadingChanged = { loadingTransitions.add(it) },
        operation = {
            val n = started.incrementAndGet()
            if (!inside.compareAndSet(false, true)) overlaps.incrementAndGet()
            try {
                val hold = gateFor?.invoke(n) ?: if (n in gatePasses) gate else null
                hold?.await()
                if (n in failOnPass) error(failureMessage)
            } catch (cancelled: CancellationException) {
                cancellationsSeen.incrementAndGet()
                throw cancelled
            } finally {
                inside.set(false)
            }
            passes.incrementAndGet()
        },
        onFailure = { failuresReported.add(it) }
    )

    /** Blocks until pass [n] has entered the operation. */
    fun awaitPassStarted(n: Int, what: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (started.get() < n) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.yield()
        }
    }

    /**
     * Blocks until the coalescer reports idle.
     *
     * `onLoadingChanged(false)` is emitted from inside the lock at the moment the
     * coalescer drops its job, so it is the idle signal. Comparing started to passes
     * would not be: a pass that throws never increments `passes`, yet the coalescer
     * is still correctly idle afterwards.
     */
    fun awaitQuiescent(what: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (true) {
            val idle = loadingTransitions.lastOrNull() == false
            if (idle) return
            if (System.nanoTime() > deadline) {
                throw AssertionError(
                    "timed out waiting for $what (started=${started.get()} passes=${passes.get()} " +
                        "loading=${loadingTransitions.toList()})"
                )
            }
            Thread.yield()
        }
    }

    fun loading(): List<Boolean> = loadingTransitions.toList()
}

private fun singleThread(): ExecutorCoroutineDispatcher =
    Executors.newSingleThreadExecutor { r -> Thread(r, "coalescer-under-test").apply { isDaemon = true } }
        .asCoroutineDispatcher()

// ---------------------------------------------------------------- the cases

fun main() {
    println("RefreshCoalescer harness -- real production class, JVM execution")
    println("================================================================")

    // 1. One request performs exactly one pass.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val rig = Rig(scope, d)
        rig.coalescer.request()
        rig.awaitQuiescent("case 1")
        eq("01 single request runs exactly one pass", 1, rig.passes.get())
        eq("01 loading ends false", listOf(true, false), rig.loading())
        scope.cancel()
    }

    // 2. Ten requests queued before the first pass starts collapse to one pass.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val gate = CompletableDeferred<Unit>()
        val rig = Rig(scope, d, gate = gate)
        repeat(10) { rig.coalescer.request() }
        rig.awaitPassStarted(1, "case 2 first pass")
        gate.complete(Unit)
        rig.awaitQuiescent("case 2")
        // One pass consumes the queued requests; at most one trailing pass follows.
        check("02 ten pre-flight requests never fan out to ten passes", rig.passes.get() <= 2) {
            "ran ${rig.passes.get()} passes"
        }
        scope.cancel()
    }

    // 3. Requests arriving mid-work are preserved as exactly one trailing pass.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val gate = CompletableDeferred<Unit>()
        val rig = Rig(scope, d, gate = gate)
        rig.coalescer.request()
        rig.awaitPassStarted(1, "case 3 first pass")
        rig.coalescer.request()
        rig.coalescer.request()
        gate.complete(Unit)
        rig.awaitQuiescent("case 3")
        eq("03 mid-work requests produce one trailing pass (2 total)", 2, rig.passes.get())
        eq("03 loading is one true/false pair for the whole burst", listOf(true, false), rig.loading())
        eq("03 nothing failed", 0, rig.failuresReported.size)
        scope.cancel()
    }

    // 4. Twenty requests during a pass cost exactly two passes, never more.
    //    Every pass is gated, so this count is exact rather than timing-dependent.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val gates = (1..8).associateWith { CompletableDeferred<Unit>() }
        val rig = Rig(scope, d, gateFor = { gates[it] })
        rig.coalescer.request()
        rig.awaitPassStarted(1, "case 4 first pass")
        repeat(20) { rig.coalescer.request() }
        gates.getValue(1).complete(Unit)
        rig.awaitPassStarted(2, "case 4 trailing pass")
        gates.getValue(2).complete(Unit)
        rig.awaitQuiescent("case 4")
        eq("04 twenty mid-work requests cost exactly two passes", 2, rig.passes.get())
        println("        [measured] 21 requests, all passes gated -> ${rig.passes.get()} passes")
        // While a trailing pass is itself in flight, a further twenty requests still
        // queue exactly one more pass -- the pending queue holds one entry, not one
        // per request. Three bursts of requests therefore cost three passes total.
        val rig2 = Rig(scope, d, gateFor = { gates[it + 2] })
        rig2.coalescer.request()
        rig2.awaitPassStarted(1, "case 4b first pass")
        repeat(20) { rig2.coalescer.request() }
        gates.getValue(3).complete(Unit)
        rig2.awaitPassStarted(2, "case 4b second pass")
        repeat(20) { rig2.coalescer.request() }
        gates.getValue(4).complete(Unit)
        rig2.awaitPassStarted(3, "case 4b third pass")
        gates.getValue(5).complete(Unit)
        rig2.awaitQuiescent("case 4b")
        eq("04 three request bursts cost three passes, not forty-one", 3, rig2.passes.get())
        println("        [measured] 41 requests across three gated passes -> ${rig2.passes.get()} passes")
        scope.cancel()
    }

    // 5. Concurrency: 20 requests from 8 threads never run two passes at once.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val gate = CompletableDeferred<Unit>()
        val rig = Rig(scope, d, gate = gate)
        val threads = (1..8).map {
            Thread {
                repeat(3) { rig.coalescer.request() }
            }.apply { isDaemon = true }
        }
        rig.coalescer.request()
        rig.awaitPassStarted(1, "case 5 first pass")
        threads.forEach { it.start() }
        threads.forEach { it.join(5_000) }
        gate.complete(Unit)
        rig.awaitQuiescent("case 5")
        eq("05 no two passes ever overlap (serialization invariant)", 0, rig.overlaps.get())
        check("05 concurrent burst stays within three passes", rig.passes.get() <= 3) {
            "ran ${rig.passes.get()} passes"
        }
        println("        [measured] 25 requests from 8 threads -> ${rig.passes.get()} passes, ${rig.overlaps.get()} overlaps")
        scope.cancel()
    }

    // 6. No sticky state: a request after quiescence starts a fresh pass.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val rig = Rig(scope, d)
        rig.coalescer.request()
        rig.awaitQuiescent("case 6 first")
        rig.coalescer.request()
        rig.awaitQuiescent("case 6 second")
        rig.coalescer.request()
        rig.awaitQuiescent("case 6 third")
        eq("06 three separated requests run three passes", 3, rig.passes.get())
        eq("06 each separated request gets its own loading pair",
            listOf(true, false, true, false, true, false), rig.loading())
        scope.cancel()
    }

    // 7. Repeated request/complete cycles map one-to-one onto passes.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val rig = Rig(scope, d)
        repeat(50) { rig.coalescer.request(); rig.awaitQuiescent("case 7 cycle $it") }
        eq("07 fifty serial cycles run fifty passes", 50, rig.passes.get())
        eq("07 fifty serial cycles report fifty loading pairs", 100, rig.loading().size)
        scope.cancel()
    }

    // 8. A failing operation reports the failure and clears loading.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val rig = Rig(scope, d, failOnPass = setOf(1))
        rig.coalescer.request()
        rig.awaitQuiescent("case 8")
        eq("08 failure reported exactly once", 1, rig.failuresReported.size)
        eq("08 failure message preserved", "temporary failure", rig.failuresReported.single().message)
        eq("08 loading cleared after failure", listOf(true, false), rig.loading())
        eq("08 a failed pass is not counted as completed", 0, rig.passes.get())
        scope.cancel()
    }

    // 9. A retry after a failure runs a second pass.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val rig = Rig(scope, d, failOnPass = setOf(1))
        rig.coalescer.request()
        rig.awaitQuiescent("case 9 first")
        rig.coalescer.request()
        rig.awaitQuiescent("case 9 retry")
        eq("09 retry after failure runs a second pass", 1, rig.passes.get())
        eq("09 only the first attempt failed", 1, rig.failuresReported.size)
        eq("09 retry loading pair is emitted",
            listOf(true, false, true, false), rig.loading())
        scope.cancel()
    }

    // 10. A request landing during a failing pass still gets its trailing pass.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val gate = CompletableDeferred<Unit>()
        val rig = Rig(scope, d, failOnPass = setOf(1), gate = gate)
        rig.coalescer.request()
        rig.awaitPassStarted(1, "case 10 first pass")
        rig.coalescer.request()
        gate.complete(Unit)
        rig.awaitQuiescent("case 10")
        eq("10 a request during a failed pass is still served", 1, rig.passes.get())
        eq("10 exactly one failure reported", 1, rig.failuresReported.size)
        scope.cancel()
    }

    // 11. Sustained failures do not wedge the coalescer.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val rig = Rig(scope, d, failOnPass = setOf(1, 2, 3, 4, 5))
        repeat(6) { rig.coalescer.request(); rig.awaitQuiescent("case 11 attempt $it") }
        eq("11 five consecutive failures then success", 1, rig.passes.get())
        eq("11 every failure reported", 5, rig.failuresReported.size)
        eq("11 loading balanced across six attempts", 12, rig.loading().size)
        eq("11 loading ends false", false, rig.loading().last())
        scope.cancel()
    }

    // 12. Cancellation propagates to the operation and is not reported as failure.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val gate = CompletableDeferred<Unit>()
        val rig = Rig(scope, d, gate = gate)
        rig.coalescer.request()
        rig.awaitPassStarted(1, "case 12 first pass")
        scope.cancel()
        val settled = CountDownLatch(1)
        scope.coroutineContext[kotlinx.coroutines.Job]!!.invokeOnCompletion { settled.countDown() }
        await(settled, "case 12 job completion")
        eq("12 CancellationException reached the operation", 1, rig.cancellationsSeen.get())
        eq("12 cancellation never surfaces as a refresh failure", 0, rig.failuresReported.size)
        eq("12 a cancelled pass is not counted as completed", 0, rig.passes.get())
    }

    // 13. After lifecycle termination no further work is started.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val rig = Rig(scope, d)
        rig.coalescer.request()
        rig.awaitQuiescent("case 13 first")
        scope.cancel()
        repeat(10) { rig.coalescer.request() }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(750)
        while (System.nanoTime() < deadline) Thread.yield()
        eq("13 requests after cancellation start no new work", 1, rig.started.get())
        check("13 the scope is really cancelled", !scope.isActive) { "scope still active" }
    }

    // 14. A trailing request can never be lost under sustained load.
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val gate = CompletableDeferred<Unit>()
        val rig = Rig(scope, d, gate = gate)
        val workers = (1..16).map {
            Thread { repeat(15) { rig.coalescer.request() } }.apply { isDaemon = true }
        }
        rig.coalescer.request()
        rig.awaitPassStarted(1, "case 14 first pass")
        workers.forEach { it.start() }
        gate.complete(Unit)
        workers.forEach { it.join(10_000) }
        // 241 requests total. The exact pass count here is timing-dependent, because a
        // request may land during each successive pass; what the class guarantees is
        // that only one pass runs at a time and that the pending queue never holds more
        // than one entry. So assert the invariants, not a pass count.
        val total = rig.passes.get()
        eq("14 no pass overlap across 241 requests from 16 threads", 0, rig.overlaps.get())
        check("14 241 requests coalesce by at least 10x", total * 10 <= 241) {
            "ran $total passes for 241 requests (ratio ${(241.0 / total)})"
        }
        println("        [measured] 241 requests from 16 threads -> $total passes " +
            "(ratio ${(241.0 / total).toInt()}x), ${rig.overlaps.get()} overlaps")
        rig.awaitQuiescent("case 14 burst")
        val before = rig.passes.get()
        rig.coalescer.request()
        rig.awaitQuiescent("case 14 sentinel")
        eq("14 a post-burst request is still served (no lost refresh)", before + 1, rig.passes.get())
        eq("14 no pass overlap under load", 0, rig.overlaps.get())
        scope.cancel()
    }

    // 15. The dispatcher handed in is the one that runs the work (no hidden thread).
    singleThread().use { d ->
        val scope = CoroutineScope(SupervisorJob() + d)
        val threadNames = ConcurrentLinkedQueue<String>()
        val rig = RefreshCoalescer(
            scope = scope,
            dispatcher = d,
            onLoadingChanged = {},
            operation = { threadNames.add(Thread.currentThread().name) }
        )
        repeat(5) { rig.request() }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (threadNames.size < 2 && System.nanoTime() < deadline) Thread.yield()
        check("15 all work runs on the supplied dispatcher", threadNames.all { it == "coalescer-under-test" }) {
            "saw threads ${threadNames.toSet()}"
        }
        scope.cancel()
    }

    println("================================================================")
    println("passed=$passed failed=${failures.size}")
    failures.forEach { println("  $it") }
    if (failures.isNotEmpty()) kotlin.system.exitProcess(1)
}

private inline fun <T : ExecutorCoroutineDispatcher> T.use(block: (T) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}
