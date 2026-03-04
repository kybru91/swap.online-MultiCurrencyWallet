package com.mcw.core.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over system clock for deterministic testing.
 * Production implementation uses [System.currentTimeMillis].
 */
interface TimeProvider {
    fun currentTimeMillis(): Long
}

/**
 * Production [TimeProvider] backed by the real system clock.
 * Injectable via Hilt as @Singleton.
 */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
