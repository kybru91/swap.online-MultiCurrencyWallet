package com.mcw.core.btc

/**
 * Thrown when transaction broadcast to the Bitcoin network fails.
 *
 * Wraps the underlying API error to provide a consistent exception type
 * for callers to catch.
 */
class BroadcastException(message: String, cause: Throwable? = null) : Exception(message, cause)
