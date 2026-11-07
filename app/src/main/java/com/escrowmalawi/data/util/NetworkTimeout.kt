package com.escrowmalawi.data.util

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

const val NETWORK_TIMEOUT_MS = 15_000L

/**
 * Runs [block] and throws a readable IOException if it takes longer than [timeoutMs].
 * Use around Firebase .await() calls so the UI never hangs forever on a bad connection.
 */
suspend fun <T> withNetworkTimeout(
    timeoutMs: Long = NETWORK_TIMEOUT_MS,
    block: suspend () -> T
): T = try {
    withTimeout(timeoutMs) { block() }
} catch (e: TimeoutCancellationException) {
    throw IOException("Can't reach the server. Check your internet connection and try again.")
}
