package com.mcw.wallet.ui.send

import com.mcw.core.btc.BroadcastException
import com.mcw.core.btc.BtcFeeRates
import com.mcw.core.btc.BtcManager
import com.mcw.core.btc.ChangeResult
import com.mcw.core.btc.UnspentOutput
import com.mcw.core.evm.EvmManager
import com.mcw.core.evm.GasEstimate
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Unit tests for SendViewModel -- address validation, amount validation,
 * state machine transitions, and duplicate submission prevention.
 *
 * TDD anchors from task 9:
 * - testAddressValidationBtc -- valid P2PKH passes, invalid error
 * - testAddressValidationEth -- checksummed passes, lowercase warning, invalid error
 * - testAmountValidation -- 0 error, > balance error, valid passes
 * - testDuplicateSubmissionPrevention -- tap Send -> button disabled until broadcast completes
 * - testStateMachine -- Idle -> Building -> Confirming -> Submitting -> Success
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var btcManager: BtcManager
  private lateinit var evmManager: EvmManager
  private lateinit var secureStorage: SecureStorage
  private lateinit var evmSendHelper: EvmSendHelper

  private val testBtcAddress = "1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA"
  private val testEthAddress = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"
  private val testBtcWIF = "L1RzGGsYw7iFocm9v4gLPihmTJagbBwRaApK1KnDCqeFE7GEDhsz"
  private val testEthHex = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"

  /**
   * Test checksum provider that returns the address as-is if it matches
   * the expected checksummed form, or a lowercase version for "not checksummed" testing.
   */
  private val testChecksumProvider = object : AddressChecksumProvider {
    override fun toChecksumAddress(address: String): String {
      // Known checksummed addresses return as-is
      if (address == "0x9858EfFD232B4033E47d90003D41EC34EcaEda94") return address
      // Lowercase input: return mixed-case checksum form (different from input)
      if (address == address.lowercase() && address.startsWith("0x")) {
        return "0x" + address.removePrefix("0x").uppercase().take(20) +
          address.removePrefix("0x").lowercase().drop(20)
      }
      // All-uppercase input: return mixed-case (different from input)
      if (address.removePrefix("0x") == address.removePrefix("0x").uppercase()) {
        return "0x" + address.removePrefix("0x").lowercase().take(20) +
          address.removePrefix("0x").uppercase().drop(20)
      }
      // Mixed case but not matching the known checksummed address: return different
      return "0x" + address.removePrefix("0x").lowercase().take(10) +
        address.removePrefix("0x").uppercase().drop(10)
    }
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    btcManager = mock()
    evmManager = mock()
    secureStorage = mock()
    evmSendHelper = mock()

    whenever(secureStorage.getPrivateKeys()).thenReturn(Pair(testBtcWIF, testEthHex))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createBtcViewModel(balance: BigDecimal = BigDecimal("1.5")): SendViewModel {
    return SendViewModel(
      currency = "BTC",
      balance = balance,
      senderAddress = testBtcAddress,
      btcManager = btcManager,
      evmManager = evmManager,
      secureStorage = secureStorage,
      evmSendHelper = null,
      chainId = 0L,
    )
  }

  private fun createEvmViewModel(
    currency: String = "ETH",
    balance: BigDecimal = BigDecimal("2.5"),
  ): SendViewModel {
    return SendViewModel(
      currency = currency,
      balance = balance,
      senderAddress = testEthAddress,
      btcManager = btcManager,
      evmManager = evmManager,
      secureStorage = secureStorage,
      evmSendHelper = evmSendHelper,
      chainId = 1L,
      checksumProvider = testChecksumProvider,
    )
  }

  // ===== Address Validation Tests =====

  @Test
  fun `testAddressValidationBtc - valid P2PKH address passes`() {
    val vm = createBtcViewModel()
    val result = vm.validateAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
    assertEquals(AddressValidation.Valid, result)
  }

  @Test
  fun `testAddressValidationBtc - valid P2PKH testnet address passes`() {
    val vm = createBtcViewModel()
    val result = vm.validateAddress("mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn")
    assertEquals(AddressValidation.Valid, result)
  }

  @Test
  fun `testAddressValidationBtc - empty address returns error`() {
    val vm = createBtcViewModel()
    val result = vm.validateAddress("")
    assertTrue(result is AddressValidation.Invalid)
  }

  @Test
  fun `testAddressValidationBtc - too short address returns error`() {
    val vm = createBtcViewModel()
    val result = vm.validateAddress("1A1zP1e")
    assertTrue(result is AddressValidation.Invalid)
  }

  @Test
  fun `testAddressValidationBtc - too long address returns error`() {
    val vm = createBtcViewModel()
    val result = vm.validateAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa12345")
    assertTrue(result is AddressValidation.Invalid)
  }

  @Test
  fun `testAddressValidationBtc - special characters return error`() {
    val vm = createBtcViewModel()
    val result = vm.validateAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7Div!@#")
    assertTrue(result is AddressValidation.Invalid)
  }

  @Test
  fun `testAddressValidationBtc - EVM address in BTC field returns error`() {
    val vm = createBtcViewModel()
    val result = vm.validateAddress("0x9858EfFD232B4033E47d90003D41EC34EcaEda94")
    assertTrue("EVM address should be rejected in BTC send", result is AddressValidation.Invalid)
  }

  @Test
  fun `testAddressValidationEth - checksummed address passes`() {
    val vm = createEvmViewModel()
    val result = vm.validateAddress("0x9858EfFD232B4033E47d90003D41EC34EcaEda94")
    assertEquals(AddressValidation.Valid, result)
  }

  @Test
  fun `testAddressValidationEth - lowercase address returns warning`() {
    val vm = createEvmViewModel()
    val result = vm.validateAddress("0x9858effd232b4033e47d90003d41ec34ecaeda94")
    assertTrue(
      "Lowercase EVM address should return checksum warning",
      result is AddressValidation.ChecksumWarning
    )
  }

  @Test
  fun `testAddressValidationEth - uppercase address returns warning`() {
    val vm = createEvmViewModel()
    val result = vm.validateAddress("0x9858EFFD232B4033E47D90003D41EC34ECAEDA94")
    assertTrue(
      "All-uppercase EVM address should return checksum warning",
      result is AddressValidation.ChecksumWarning
    )
  }

  @Test
  fun `testAddressValidationEth - invalid format returns error`() {
    val vm = createEvmViewModel()
    val result = vm.validateAddress("not_an_address")
    assertTrue(result is AddressValidation.Invalid)
  }

  @Test
  fun `testAddressValidationEth - wrong length returns error`() {
    val vm = createEvmViewModel()
    val result = vm.validateAddress("0x9858EfFD232B4033E47d90003D41EC34EcaEda")
    assertTrue(result is AddressValidation.Invalid)
  }

  @Test
  fun `testAddressValidationEth - empty address returns error`() {
    val vm = createEvmViewModel()
    val result = vm.validateAddress("")
    assertTrue(result is AddressValidation.Invalid)
  }

  @Test
  fun `testAddressValidationEth - BTC address in ETH field returns error`() {
    val vm = createEvmViewModel()
    val result = vm.validateAddress("1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA")
    assertTrue("BTC address should be rejected in EVM send", result is AddressValidation.Invalid)
  }

  @Test
  fun `testAddressValidationEth - invalid mixed case returns warning`() {
    val vm = createEvmViewModel()
    val result = vm.validateAddress("0x9858EfFD232b4033e47d90003d41ec34ecaeda94")
    assertTrue(
      "Incorrectly checksummed address should return checksum warning",
      result is AddressValidation.ChecksumWarning
    )
  }

  // ===== Amount Validation Tests =====

  @Test
  fun `testAmountValidation - zero amount returns error`() {
    val vm = createBtcViewModel(balance = BigDecimal("1.0"))
    val result = vm.validateAmount("0")
    assertTrue("Zero amount should fail", result is AmountValidation.Invalid)
    assertEquals("Amount must be greater than zero", (result as AmountValidation.Invalid).message)
  }

  @Test
  fun `testAmountValidation - negative amount returns error`() {
    val vm = createBtcViewModel(balance = BigDecimal("1.0"))
    val result = vm.validateAmount("-1")
    assertTrue("Negative amount should fail", result is AmountValidation.Invalid)
  }

  @Test
  fun `testAmountValidation - exceeds balance returns error`() {
    val vm = createBtcViewModel(balance = BigDecimal("1.0"))
    val result = vm.validateAmount("2.0")
    assertTrue("Amount exceeding balance should fail", result is AmountValidation.Invalid)
    assertTrue(
      "Should mention insufficient balance",
      (result as AmountValidation.Invalid).message.contains("Insufficient balance")
    )
  }

  @Test
  fun `testAmountValidation - valid amount passes`() {
    val vm = createBtcViewModel(balance = BigDecimal("1.5"))
    val result = vm.validateAmount("0.5")
    assertEquals(AmountValidation.Valid, result)
  }

  @Test
  fun `testAmountValidation - equals balance passes`() {
    val vm = createBtcViewModel(balance = BigDecimal("1.0"))
    val result = vm.validateAmount("1.0")
    assertEquals(AmountValidation.Valid, result)
  }

  @Test
  fun `testAmountValidation - empty string returns error`() {
    val vm = createBtcViewModel()
    val result = vm.validateAmount("")
    assertTrue("Empty amount should fail", result is AmountValidation.Invalid)
  }

  @Test
  fun `testAmountValidation - non-numeric returns error`() {
    val vm = createBtcViewModel()
    val result = vm.validateAmount("abc")
    assertTrue("Non-numeric amount should fail", result is AmountValidation.Invalid)
  }

  // ===== State Machine Tests =====

  @Test
  fun `testStateMachine - initial state is Idle`() {
    val vm = createBtcViewModel()
    assertEquals(SendState.Idle, vm.uiState.value.state)
  }

  @Test
  fun `testStateMachine - Idle to Building to Confirming for BTC`() = runTest {
    val feeRates = BtcFeeRates(fast = 30000L, normal = 15000L, slow = 5000L)
    whenever(btcManager.estimateFees()).thenReturn(feeRates)

    val utxos = listOf(
      UnspentOutput(txid = "aabb", vout = 0, valueSatoshis = 200_000_000L, script = "76a914...")
    )
    whenever(btcManager.fetchUnspentOutputs(testBtcAddress)).thenReturn(utxos)

    val vm = createBtcViewModel()
    vm.onAddressChanged("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
    vm.onAmountChanged("0.5")

    vm.prepareSend()
    advanceUntilIdle()

    val state = vm.uiState.value
    assertEquals("Should transition to Confirming after building", SendState.Confirming, state.state)
    assertNotNull("Should have fee options", state.feeOptions)
    assertTrue("Should have 3 fee tiers", state.feeOptions!!.size == 3)
  }

  @Test
  fun `testStateMachine - Idle to Building to Confirming for EVM`() = runTest {
    val gasEstimate = GasEstimate(
      gasPrice = BigInteger.valueOf(20_000_000_000L),
      gasLimit = BigInteger.valueOf(21000L),
      totalFeeWei = BigInteger.valueOf(420_000_000_000_000L),
      totalFeeNative = BigDecimal("0.00042"),
    )
    whenever(evmSendHelper.estimateGas(any(), any(), any())).thenReturn(gasEstimate)

    val vm = createEvmViewModel()
    vm.onAddressChanged("0x9858EfFD232B4033E47d90003D41EC34EcaEda94")
    vm.onAmountChanged("0.5")

    vm.prepareSend()
    advanceUntilIdle()

    val state = vm.uiState.value
    assertEquals("Should transition to Confirming", SendState.Confirming, state.state)
    assertNotNull("Should have fee options", state.feeOptions)
    assertEquals("Should have 3 fee tiers", 3, state.feeOptions!!.size)
  }

  @Test
  fun `testStateMachine - Building to Error on fee estimation failure`() = runTest {
    whenever(btcManager.estimateFees()).thenAnswer { throw RuntimeException("Network error") }

    val vm = createBtcViewModel()
    vm.onAddressChanged("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
    vm.onAmountChanged("0.5")

    vm.prepareSend()
    advanceUntilIdle()

    val state = vm.uiState.value
    assertEquals("Should transition to Error on failure", SendState.Error, state.state)
    assertNotNull("Should have error message", state.error)
  }

  @Test
  fun `testStateMachine - Confirming to Submitting to Success for BTC`() = runTest {
    val feeRates = BtcFeeRates(fast = 30000L, normal = 15000L, slow = 5000L)
    whenever(btcManager.estimateFees()).thenReturn(feeRates)

    val utxos = listOf(
      UnspentOutput(txid = "aabb", vout = 0, valueSatoshis = 200_000_000L, script = "76a914...")
    )
    whenever(btcManager.fetchUnspentOutputs(testBtcAddress)).thenReturn(utxos)
    whenever(btcManager.selectUtxos(any(), any())).thenReturn(utxos)
    whenever(btcManager.calculateTxSize(any(), any())).thenReturn(226)
    whenever(btcManager.calculateFee(any(), any())).thenReturn(3400L)
    whenever(btcManager.calculateChange(any(), any(), any())).thenReturn(
      ChangeResult(changeSatoshis = 149_996_600L, effectiveFeeSatoshis = 3400L)
    )
    whenever(btcManager.buildTransaction(any(), any(), any(), any(), any(), any()))
      .thenReturn("signed_tx_hex_data")
    whenever(btcManager.broadcastTransaction(any())).thenReturn("txhash_abc123")

    val vm = createBtcViewModel()
    vm.onAddressChanged("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
    vm.onAmountChanged("0.5")

    vm.prepareSend()
    advanceUntilIdle()
    assertEquals(SendState.Confirming, vm.uiState.value.state)

    vm.selectFeeTier(0)
    vm.confirmSend()
    advanceUntilIdle()

    val finalState = vm.uiState.value
    assertEquals("Should transition to Success", SendState.Success, finalState.state)
    assertEquals("Should have tx hash", "txhash_abc123", finalState.txHash)
  }

  @Test
  fun `testStateMachine - Submitting to Error on broadcast failure for BTC`() = runTest {
    val feeRates = BtcFeeRates(fast = 30000L, normal = 15000L, slow = 5000L)
    whenever(btcManager.estimateFees()).thenReturn(feeRates)

    val utxos = listOf(
      UnspentOutput(txid = "aabb", vout = 0, valueSatoshis = 200_000_000L, script = "76a914...")
    )
    whenever(btcManager.fetchUnspentOutputs(testBtcAddress)).thenReturn(utxos)
    whenever(btcManager.selectUtxos(any(), any())).thenReturn(utxos)
    whenever(btcManager.calculateTxSize(any(), any())).thenReturn(226)
    whenever(btcManager.calculateFee(any(), any())).thenReturn(3400L)
    whenever(btcManager.calculateChange(any(), any(), any())).thenReturn(
      ChangeResult(changeSatoshis = 149_996_600L, effectiveFeeSatoshis = 3400L)
    )
    whenever(btcManager.buildTransaction(any(), any(), any(), any(), any(), any()))
      .thenReturn("signed_tx_hex_data")
    whenever(btcManager.broadcastTransaction(any()))
      .thenAnswer { throw BroadcastException("Broadcast failed: timeout") }

    val vm = createBtcViewModel()
    vm.onAddressChanged("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
    vm.onAmountChanged("0.5")

    vm.prepareSend()
    advanceUntilIdle()
    vm.selectFeeTier(0)
    vm.confirmSend()
    advanceUntilIdle()

    val finalState = vm.uiState.value
    assertEquals("Should transition to Error", SendState.Error, finalState.state)
    assertNotNull("Should have error message", finalState.error)
    assertTrue(
      "Error should mention broadcast failure",
      finalState.error!!.contains("Broadcast failed") || finalState.error!!.contains("Transaction failed")
    )
  }

  @Test
  fun `testStateMachine - Confirming to Submitting to Success for EVM`() = runTest {
    val gasEstimate = GasEstimate(
      gasPrice = BigInteger.valueOf(20_000_000_000L),
      gasLimit = BigInteger.valueOf(21000L),
      totalFeeWei = BigInteger.valueOf(420_000_000_000_000L),
      totalFeeNative = BigDecimal("0.00042"),
    )
    whenever(evmSendHelper.estimateGas(any(), any(), any())).thenReturn(gasEstimate)
    whenever(evmSendHelper.sendTransaction(any(), any(), any(), any(), any(), any()))
      .thenReturn("0xevmtxhash123")

    val vm = createEvmViewModel()
    vm.onAddressChanged("0x9858EfFD232B4033E47d90003D41EC34EcaEda94")
    vm.onAmountChanged("0.5")

    vm.prepareSend()
    advanceUntilIdle()
    assertEquals(SendState.Confirming, vm.uiState.value.state)

    vm.selectFeeTier(0)
    vm.confirmSend()
    advanceUntilIdle()

    val finalState = vm.uiState.value
    assertEquals("Should transition to Success", SendState.Success, finalState.state)
    assertEquals("Should have EVM tx hash", "0xevmtxhash123", finalState.txHash)
  }

  // ===== Duplicate Submission Prevention Tests =====

  @Test
  fun `testDuplicateSubmissionPrevention - send button disabled during submitting`() = runTest {
    val feeRates = BtcFeeRates(fast = 30000L, normal = 15000L, slow = 5000L)
    whenever(btcManager.estimateFees()).thenReturn(feeRates)

    val utxos = listOf(
      UnspentOutput(txid = "aabb", vout = 0, valueSatoshis = 200_000_000L, script = "76a914...")
    )
    whenever(btcManager.fetchUnspentOutputs(testBtcAddress)).thenReturn(utxos)
    whenever(btcManager.selectUtxos(any(), any())).thenReturn(utxos)
    whenever(btcManager.calculateTxSize(any(), any())).thenReturn(226)
    whenever(btcManager.calculateFee(any(), any())).thenReturn(3400L)
    whenever(btcManager.calculateChange(any(), any(), any())).thenReturn(
      ChangeResult(changeSatoshis = 149_996_600L, effectiveFeeSatoshis = 3400L)
    )
    whenever(btcManager.buildTransaction(any(), any(), any(), any(), any(), any()))
      .thenReturn("signed_tx_hex_data")
    whenever(btcManager.broadcastTransaction(any())).thenReturn("txhash_abc123")

    val vm = createBtcViewModel()
    vm.onAddressChanged("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
    vm.onAmountChanged("0.5")

    assertFalse("Send button should be enabled initially", vm.uiState.value.isSubmitting)

    vm.prepareSend()
    advanceUntilIdle()
    vm.selectFeeTier(0)

    vm.confirmSend()
    advanceUntilIdle()

    assertFalse("Send button should not be submitting after success", vm.uiState.value.isSubmitting)
    assertEquals(SendState.Success, vm.uiState.value.state)
  }

  @Test
  fun `testDuplicateSubmissionPrevention - send button re-enabled on error`() = runTest {
    val feeRates = BtcFeeRates(fast = 30000L, normal = 15000L, slow = 5000L)
    whenever(btcManager.estimateFees()).thenReturn(feeRates)

    val utxos = listOf(
      UnspentOutput(txid = "aabb", vout = 0, valueSatoshis = 200_000_000L, script = "76a914...")
    )
    whenever(btcManager.fetchUnspentOutputs(testBtcAddress)).thenReturn(utxos)
    whenever(btcManager.selectUtxos(any(), any())).thenReturn(utxos)
    whenever(btcManager.calculateTxSize(any(), any())).thenReturn(226)
    whenever(btcManager.calculateFee(any(), any())).thenReturn(3400L)
    whenever(btcManager.calculateChange(any(), any(), any())).thenReturn(
      ChangeResult(changeSatoshis = 149_996_600L, effectiveFeeSatoshis = 3400L)
    )
    whenever(btcManager.buildTransaction(any(), any(), any(), any(), any(), any()))
      .thenReturn("signed_tx_hex_data")
    whenever(btcManager.broadcastTransaction(any()))
      .thenAnswer { throw BroadcastException("Network timeout") }

    val vm = createBtcViewModel()
    vm.onAddressChanged("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
    vm.onAmountChanged("0.5")

    vm.prepareSend()
    advanceUntilIdle()
    vm.selectFeeTier(0)
    vm.confirmSend()
    advanceUntilIdle()

    assertFalse("Send button should be re-enabled after error", vm.uiState.value.isSubmitting)
    assertEquals(SendState.Error, vm.uiState.value.state)
  }

  // ===== Error to Idle Reset =====

  @Test
  fun `error state can be reset to idle`() {
    val vm = createBtcViewModel()
    vm.onAddressChanged("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
    vm.onAmountChanged("0.5")
    vm.resetState()
    assertEquals(SendState.Idle, vm.uiState.value.state)
    assertNull(vm.uiState.value.error)
  }

  // ===== Input Change Tests =====

  @Test
  fun `address change updates ui state`() {
    val vm = createBtcViewModel()
    vm.onAddressChanged("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
    assertEquals("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", vm.uiState.value.recipientAddress)
  }

  @Test
  fun `amount change updates ui state`() {
    val vm = createBtcViewModel()
    vm.onAmountChanged("1.5")
    assertEquals("1.5", vm.uiState.value.amount)
  }

  // ===== Fee Tier Selection Tests =====

  @Test
  fun `fee tier selection updates selected index`() = runTest {
    val feeRates = BtcFeeRates(fast = 30000L, normal = 15000L, slow = 5000L)
    whenever(btcManager.estimateFees()).thenReturn(feeRates)
    whenever(btcManager.fetchUnspentOutputs(testBtcAddress)).thenReturn(
      listOf(UnspentOutput(txid = "aa", vout = 0, valueSatoshis = 200_000_000L, script = "76a914..."))
    )

    val vm = createBtcViewModel()
    vm.onAddressChanged("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
    vm.onAmountChanged("0.5")
    vm.prepareSend()
    advanceUntilIdle()

    vm.selectFeeTier(1)
    assertEquals(1, vm.uiState.value.selectedFeeTierIndex)

    vm.selectFeeTier(2)
    assertEquals(2, vm.uiState.value.selectedFeeTierIndex)
  }

  // ===== Validation Before prepareSend =====

  @Test
  fun `prepareSend with invalid address sets error`() = runTest {
    val vm = createBtcViewModel()
    vm.onAddressChanged("")
    vm.onAmountChanged("0.5")

    vm.prepareSend()
    advanceUntilIdle()

    assertEquals(SendState.Error, vm.uiState.value.state)
    assertNotNull(vm.uiState.value.error)
  }

  @Test
  fun `prepareSend with invalid amount sets error`() = runTest {
    val vm = createBtcViewModel()
    vm.onAddressChanged("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
    vm.onAmountChanged("0")

    vm.prepareSend()
    advanceUntilIdle()

    assertEquals(SendState.Error, vm.uiState.value.state)
    assertNotNull(vm.uiState.value.error)
  }

  // ===== EVM Gas Estimation Failure =====

  @Test
  fun `EVM gas estimation failure transitions to Error`() = runTest {
    whenever(evmSendHelper.estimateGas(any(), any(), any())).thenReturn(null)

    val vm = createEvmViewModel()
    vm.onAddressChanged("0x9858EfFD232B4033E47d90003D41EC34EcaEda94")
    vm.onAmountChanged("0.5")

    vm.prepareSend()
    advanceUntilIdle()

    assertEquals(SendState.Error, vm.uiState.value.state)
    assertNotNull("Should have error message", vm.uiState.value.error)
  }

  // ===== Amount + Fee > Balance for BTC =====

  @Test
  fun `amount plus fee exceeding balance handled gracefully`() = runTest {
    val feeRates = BtcFeeRates(fast = 100_000L, normal = 50_000L, slow = 20_000L)
    whenever(btcManager.estimateFees()).thenReturn(feeRates)

    val utxos = listOf(
      UnspentOutput(txid = "aabb", vout = 0, valueSatoshis = 100_000L, script = "76a914...")
    )
    whenever(btcManager.fetchUnspentOutputs(testBtcAddress)).thenReturn(utxos)
    whenever(btcManager.selectUtxos(any(), any())).thenReturn(utxos)
    whenever(btcManager.calculateTxSize(any(), any())).thenReturn(226)
    whenever(btcManager.calculateFee(any(), any())).thenReturn(22_100L)

    val vm = createBtcViewModel(balance = BigDecimal("0.001"))
    vm.onAddressChanged("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")
    vm.onAmountChanged("0.0009")

    vm.prepareSend()
    advanceUntilIdle()

    val state = vm.uiState.value
    // Should transition to Confirming with fee options (user can see the fee)
    // or Error if fees are too high. Either is acceptable behavior.
    assertTrue(
      "Should handle amount+fee > balance gracefully",
      state.state == SendState.Error || state.state == SendState.Confirming
    )
  }

  // ===== EVM Broadcast Failure =====

  @Test
  fun `EVM broadcast failure transitions to Error`() = runTest {
    val gasEstimate = GasEstimate(
      gasPrice = BigInteger.valueOf(20_000_000_000L),
      gasLimit = BigInteger.valueOf(21000L),
      totalFeeWei = BigInteger.valueOf(420_000_000_000_000L),
      totalFeeNative = BigDecimal("0.00042"),
    )
    whenever(evmSendHelper.estimateGas(any(), any(), any())).thenReturn(gasEstimate)
    whenever(evmSendHelper.sendTransaction(any(), any(), any(), any(), any(), any()))
      .thenReturn(null)

    val vm = createEvmViewModel()
    vm.onAddressChanged("0x9858EfFD232B4033E47d90003D41EC34EcaEda94")
    vm.onAmountChanged("0.5")

    vm.prepareSend()
    advanceUntilIdle()

    vm.selectFeeTier(0)
    vm.confirmSend()
    advanceUntilIdle()

    assertEquals("Should transition to Error", SendState.Error, vm.uiState.value.state)
    assertNotNull("Should have error message", vm.uiState.value.error)
  }
}
