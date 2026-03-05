package com.mcw.wallet.debug

import android.os.SystemClock
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Lightweight performance tracker.
 * Records operation timings in a ring buffer (last 200 entries).
 * Thread-safe, no persistence — in-memory only.
 *
 * Usage:
 *   val token = PerformanceTracker.start("btc_balance_fetch")
 *   // ... do work ...
 *   PerformanceTracker.end(token)
 */
object PerformanceTracker {

    private const val MAX_ENTRIES = 200

    private val entries = ConcurrentLinkedDeque<PerfEntry>()

    data class PerfEntry(
        val operation: String,
        val durationMs: Long,
        val timestampMs: Long = System.currentTimeMillis(),
        val success: Boolean = true,
        val meta: String? = null,
    )

    data class Token(
        val operation: String,
        val startElapsed: Long = SystemClock.elapsedRealtime(),
    )

    /** Start timing an operation. Returns a token to pass to [end]. */
    fun start(operation: String): Token = Token(operation)

    /** End timing and record the result. */
    fun end(token: Token, success: Boolean = true, meta: String? = null) {
        val durationMs = SystemClock.elapsedRealtime() - token.startElapsed
        record(token.operation, durationMs, success, meta)
    }

    /** Record a completed operation with known duration. */
    fun record(operation: String, durationMs: Long, success: Boolean = true, meta: String? = null) {
        entries.addLast(PerfEntry(operation, durationMs, System.currentTimeMillis(), success, meta))
        // Trim to max size
        while (entries.size > MAX_ENTRIES) entries.pollFirst()
    }

    /** Get all recorded entries, newest first. */
    fun getAll(): List<PerfEntry> = entries.toList().reversed()

    /** Get summary stats per operation. */
    fun getSummary(): Map<String, PerfSummary> {
        val grouped = entries.groupBy { it.operation }
        return grouped.mapValues { (_, ops) ->
            val durations = ops.map { it.durationMs }
            PerfSummary(
                operation = ops.first().operation,
                count = ops.size,
                avgMs = durations.average().toLong(),
                minMs = durations.min(),
                maxMs = durations.max(),
                successRate = ops.count { it.success }.toFloat() / ops.size,
            )
        }
    }

    /** Clear all entries. */
    fun clear() = entries.clear()

    data class PerfSummary(
        val operation: String,
        val count: Int,
        val avgMs: Long,
        val minMs: Long,
        val maxMs: Long,
        val successRate: Float,
    )
}

/** Convenience inline: track block execution time. */
suspend inline fun <T> trackPerf(operation: String, block: suspend () -> T): T {
    val token = PerformanceTracker.start(operation)
    return try {
        val result = block()
        PerformanceTracker.end(token, success = true)
        result
    } catch (e: Exception) {
        PerformanceTracker.end(token, success = false, meta = e.message)
        throw e
    }
}
