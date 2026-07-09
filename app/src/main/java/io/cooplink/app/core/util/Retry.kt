package io.cooplink.app.core.util

import kotlinx.coroutines.delay

/**
 * Retries [block] up to [times] attempts with [delayMs] between attempts,
 * rethrowing the last failure if every attempt fails. Meant for transient
 * network failures (timeouts) on unreliable connections — not a substitute
 * for handling genuine, non-transient errors (auth, bad request, etc.).
 */
suspend fun <T> retrying(times: Int = 3, delayMs: Long = 1_000L, block: suspend () -> T): T {
    var lastError: Throwable? = null
    repeat(times) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastError = e
            if (attempt < times - 1) delay(delayMs)
        }
    }
    throw lastError!!
}
