package com.mcw.wallet.ui.history

import com.mcw.core.btc.BtcManager
import com.mcw.core.evm.EvmManager
import com.mcw.core.network.TransactionRecord
import com.mcw.core.network.TxDirection
import com.mcw.core.network.api.EtherscanApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * Unit tests for HistoryViewModel.
 *
 * Tests cover:
 * - Loading BTC history from BtcManager
 * - Loading EVM history from EvmManager
 * - Merging and sorting from multiple chains
 * - Offline error handling
 * - Toggle expanded state
 */
class HistoryViewModelTest {

  private val btcAddress = "1DEP8i3QJCsjoHoFY2n4l3L2X8GKEyJSbb"
  private val evmAddress = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"

  private fun createTxRecord(
    hash: String,
    direction: TxDirection = TxDirection.IN,
    currency: String = "BTC",
    timestamp: Long = 1700000000L,
    amount: BigDecimal = BigDecimal("0.001"),
  ) = TransactionRecord(
    hash = hash,
    direction = direction,
    amount = amount,
    fee = BigDecimal.ZERO,
    currency = currency,
    timestamp = timestamp,
    confirmations = 10,
    counterpartyAddress = "0xCounterparty",
  )

  @Test
  fun testLoadHistory_btcOnly() = runTest {
    val btcManager: BtcManager = mock()
    val btcTx = createTxRecord("btc_tx_1", currency = "BTC", timestamp = 1700000001)

    whenever(btcManager.fetchTransactionHistory(btcAddress))
      .thenReturn(listOf(btcTx))

    val viewModel = HistoryViewModel(
      btcManager = btcManager,
      evmManager = null,
      btcAddress = btcAddress,
      evmAddress = "",
    )

    viewModel.loadHistory()

    val state = viewModel.uiState.value
    assertFalse("Should not be loading after load", state.isLoading)
    assertNull("Error should be null on success", state.error)
    assertEquals("Should have 1 transaction", 1, state.transactions.size)
    assertEquals("Should be BTC tx", "btc_tx_1", state.transactions[0].hash)
    assertEquals("Currency should be BTC", "BTC", state.transactions[0].currency)
  }

  @Test
  fun testLoadHistory_evmOnly() = runTest {
    val evmManager: EvmManager = mock()
    val etherscanApi: EtherscanApi = mock()

    val ethTx = createTxRecord("eth_tx_1", currency = "ETH", timestamp = 1700000002)

    whenever(evmManager.fetchTransactionHistory(
      address = eq(evmAddress),
      chainId = eq(1L),
      currency = eq("ETH"),
      apiKey = eq("testkey"),
      etherscanApi = any(),
    )).thenReturn(listOf(ethTx))

    val chainConfig = EvmChainHistoryConfig(
      chainId = 1L,
      currency = "ETH",
      apiKey = "testkey",
      etherscanApi = etherscanApi,
    )

    val viewModel = HistoryViewModel(
      btcManager = null,
      evmManager = evmManager,
      btcAddress = "",
      evmAddress = evmAddress,
      evmChains = listOf(chainConfig),
    )

    viewModel.loadHistory()

    val state = viewModel.uiState.value
    assertFalse("Should not be loading", state.isLoading)
    assertNull("Error should be null", state.error)
    assertEquals("Should have 1 transaction", 1, state.transactions.size)
    assertEquals("Should be ETH tx", "eth_tx_1", state.transactions[0].hash)
  }

  @Test
  fun testLoadHistory_multiChainMergeAndSort() = runTest {
    val btcManager: BtcManager = mock()
    val evmManager: EvmManager = mock()
    val etherscanApi: EtherscanApi = mock()

    val btcTx = createTxRecord("btc_tx", currency = "BTC", timestamp = 1700000001)
    val ethTx = createTxRecord("eth_tx", currency = "ETH", timestamp = 1700000003)
    val bnbTx = createTxRecord("bnb_tx", currency = "BNB", timestamp = 1700000002)

    whenever(btcManager.fetchTransactionHistory(btcAddress))
      .thenReturn(listOf(btcTx))

    whenever(evmManager.fetchTransactionHistory(
      address = eq(evmAddress),
      chainId = eq(1L),
      currency = eq("ETH"),
      apiKey = eq("eth_key"),
      etherscanApi = any(),
    )).thenReturn(listOf(ethTx))

    whenever(evmManager.fetchTransactionHistory(
      address = eq(evmAddress),
      chainId = eq(56L),
      currency = eq("BNB"),
      apiKey = eq("bnb_key"),
      etherscanApi = any(),
    )).thenReturn(listOf(bnbTx))

    val viewModel = HistoryViewModel(
      btcManager = btcManager,
      evmManager = evmManager,
      btcAddress = btcAddress,
      evmAddress = evmAddress,
      evmChains = listOf(
        EvmChainHistoryConfig(1L, "ETH", "eth_key", etherscanApi),
        EvmChainHistoryConfig(56L, "BNB", "bnb_key", etherscanApi),
      ),
    )

    viewModel.loadHistory()

    val state = viewModel.uiState.value
    assertEquals("Should have 3 transactions from all chains", 3, state.transactions.size)

    // Verify sorted by timestamp descending
    assertEquals("First should be ETH (most recent)", "eth_tx", state.transactions[0].hash)
    assertEquals("Second should be BNB", "bnb_tx", state.transactions[1].hash)
    assertEquals("Third should be BTC (oldest)", "btc_tx", state.transactions[2].hash)
  }

