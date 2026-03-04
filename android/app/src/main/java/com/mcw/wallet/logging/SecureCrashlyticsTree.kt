package com.mcw.wallet.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Timber tree for release builds that sends logs to Firebase Crashlytics.
 *
 * Secret-safe logging policy (tech-spec):
 * - Strips DEBUG-level logs in release builds (only INFO, WARN, ERROR logged)
 * - Sanitizes all messages through [SecureLogSanitizer] to remove private keys,
 *   mnemonics, and password hashes before sending to Crashlytics
 * - Exceptions are recorded as non-fatal Crashlytics events
 *
 * Usage: Plant this tree in Application.onCreate() for release builds.
 * Debug builds should use Timber.DebugTree() instead (logcat only, no Crashlytics).
 */
class SecureCrashlyticsTree : Timber.Tree() {

  override fun isLoggable(tag: String?, priority: Int): Boolean {
    // Strip DEBUG and VERBOSE in release — only INFO, WARN, ERROR, ASSERT
    return priority >= Log.INFO
  }

  override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    val crashlytics = FirebaseCrashlytics.getInstance()

    // Sanitize message to remove any sensitive data
    val safeMessage = SecureLogSanitizer.sanitize(message) ?: return

    // Log to Crashlytics as custom log
    val priorityLabel = when (priority) {
      Log.INFO -> "I"
      Log.WARN -> "W"
      Log.ERROR -> "E"
      Log.ASSERT -> "A"
      else -> "?"
    }
    crashlytics.log("$priorityLabel/$tag: $safeMessage")

    // Record exceptions as non-fatal events
    if (t != null) {
      crashlytics.recordException(t)
    }
  }
}
