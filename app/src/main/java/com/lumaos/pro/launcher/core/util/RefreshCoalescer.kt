package com.lumaos.pro.launcher.core.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Serializes refresh requests while preserving one trailing refresh.
 *
 * Package broadcasts and Activity resumes can arrive during an enumeration. A
 * plain "already running, return" drops those events and can leave the app list
 * stale until the next resume. This runner performs at most one refresh at a
 * time and coalesces any number of requests during that pass into one follow-up.
 */
internal class RefreshCoalescer(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val onLoadingChanged: (Boolean) -> Unit,
    private val operation: suspend () -> Unit,
    private val onFailure: (Exception) -> Unit = {}
) {
    private val lock = Any()
    private var requested = false
    private var job: Job? = null

    fun request() {
        synchronized(lock) {
            requested = true
            if (job?.isActive == true) return
            onLoadingChanged(true)
            job = scope.launch(dispatcher) { drain() }
        }
    }

    private suspend fun drain() {
        while (true) {
            val shouldRun = synchronized(lock) {
                if (requested) {
                    requested = false
                    true
                } else {
                    // Clear the job under the same lock used by request(). A
                    // request arriving at the end either queues a pass above or
                    // starts a new job after this one has fully claimed the idle state.
                    job = null
                    onLoadingChanged(false)
                    false
                }
            }
            if (!shouldRun) return

            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                onFailure(failure)
            }
        }
    }
}
