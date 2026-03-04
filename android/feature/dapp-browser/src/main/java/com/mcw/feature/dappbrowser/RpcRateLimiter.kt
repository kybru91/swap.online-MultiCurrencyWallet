package com.mcw.feature.dappbrowser

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Rate limiter for JS bridge RPC calls.
 *
 * Per tech-spec Decision 9: "Rate limit: max 10 bridge calls per second, queue excess."
 *
 * Uses a sliding window approach: tracks the count of calls within the current
 * 1-second window. Calls exceeding the limit are rejected (caller queues them).
 *
 * Thread-safe via atomic operations — multiple WebView JS callbacks may arrive
 * concurrently on different threads.
 *
 * @param maxCallsPerSecond maximum allowed calls per 1-second window (default: 10)
 */
class RpcRateLimiter(
  private val maxCallsPerSecond: Int = 10,
) {

  /** Start of the current 1-second window (epoch millis) */
  private val windowStart = AtomicLong(System.currentTimeMillis())

  /** Number of calls made in the current window */
  private val callCount = AtomicInteger(0)

  /**
   * Try to acquire a permit for an RPC call.
   *
   * @return true if the call is allowed, false if rate limit exceeded
   */
  fun tryAcquire(): Boolean {
    val now = System.currentTimeMillis()
    val currentWindowStart = windowStart.get()

    // Check if we're in a new 1-second window
    if (now - currentWindowStart >= 1000L) {
      // Reset the window — CAS to prevent race conditions
      if (windowStart.compareAndSet(currentWindowStart, now)) {
        callCount.set(0)
      }
    }

    // Try to increment the call count
    val currentCount = callCount.incrementAndGet()
    return currentCount <= maxCallsPerSecond
  }

  /**
   * Reset the rate limiter window for testing purposes.
   * Simulates the passage of time beyond the 1-second window.
   */
  fun resetForTesting() {
    // Set window start to 2 seconds ago so next call sees a new window
    windowStart.set(System.currentTimeMillis() - 2000L)
    callCount.set(0)
  }
}
