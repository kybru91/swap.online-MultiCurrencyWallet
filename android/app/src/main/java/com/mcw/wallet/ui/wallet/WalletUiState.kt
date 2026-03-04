package com.mcw.wallet.ui.wallet

import java.math.BigDecimal

/**
 * UI state for the Wallet screen.
 *
 * Holds in-memory balances (no persistent caching per Decision 8),
 * loading state, offline error, and lastUpdated timestamp.
 *
 * @param balances list of currency balances (BTC, ETH, BNB, MATIC)
 * @param isLoading true during a balance refresh
 * @param error error message for offline banner, null when online
 * @param isSendDisabled true when offline or no balances loaded
 * @param lastUpdated epoch millis of last successful balance fetch, null if never loaded
 */
data class WalletUiState(
  val balances: List<CurrencyBalanceUi> = emptyList(),
  val isLoading: Boolean = false,
  val error: String? = null,
  val isSendDisabled: Boolean = true,
  val lastUpdated: Long? = null,
)

/**
 * Per-currency balance for UI display.
 *
 * @param currency symbol: "BTC", "ETH", "BNB", "MATIC"
 * @param balance native balance (BTC units, ETH units, etc.)
 * @param balanceUsd fiat equivalent in USD, null if price unavailable
 * @param decimals display precision for formatting
 */
data class CurrencyBalanceUi(
  val currency: String,
  val balance: BigDecimal,
  val balanceUsd: BigDecimal?,
  val decimals: Int,
)
