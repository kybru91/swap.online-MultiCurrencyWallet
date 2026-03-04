package com.mcw.wallet.ui.history

import com.mcw.core.network.TransactionRecord

/**
 * UI state for the Transaction History screen.
 *
 * Holds a flat list of TransactionRecord items fetched on-demand
 * (no persistent caching per Decision 8).
 *
 * @param transactions list of tx records from all chains, sorted by timestamp desc
 * @param isLoading true during a history fetch
 * @param error error message if fetch failed (offline mode), null when OK
 * @param expandedTxHash hash of the currently expanded tx for detail view, null if none
 */
data class HistoryUiState(
  val transactions: List<TransactionRecord> = emptyList(),
  val isLoading: Boolean = false,
  val error: String? = null,
  val expandedTxHash: String? = null,
)
