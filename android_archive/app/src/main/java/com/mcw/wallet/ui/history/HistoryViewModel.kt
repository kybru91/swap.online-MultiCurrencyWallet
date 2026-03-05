package com.mcw.wallet.ui.history

import com.mcw.core.btc.BtcManager
import com.mcw.core.evm.EvmManager
import com.mcw.core.network.TransactionRecord
import com.mcw.core.network.api.EtherscanApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Transaction History screen.
 *
 * Fetches transaction history from Blockcypher (BTC) and Etherscan (EVM)
 * APIs, merges and sorts by timestamp descending, and exposes state
 * via StateFlow for the Compose UI.
 *
 * No persistent caching (Decision 8): history is fetched on every
 * screen open and pull-to-refresh.
 *
 * This ViewModel is a plain class (not @HiltViewModel) following the
 * same pattern as WalletViewModel and SendViewModel — CryptoManager/BtcManager
 * lack @Inject constructors with injectable params. Will be wired
 * via assisted injection in the integration phase.
 */
class HistoryViewModel(
  private val btcManager: BtcManager?,
  private val evmManager: EvmManager?,
  private val btcAddress: String,
  private val evmAddress: String,
  private val evmChains: List<EvmChainHistoryConfig> = emptyList(),
) {

  private val _uiState = MutableStateFlow(HistoryUiState())
  val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

  /**
   * Loads transaction history from all configured chains.
   *
   * Fetches BTC history from Blockcypher and EVM history from Etherscan
   * in parallel, merges results, and sorts by timestamp descending.
   *
   * On error: retains previous transactions, shows error message.
   * On success: replaces all transactions with fresh data.
   */
  suspend fun loadHistory() {
    _uiState.value = _uiState.value.copy(isLoading = true, error = null)

    val allTransactions = mutableListOf<TransactionRecord>()
    var hasError = false

    // Fetch BTC history
    if (btcManager != null && btcAddress.isNotBlank()) {
      val btcHistory = btcManager.fetchTransactionHistory(btcAddress)
      if (btcHistory != null) {
        allTransactions.addAll(btcHistory)
      } else {
        hasError = true
      }
    }

    // Fetch EVM history for each chain
    if (evmManager != null && evmAddress.isNotBlank()) {
      for (chain in evmChains) {
        val evmHistory = evmManager.fetchTransactionHistory(
          address = evmAddress,
          chainId = chain.chainId,
          currency = chain.currency,
          apiKey = chain.apiKey,
          etherscanApi = chain.etherscanApi,
        )
        if (evmHistory != null) {
          allTransactions.addAll(evmHistory)
        } else {
          hasError = true
        }
      }
    }

    // Sort by timestamp descending (most recent first)
    val sorted = allTransactions.sortedByDescending { it.timestamp }

    _uiState.value = _uiState.value.copy(
      transactions = sorted,
      isLoading = false,
      error = if (hasError && sorted.isEmpty()) "Failed to load transaction history" else null,
    )
  }

  /**
   * Toggles the expanded state of a transaction detail view.
   *
   * If the tapped tx is already expanded, collapse it.
   * Otherwise, expand it and collapse the previous one.
   */
  fun toggleExpanded(txHash: String) {
    val current = _uiState.value.expandedTxHash
    _uiState.value = _uiState.value.copy(
      expandedTxHash = if (current == txHash) null else txHash,
    )
  }
}

/**
 * Configuration for fetching EVM transaction history for a specific chain.
 *
 * @param chainId the EVM chain ID (1 for ETH, 56 for BSC, 137 for Polygon)
 * @param currency the native currency symbol
 * @param apiKey the Etherscan API key for this chain
 * @param etherscanApi the Etherscan Retrofit API interface
 */
data class EvmChainHistoryConfig(
  val chainId: Long,
  val currency: String,
  val apiKey: String,
  val etherscanApi: EtherscanApi,
)
