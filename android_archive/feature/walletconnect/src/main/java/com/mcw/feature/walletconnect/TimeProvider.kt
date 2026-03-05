package com.mcw.feature.walletconnect

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over system time for testability.
 *
 * Production code uses [SystemTimeProvider] which delegates to [System.currentTimeMillis].
 * Tests inject a fake implementation to control time for session expiry testing
 * without relying on Thread.sleep or flaky time-dependent assertions.
 */
interface TimeProvider {
    /** Returns the current time in milliseconds since epoch. */
    fun currentTimeMillis(): Long
}

/**
 * Production implementation that uses real system time.
 */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