  @Test
  fun testLoadHistory_partialError_showsResults() = runTest {
    val btcManager: BtcManager = mock()
    val evmManager: EvmManager = mock()
    val etherscanApi: EtherscanApi = mock()

    val btcTx = createTxRecord("btc_tx", currency = "BTC", timestamp = 1700000001)

    // BTC succeeds
    whenever(btcManager.fetchTransactionHistory(btcAddress))
      .thenReturn(listOf(btcTx))

    // ETH fails
    whenever(evmManager.fetchTransactionHistory(
      address = eq(evmAddress),
      chainId = eq(1L),
      currency = eq("ETH"),
      apiKey = eq("eth_key"),
      etherscanApi = any(),
    )).thenReturn(null)

    val viewModel = HistoryViewModel(
      btcManager = btcManager,
      evmManager = evmManager,
      btcAddress = btcAddress,
      evmAddress = evmAddress,
      evmChains = listOf(
        EvmChainHistoryConfig(1L, "ETH", "eth_key", etherscanApi),
      ),
    )

    viewModel.loadHistory()

    val state = viewModel.uiState.value
    // Partial success: BTC loaded, ETH failed
    assertEquals("Should still have BTC transactions", 1, state.transactions.size)
    // Error should be null because we have some results
    assertNull("Error should be null when we have partial results", state.error)
  }

  @Test
  fun testLoadHistory_totalFailure_showsError() = runTest {
    val btcManager: BtcManager = mock()

    whenever(btcManager.fetchTransactionHistory(btcAddress))
      .thenReturn(null)

    val viewModel = HistoryViewModel(
      btcManager = btcManager,
      evmManager = null,
      btcAddress = btcAddress,
      evmAddress = "",
    )

    viewModel.loadHistory()

    val state = viewModel.uiState.value
    assertTrue("Should have empty transactions on total failure", state.transactions.isEmpty())
    assertNotNull("Error should be set on total failure", state.error)
    assertEquals("Error message", "Failed to load transaction history", state.error)
  }

  @Test
  fun testToggleExpanded_expandsTransaction() {
    val viewModel = HistoryViewModel(
      btcManager = null,
      evmManager = null,
      btcAddress = "",
      evmAddress = "",
    )

    assertNull("Initially no expanded tx", viewModel.uiState.value.expandedTxHash)

    viewModel.toggleExpanded("tx_hash_1")
    assertEquals("Should expand tx_hash_1", "tx_hash_1", viewModel.uiState.value.expandedTxHash)

    // Toggle same hash -> collapse
    viewModel.toggleExpanded("tx_hash_1")
    assertNull("Should collapse tx_hash_1", viewModel.uiState.value.expandedTxHash)

    // Toggle different hash -> switch
    viewModel.toggleExpanded("tx_hash_1")
    viewModel.toggleExpanded("tx_hash_2")
    assertEquals("Should switch to tx_hash_2", "tx_hash_2", viewModel.uiState.value.expandedTxHash)
  }

  @Test
  fun testLoadHistory_setsLoadingState() = runTest {
    val btcManager: BtcManager = mock()

    whenever(btcManager.fetchTransactionHistory(btcAddress))
      .thenReturn(emptyList())

    val viewModel = HistoryViewModel(
      btcManager = btcManager,
      evmManager = null,
      btcAddress = btcAddress,
      evmAddress = "",
    )

    // After loadHistory completes, isLoading should be false
    viewModel.loadHistory()
    assertFalse("Should not be loading after completion", viewModel.uiState.value.isLoading)
  }
}
