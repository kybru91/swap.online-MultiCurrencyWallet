package com.mcw.wallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcw.core.btc.BtcManager
import com.mcw.core.evm.EvmManager
import com.mcw.core.network.api.CoinGeckoApi
import com.mcw.core.storage.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Abstraction for fetching EVM balances.
 * Decouples the ViewModel from web3j dependency (which lives in :core:evm).
 * Production implementation wraps EvmManager + chain-specific Web3j instances.
 * Test implementation is a simple mock.
 */
interface EvmBalanceFetcher {
  /**
   * Fetches the native balance for an address on a specific chain.
   * @param address the EVM address
   * @param currency the currency symbol (ETH, BNB, MATIC)
   * @return balance in native units, or null on error
   */
  suspend fun fetchBalance(address: String, currency: String): BigDecimal?
}

/**
 * ViewModel managing wallet balances with pull-to-refresh and offline mode.
 *
 * Balance display (from tech-spec Architecture "Balance display"):
 * - On pull-to-refresh or screen open: parallel requests for BTC + EVM balances + fiat prices
 * - Results displayed immediately as they arrive (no persistent caching)
 * - In-memory StateFlow retains last successful balances with lastUpdated timestamp
 * - Offline mode: retain previous balances, show "No internet connection" error banner,
 *   disable Send button
 * - Network restored: auto-refresh on next pull-to-refresh
 *
 * Supported currencies: BTC (8 decimals), ETH/BNB/MATIC (18 decimals)
 *
 * Note: This ViewModel is not @HiltViewModel because it requires runtime parameters
 * (btcAddress, ethAddress) that come from SecureStorage after wallet creation.
 * In production, use a ViewModelProvider.Factory or assisted injection.
 */
class WalletViewModel(
  private val btcManager: BtcManager,
  private val evmManager: EvmManager,
  private val secureStorage: SecureStorage,
  private val coinGeckoApi: CoinGeckoApi,
  val btcAddress: String = "",
  val ethAddress: String = "",
) : ViewModel() {

  companion object {
    /** Supported currencies in display order */
    val CURRENCIES = listOf(
      CurrencyConfig("BTC", 8),
      CurrencyConfig("ETH", 18),
      CurrencyConfig("BNB", 18),
      CurrencyConfig("MATIC", 18),
    )
  }

  /**
   * EVM balance fetcher, set by the DI layer or test.
   * Decouples ViewModel from web3j (which lives in :core:evm module).
   * Internal setter prevents external mutation after initialization.
   */
  var evmBalanceFetcher: EvmBalanceFetcher? = null
    internal set

  private val _uiState = MutableStateFlow(WalletUiState())
  val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

  /**
   * Refreshes all balances from network.
   *
   * Fetches BTC balance (Bitpay), EVM balances (eth_getBalance per chain),
   * and fiat prices (CoinGecko) in parallel. On any failure, retains previous
   * balances and shows error banner.
   */
  fun refreshBalances() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }

      var hasError = false
      val newBalances = mutableMapOf<String, BigDecimal>()

      // Fetch BTC balance
      try {
        val btcBalance = btcManager.fetchBalance(btcAddress)
        newBalances["BTC"] = btcBalance.balance
      } catch (e: Exception) {
        hasError = true
      }

      // Fetch EVM balances (ETH, BNB, MATIC share the same address)
      val fetcher = evmBalanceFetcher
      for (currency in listOf("ETH", "BNB", "MATIC")) {
        try {
          if (fetcher != null) {
            val balance = fetcher.fetchBalance(ethAddress, currency)
            if (balance != null) {
              newBalances[currency] = balance
            } else {
              hasError = true
            }
          } else {
            hasError = true
          }
        } catch (e: Exception) {
          hasError = true
        }
      }

      // Fetch fiat prices
      val prices = try {
        evmManager.fetchFiatPrices(
          coinGeckoApi,
          CURRENCIES.map { it.symbol }
        )
      } catch (e: Exception) {
        hasError = true
        null
      }

      // Build UI balance list
      val previousBalances = _uiState.value.balances
      val balanceList = CURRENCIES.map { config ->
        val balance = newBalances[config.symbol]
          ?: previousBalances.find { it.currency == config.symbol }?.balance
          ?: BigDecimal.ZERO

        val fiatPrice = prices?.get(config.symbol)
        val balanceUsd = if (fiatPrice != null) {
          balance.multiply(fiatPrice).setScale(2, RoundingMode.HALF_UP)
        } else {
          null
        }

        CurrencyBalanceUi(
          currency = config.symbol,
          balance = balance,
          balanceUsd = balanceUsd,
          decimals = config.decimals,
        )
      }

      val now = System.currentTimeMillis()
      val hasAnyNewData = newBalances.isNotEmpty()

      _uiState.update { current ->
        current.copy(
          balances = balanceList,
          isLoading = false,
          error = if (hasError && !hasAnyNewData) "Failed to load balances. Check your internet connection." else null,
          isSendDisabled = hasError && !hasAnyNewData,
          lastUpdated = if (hasAnyNewData) now else current.lastUpdated,
        )
      }
    }
  }
}

/**
 * Configuration for a supported currency.
 *
 * @param symbol currency symbol (BTC, ETH, BNB, MATIC)
 * @param decimals display precision
 */
data class CurrencyConfig(
  val symbol: String,
  val decimals: Int,
)
