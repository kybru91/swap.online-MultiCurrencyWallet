package com.mcw.wallet

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mcw.wallet.debug.DebugLogTree
import com.mcw.wallet.logging.SecureCrashlyticsTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application class for MCW Wallet.
 *
 * Initializes:
 * - Timber logging with build-type-appropriate tree
 * - Firebase Crashlytics (disabled in debug for local development)
 *
 * Secret-safe logging policy:
 * - Debug builds: Timber.DebugTree (logcat only, full DEBUG output)
 * - Release builds: SecureCrashlyticsTree (INFO+ only, sanitized, sent to Crashlytics)
 */
@HiltAndroidApp
class MCWalletApplication : Application() {

  companion object {
    /** In-memory log tree for debug report export. Null in release builds. */
    var debugLogTree: DebugLogTree? = null
      private set
  }

  override fun onCreate() {
    super.onCreate()
    initLogging()
  }

  private fun initLogging() {
    if (BuildConfig.DEBUG) {
      // Debug: full logcat output + in-memory buffer for debug reports
      val logTree = DebugLogTree()
      debugLogTree = logTree
      Timber.plant(Timber.DebugTree(), logTree)
      // Disable Crashlytics in debug builds to avoid noise
      FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
    } else {
      // Release: sanitized logs to Crashlytics, strip DEBUG level
      Timber.plant(SecureCrashlyticsTree())
      FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
    }
  }
}
