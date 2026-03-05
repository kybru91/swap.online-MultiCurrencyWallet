package com.mcw.wallet.ui.wallet

import com.mcw.core.btc.BtcBalance
import com.mcw.core.btc.BtcManager
import com.mcw.core.evm.EvmManager
import com.mcw.core.network.api.CoinGeckoApi
import com.mcw.core.storage.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * Unit tests for WalletViewModel -- balance refresh, offline mode state.
 *
 * TDD anchors from task 8:
 * - testBalanceRefresh -- pull-to-refresh triggers new API call
 * - testOfflineModeState -- API error -> show error banner, disable Send button
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var btcManager: BtcManager
  private lateinit var evmManager: EvmManager
  private lateinit var secureStorage: SecureStorage
  private lateinit var coinGeckoApi: CoinGeckoApi
  private lateinit var evmBalanceFetcher: EvmBalanceFetcher

  private val testBtcAddress = "1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA"
  private val testEthAddress = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    btcManager = mock()
    evmManager = mock()
    secureStorage = mock()
    coinGeckoApi = mock()
    evmBalanceFetcher = mock()

    whenever(secureStorage.hasWallet()).thenReturn(true)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(): WalletViewModel {
    val vm = WalletViewModel(
      btcManager = btcManager,
      evmManager = evmManager,
      secureStorage = secureStorage,
      coinGeckoApi = coinGeckoApi,
      btcAddress = testBtcAddress,
      ethAddress = testEthAddress,
    )
    vm.evmBalanceFetcher = evmBalanceFetcher
    return vm
  }

  // --- Balance Refresh Tests ---

  @Test
  fun `testBalanceRefresh - pull-to-refresh triggers new API call`() = runTest {
    val btcBalance = BtcBalance(
      balance = BigDecimal("1.50000000"),
      unconfirmed = BigDecimal.ZERO
    )
    whenever(btcManager.fetchBalance(testBtcAddress)).thenReturn(btcBalance)
    whenever(evmBalanceFetcher.fetchBalance(eq(testEthAddress), any())).thenReturn(BigDecimal("2.5"))
    whenever(evmManager.fetchFiatPrices(eq(coinGeckoApi), any())).thenReturn(
      mapOf("BTC" to BigDecimal("50000"), "ETH" to BigDecimal("3000"))
    )

    val viewModel = createViewModel()

    // First refresh
    viewModel.refreshBalances()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse("Should not be loading after refresh", state.isLoading)
    assertNotNull("Should have BTC balance", state.balances.find { it.currency == "BTC" })

    val btcEntry = state.balances.first { it.currency == "BTC" }
    assertEquals("BTC balance should be 1.5", 0, BigDecimal("1.50000000").compareTo(btcEntry.balance))

    // Second refresh triggers new API call
    viewModel.refreshBalances()
    advanceUntilIdle()

    verify(btcManager, times(2)).fetchBalance(testBtcAddress)
  }

  @Test
  fun `testBalanceRefresh - isLoading transitions correctly`() = runTest {
    whenever(btcManager.fetchBalance(testBtcAddress)).thenReturn(
      BtcBalance(BigDecimal("1.0"), BigDecimal.ZERO)
    )
    whenever(evmBalanceFetcher.fetchBalance(eq(testEthAddress), any())).thenReturn(BigDecimal("2.0"))
    whenever(evmManager.fetchFiatPrices(eq(coinGeckoApi), any())).thenReturn(emptyMap())

    val viewModel = createViewModel()

    // Before refresh, isLoading should be false
    assertFalse("Should not be loading initially", viewModel.uiState.value.isLoading)

    viewModel.refreshBalances()
    advanceUntilIdle()

    // After refresh completes, isLoading should be false
    assertFalse("Should not be loading after refresh completes", viewModel.uiState.value.isLoading)

    // Verify balances were loaded (confirming refresh happened)
    assertTrue("Should have balances", viewModel.uiState.value.balances.isNotEmpty())
  }

  // --- Offline Mode Tests ---

  @Test
  fun `testOfflineModeState - API error shows error banner`() = runTest {
    doAnswer { throw RuntimeException("Network error") }
      .whenever(btcManager).fetchBalance(testBtcAddress)
    whenever(evmBalanceFetcher.fetchBalance(eq(testEthAddress), any())).thenReturn(null)
    whenever(evmManager.fetchFiatPrices(eq(coinGeckoApi), any())).thenReturn(null)

    val viewModel = createViewModel()
    viewModel.refreshBalances()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertNotNull("Should have error message", state.error)
    assertTrue("Send should be disabled", state.isSendDisabled)
  }

  @Test
  fun `testOfflineModeState - retains previous balances on network error`() = runTest {
    val btcBalance = BtcBalance(BigDecimal("1.0"), BigDecimal.ZERO)
    whenever(btcManager.fetchBalance(testBtcAddress)).thenReturn(btcBalance)
    whenever(evmBalanceFetcher.fetchBalance(eq(testEthAddress), any())).thenReturn(BigDecimal("2.0"))
    whenever(evmManager.fetchFiatPrices(eq(coinGeckoApi), any())).thenReturn(emptyMap())

    val viewModel = createViewModel()
    viewModel.refreshBalances()
    advanceUntilIdle()

    // Verify balances loaded
    val loadedState = viewModel.uiState.value
    assertTrue("Should have balances", loadedState.balances.isNotEmpty())
    assertFalse("Send should be enabled with balances", loadedState.isSendDisabled)

    // Now simulate network failure: use reset + doAnswer to avoid re-stubbing issue
    reset(btcManager)
    doAnswer { throw RuntimeException("Network error") }
      .whenever(btcManager).fetchBalance(testBtcAddress)
    reset(evmBalanceFetcher)
    whenever(evmBalanceFetcher.fetchBalance(eq(testEthAddress), any())).thenReturn(null)

    viewModel.refreshBalances()
    advanceUntilIdle()

    val offlineState = viewModel.uiState.value
    assertTrue("Should retain previous balances", offlineState.balances.isNotEmpty())
    // BTC balance should still be 1.0 from previous fetch
    val btcEntry = offlineState.balances.first { it.currency == "BTC" }
    assertEquals("Previous BTC balance retained", 0, BigDecimal("1.0").compareTo(btcEntry.balance))
    assertNotNull("Should have lastUpdated timestamp", offlineState.lastUpdated)
  }

  @Test
  fun `testOfflineModeState - network restored clears error`() = runTest {
    // First: fail
    doAnswer { throw RuntimeException("Network error") }
      .whenever(btcManager).fetchBalance(testBtcAddress)
    whenever(evmBalanceFetcher.fetchBalance(eq(testEthAddress), any())).thenReturn(null)
    whenever(evmManager.fetchFiatPrices(eq(coinGeckoApi), any())).thenReturn(null)

    val viewModel = createViewModel()
    viewModel.refreshBalances()
    advanceUntilIdle()

    assertTrue("Should have error", viewModel.uiState.value.error != null)

    // Now: succeed — use reset to clear the previous thenAnswer stub
    reset(btcManager)
    doReturn(BtcBalance(BigDecimal("1.0"), BigDecimal.ZERO))
      .whenever(btcManager).fetchBalance(testBtcAddress)
    reset(evmBalanceFetcher)
    whenever(evmBalanceFetcher.fetchBalance(eq(testEthAddress), any())).thenReturn(BigDecimal("2.0"))
    reset(evmManager)
    whenever(evmManager.fetchFiatPrices(eq(coinGeckoApi), any())).thenReturn(emptyMap())

    viewModel.refreshBalances()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertNull("Error should be cleared after successful refresh", state.error)
    assertFalse("Send should be re-enabled", state.isSendDisabled)
  }

  // --- Zero Balance Display ---

  @Test
  fun `zero balances show as 0 not empty`() = runTest {
    whenever(btcManager.fetchBalance(testBtcAddress)).thenReturn(
      BtcBalance(BigDecimal.ZERO, BigDecimal.ZERO)
    )
    whenever(evmBalanceFetcher.fetchBalance(eq(testEthAddress), any())).thenReturn(BigDecimal.ZERO)
    whenever(evmManager.fetchFiatPrices(eq(coinGeckoApi), any())).thenReturn(emptyMap())

    val viewModel = createViewModel()
    viewModel.refreshBalances()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    // Should have 4 currency entries even with zero balances
    assertTrue("Should have at least 4 currencies", state.balances.size >= 4)
    state.balances.forEach { entry ->
      assertEquals(
        "${entry.currency} should have zero balance",
        0,
        BigDecimal.ZERO.compareTo(entry.balance)
      )
    }
  }

  // --- Fiat Price Display ---

  @Test
  fun `fiat prices populated when available`() = runTest {
    whenever(btcManager.fetchBalance(testBtcAddress)).thenReturn(
      BtcBalance(BigDecimal("1.5"), BigDecimal.ZERO)
    )
    whenever(evmBalanceFetcher.fetchBalance(eq(testEthAddress), any())).thenReturn(BigDecimal("2.0"))
    whenever(evmManager.fetchFiatPrices(eq(coinGeckoApi), any())).thenReturn(
      mapOf("BTC" to BigDecimal("50000"), "ETH" to BigDecimal("3000"))
    )

    val viewModel = createViewModel()
    viewModel.refreshBalances()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    val btcEntry = state.balances.first { it.currency == "BTC" }
    assertNotNull("BTC should have fiat value", btcEntry.balanceUsd)
    assertEquals(
      "BTC fiat value should be 75000",
      0,
      BigDecimal("75000.00").compareTo(btcEntry.balanceUsd)
    )
  }

  @Test
  fun `fiat prices null when API unavailable`() = runTest {
    whenever(btcManager.fetchBalance(testBtcAddress)).thenReturn(
      BtcBalance(BigDecimal("1.0"), BigDecimal.ZERO)
    )
    whenever(evmBalanceFetcher.fetchBalance(eq(testEthAddress), any())).thenReturn(BigDecimal("2.0"))
    whenever(evmManager.fetchFiatPrices(eq(coinGeckoApi), any())).thenReturn(null)

    val viewModel = createViewModel()
    viewModel.refreshBalances()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    val btcEntry = state.balances.first { it.currency == "BTC" }
    assertNull("BTC fiat value should be null when API unavailable", btcEntry.balanceUsd)
  }
}
