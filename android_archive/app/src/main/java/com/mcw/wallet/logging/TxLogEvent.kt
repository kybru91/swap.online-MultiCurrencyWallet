package com.mcw.wallet.logging

/**
 * Transaction lifecycle statuses for structured logging.
 *
 * Tech-spec: "Crashlytics custom logs for tx lifecycle: only tx hash, status, chain"
 */
enum class TxStatus {
  CREATED,
  SIGNED,
  BROADCAST,
  CONFIRMED,
  FAILED,
}

/**
 * Structured transaction log event.
 *
 * Contains ONLY fields permitted by the secret-safe logging policy:
 * - tx hash
 * - status
 * - chain
 * - error message (for failed transactions)
 *
 * Intentionally excludes: amounts, addresses, signing data, private keys.
 */
data class TxLogEvent(
  val txHash: String,
  val status: TxStatus,
  val chain: String,
  val errorMessage: String? = null,
) {
  /**
   * Formats the event as a structured log string.
   * Only includes the safe fields defined in this data class.
   */
  fun toLogString(): String {
    val base = "TX [$chain] $txHash -> $status"
    return if (errorMessage != null) {
      "$base: $errorMessage"
    } else {
      base
    }
  }
}
