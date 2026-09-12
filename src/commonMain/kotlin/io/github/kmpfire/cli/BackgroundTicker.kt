package io.github.kmpfire.cli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Runs [block] on [Dispatchers.Default] while [onTick] runs on the caller thread.
 *
 * Terminal output must happen on the caller thread — Native / Mordant do not reliably
 * flush spinner frames drawn from a background worker.
 */
object BackgroundTicker {
    fun <T> whileRunning(
        intervalMs: Long,
        onTick: () -> Unit,
        block: () -> T,
    ): T = runBlocking {
        val deferred = async(Dispatchers.Default) {
            runCatching { block() }
        }
        while (deferred.isActive) {
            onTick()
            delay(intervalMs)
        }
        deferred.await().getOrThrow()
    }
}
