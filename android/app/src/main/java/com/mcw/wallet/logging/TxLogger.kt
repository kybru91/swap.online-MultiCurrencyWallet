package com.mcw.wallet.logging

import timber.log.Timber

/**
 * Transaction lifecycle logger.
 *
 * Provides structured logging for transaction events using only safe fields:
 * tx hash, status, chain, and error message.
 *
 * Tech-spec: "Crashlytics custom logs include only: tx hash, status, chain,
 * error message (no addresses, amounts, or signing data)."
 */
object TxLogger {

  private const val TAG = "TxLifecycle"

  /**
   * Log a transaction lifecycle event.
   *
   * @param event structured event with only safe fields
   */
  fun log(event: TxLogEvent) {
    val logString = event.toLogString()
    when (event.status) {
      TxStatus.FAILED -> Timber.tag(TAG).e(logString)
      TxStatus.BROADCAST -> Timber.tag(TAG).i(logString)
      else -> Timber.tag(TAG).d(logString)
    }
  }

  /** Convenience: log transaction created */
  fun created(txHash: String, chain: String) {
    log(TxLogEvent(txHash, TxStatus.CREATED, chain))
  }

  /** Convenience: log transaction signed */
  fun signed(txHash: String, chain: String) {
    log(TxLogEvent(txHash, TxStatus.SIGNED, chain))
  }

  /** Convenience: log transaction broadcast */
  fun broadcast(txHash: String, chain: String) {
    log(TxLogEvent(txHash, TxStatus.BROADCAST, chain))
  }

  /** Convenience: log transaction confirmed */
  fun confirmed(txHash: String, chain: String) {
    log(TxLogEvent(txHash, TxStatus.CONFIRMED, chain))
  }

  /** Convenience: log transaction failed */
  fun failed(txHash: String, chain: String, error: String) {
    log(TxLogEvent(txHash, TxStatus.FAILED, chain, error))
  }
}
