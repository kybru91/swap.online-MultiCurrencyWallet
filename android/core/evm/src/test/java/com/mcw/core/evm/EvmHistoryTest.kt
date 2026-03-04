package com.mcw.core.evm

import com.mcw.core.network.TxDirection
import com.mcw.core.network.api.EtherscanApi
import com.mcw.core.network.api.EtherscanResponse
import com.mcw.core.network.api.EtherscanTransaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Unit tests for EVM transaction history parsing.
 *
 * TDD anchors from task 12:
 * - testParseEvmTransaction — Etherscan response -> TransactionRecord
 * - testDetermineDirectionIn — tx to my address -> IN
 * - testDetermineDirectionOut — tx from my address -> OUT
 * - testDetermineDirectionSelf — tx from my address to my address -> SELF
 *
 * Tests use real Etherscan API response fixture format.
 */
class EvmHistoryTest {

  private lateinit var evmManager: EvmManager

  private val walletAddress = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"

  @Before
  fun setUp() {
    evmManager = EvmManager()
  }

  // ===== Direction determination tests =====

  @Test
  fun testDetermineDirectionIn_receivedFunds() {
    val direction = EvmManager.determineEvmDirection(
      from = "0xabcdef1234567890abcdef1234567890abcdef12",
      to = walletAddress,
      walletAddress = walletAddress,
    )
    assertEquals("Direction should be IN when to == wallet", TxDirection.IN, direction)
  }

  @Test
  fun testDetermineDirectionOut_sentFunds() {
    val direction = EvmManager.determineEvmDirection(
      from = walletAddress,
      to = "0xabcdef1234567890abcdef1234567890abcdef12",
      walletAddress = walletAddress,
    )
    assertEquals("Direction should be OUT when from == wallet", TxDirection.OUT, direction)
  }

  @Test
  fun testDetermineDirectionSelf_sentToSelf() {
    val direction = EvmManager.determineEvmDirection(
      from = walletAddress,
      to = walletAddress,
      walletAddress = walletAddress,
    )
    assertEquals("Direction should be SELF when from == to == wallet", TxDirection.SELF, direction)
  }

  @Test
  fun testDetermineDirection_caseInsensitive() {
    // EVM addresses should be compared case-insensitively
    val direction = EvmManager.determineEvmDirection(
      from = walletAddress.lowercase(),
      to = "0xabcdef1234567890abcdef1234567890abcdef12",
      walletAddress = walletAddress,
    )
    assertEquals("Direction should be OUT (case-insensitive from match)", TxDirection.OUT, direction)
  }

  @Test
  fun testDetermineDirection_caseInsensitiveTo() {
    val direction = EvmManager.determineEvmDirection(
      from = "0xabcdef1234567890abcdef1234567890abcdef12",
      to = walletAddress.uppercase(),
      walletAddress = walletAddress,
    )
    assertEquals("Direction should be IN (case-insensitive to match)", TxDirection.IN, direction)
  }

  @Test
  fun testDetermineDirection_neitherMatchDefaultsToIn() {
    // Contract interaction where address is involved indirectly
    val direction = EvmManager.determineEvmDirection(
      from = "0x1111111111111111111111111111111111111111",
      to = "0x2222222222222222222222222222222222222222",
      walletAddress = walletAddress,
    )
    assertEquals("Should default to IN when neither from/to matches", TxDirection.IN, direction)
  }

  // ===== Transaction parsing tests =====

  @Test
  fun testParseEvmTransaction_receivedWithCorrectAmount() {
    // Simulate an Etherscan transaction where wallet received 1.5 ETH
    val tx = createEtherscanTx(
      hash = "0xabc123def456",
      from = "0xSenderAddress",
      to = walletAddress,
      value = "1500000000000000000", // 1.5 ETH in wei
      gasUsed = "21000",
      gasPrice = "20000000000", // 20 Gwei
      timeStamp = "1700000000",
      confirmations = "50",
      blockNumber = "18000000",
    )

    val record = EvmManager.parseEvmTransaction(tx, walletAddress, "ETH")

    assertEquals("Hash should match", "0xabc123def456", record.hash)
    assertEquals("Direction should be IN", TxDirection.IN, record.direction)
    assertEquals(
      "Amount should be 1.5 ETH",
      BigDecimal("1.5").setScale(18, RoundingMode.UNNECESSARY),
      record.amount,
    )
    assertEquals("Currency should be ETH", "ETH", record.currency)
    assertEquals("Confirmations should be 50", 50, record.confirmations)
    assertEquals("Timestamp should be 1700000000", 1700000000L, record.timestamp)
    assertEquals("Block number should be 18000000", 18000000L, record.blockNumber)
    assertFalse("Should not be an error", record.isError)
    assertEquals("Counterparty should be sender", "0xSenderAddress", record.counterpartyAddress)
  }

  @Test
  fun testParseEvmTransaction_sentWithCorrectAmount() {
    val tx = createEtherscanTx(
      hash = "0xsent_hash",
      from = walletAddress,
      to = "0xRecipientAddress",
      value = "2000000000000000000", // 2.0 ETH
      gasUsed = "21000",
      gasPrice = "30000000000",
      timeStamp = "1700100000",
      confirmations = "10",
      blockNumber = "18000100",
    )

    val record = EvmManager.parseEvmTransaction(tx, walletAddress, "ETH")

    assertEquals("Direction should be OUT", TxDirection.OUT, record.direction)
    assertEquals(
      "Amount should be 2.0 ETH",
      BigDecimal("2.0").setScale(18, RoundingMode.UNNECESSARY),
      record.amount,
    )
    assertEquals("Counterparty should be recipient", "0xRecipientAddress", record.counterpartyAddress)
  }

  @Test
  fun testParseEvmTransaction_feeCalculation() {
    // Fee = gasUsed * gasPrice = 21000 * 20 Gwei = 420000 Gwei = 0.00042 ETH
    val tx = createEtherscanTx(
      hash = "0xfee_tx",
      from = walletAddress,
      to = "0xRecipient",
      value = "1000000000000000000",
      gasUsed = "21000",
      gasPrice = "20000000000",
      timeStamp = "1700200000",
      confirmations = "5",
      blockNumber = "18000200",
    )

    val record = EvmManager.parseEvmTransaction(tx, walletAddress, "ETH")

    // 21000 * 20000000000 = 420000000000000 wei = 0.00042 ETH
    val expectedFee = BigDecimal("420000000000000")
      .divide(BigDecimal.TEN.pow(18), 18, RoundingMode.HALF_UP)

    assertEquals("Fee should be 0.00042 ETH", expectedFee, record.fee)
  }

  @Test
  fun testParseEvmTransaction_failedTransaction() {
    val tx = createEtherscanTx(
      hash = "0xfailed_tx",
      from = walletAddress,
      to = "0xRecipient",
      value = "1000000000000000000",
      gasUsed = "21000",
      gasPrice = "20000000000",
      timeStamp = "1700300000",
      confirmations = "5",
      blockNumber = "18000300",
      isError = "1",
      txReceiptStatus = "0",
    )

    val record = EvmManager.parseEvmTransaction(tx, walletAddress, "ETH")

    assertTrue("Failed tx should have isError=true", record.isError)
  }

  @Test
  fun testParseEvmTransaction_zeroValue() {
    // Contract interaction with 0 ETH value (e.g., token approval)
    val tx = createEtherscanTx(
      hash = "0xzero_value_tx",
      from = walletAddress,
      to = "0xContractAddress",
      value = "0",
      gasUsed = "46000",
      gasPrice = "25000000000",
      timeStamp = "1700400000",
      confirmations = "3",
      blockNumber = "18000400",
    )

    val record = EvmManager.parseEvmTransaction(tx, walletAddress, "ETH")

    assertEquals(
      "Amount should be 0",
      BigDecimal.ZERO.setScale(18, RoundingMode.UNNECESSARY),
      record.amount,
    )
  }

  @Test
  fun testParseEvmTransaction_selfTransaction() {
    val tx = createEtherscanTx(
      hash = "0xself_tx",
      from = walletAddress,
      to = walletAddress,
      value = "500000000000000000",
      gasUsed = "21000",
      gasPrice = "20000000000",
      timeStamp = "1700500000",
      confirmations = "2",
      blockNumber = "18000500",
    )

    val record = EvmManager.parseEvmTransaction(tx, walletAddress, "ETH")

    assertEquals("Direction should be SELF", TxDirection.SELF, record.direction)
    assertEquals("Counterparty should be wallet address (to)", walletAddress, record.counterpartyAddress)
  }

  @Test
  fun testParseEvmTransaction_bnbCurrency() {
    val tx = createEtherscanTx(
      hash = "0xbnb_tx",
      from = "0xSender",
      to = walletAddress,
      value = "1000000000000000000",
      gasUsed = "21000",
      gasPrice = "5000000000",
      timeStamp = "1700600000",
      confirmations = "100",
      blockNumber = "30000000",
    )

    val record = EvmManager.parseEvmTransaction(tx, walletAddress, "BNB")

    assertEquals("Currency should be BNB", "BNB", record.currency)
  }

  @Test
  fun testParseEvmTransaction_invalidNumericValues() {
    // Edge case: non-numeric strings in numeric fields
    val tx = createEtherscanTx(
      hash = "0xinvalid_nums",
      from = walletAddress,
      to = "0xRecipient",
      value = "not_a_number",
      gasUsed = "not_a_number",
      gasPrice = "not_a_number",
      timeStamp = "not_a_number",
      confirmations = "not_a_number",
      blockNumber = "not_a_number",
    )

    val record = EvmManager.parseEvmTransaction(tx, walletAddress, "ETH")

    // Should not crash, should default to zero/0
    assertEquals("Amount should be 0 for invalid value", BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP), record.amount)
    assertEquals("Fee should be 0 for invalid gas values", BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP), record.fee)
    assertEquals("Timestamp should be 0 for invalid timeStamp", 0L, record.timestamp)
    assertEquals("Confirmations should be 0 for invalid value", 0, record.confirmations)
    assertEquals("Block number should be 0 for invalid value", 0L, record.blockNumber)
  }

  // ===== fetchTransactionHistory integration tests (with mocks) =====

  @Test
  fun testFetchTransactionHistory_parsesResponse() = runTest {
    val etherscanApi: EtherscanApi = mock()

    val txList = listOf(
      createEtherscanTx(
        hash = "0xtx1",
        from = "0xSender1",
        to = walletAddress,
        value = "1000000000000000000",
        gasUsed = "21000",
        gasPrice = "20000000000",
        timeStamp = "1700000000",
        confirmations = "50",
        blockNumber = "18000000",
      ),
      createEtherscanTx(
        hash = "0xtx2",
        from = walletAddress,
        to = "0xRecipient2",
        value = "500000000000000000",
        gasUsed = "21000",
        gasPrice = "25000000000",
        timeStamp = "1700100000",
        confirmations = "40",
        blockNumber = "18000100",
      ),
    )

    whenever(etherscanApi.getTransactions(any(), any(), any(), any(), any(), any(), any(), any()))
      .thenReturn(EtherscanResponse(status = "1", message = "OK", result = txList))

    val history = evmManager.fetchTransactionHistory(
      address = walletAddress,
      chainId = 1L,
      currency = "ETH",
      apiKey = "testkey",
      etherscanApi = etherscanApi,
    )

    assertNotNull("History should not be null", history)
    assertEquals("Should have 2 transactions", 2, history!!.size)

    // Verify parsing
    assertEquals("First tx should be IN", TxDirection.IN, history[0].direction)
    assertEquals("Second tx should be OUT", TxDirection.OUT, history[1].direction)
    assertEquals("First tx hash", "0xtx1", history[0].hash)
    assertEquals("Second tx hash", "0xtx2", history[1].hash)
  }

  @Test
  fun testFetchTransactionHistory_emptyResponse() = runTest {
    val etherscanApi: EtherscanApi = mock()

    whenever(etherscanApi.getTransactions(any(), any(), any(), any(), any(), any(), any(), any()))
      .thenReturn(EtherscanResponse(status = "0", message = "No transactions found", result = emptyList()))

    val history = evmManager.fetchTransactionHistory(
      address = walletAddress,
      chainId = 1L,
      currency = "ETH",
      apiKey = "testkey",
      etherscanApi = etherscanApi,
    )

    assertNotNull("History should not be null for empty result", history)
    assertTrue("History should be empty", history!!.isEmpty())
  }

  @Test
  fun testFetchTransactionHistory_networkError_returnsNull() = runTest {
    val etherscanApi: EtherscanApi = mock()

    whenever(etherscanApi.getTransactions(any(), any(), any(), any(), any(), any(), any(), any()))
      .thenThrow(RuntimeException("Network error"))

    val history = evmManager.fetchTransactionHistory(
      address = walletAddress,
      chainId = 1L,
      currency = "ETH",
      apiKey = "testkey",
      etherscanApi = etherscanApi,
    )

    assertNull("History should be null on network error (offline mode)", history)
  }

  // ===== Helper =====

  private fun createEtherscanTx(
    hash: String = "0xdefault",
    from: String = "0xSender",
    to: String = "0xRecipient",
    value: String = "0",
    gasUsed: String = "21000",
    gasPrice: String = "20000000000",
    timeStamp: String = "1700000000",
    confirmations: String = "10",
    blockNumber: String = "18000000",
    isError: String = "0",
    txReceiptStatus: String = "1",
  ): EtherscanTransaction {
    return EtherscanTransaction(
      blockNumber = blockNumber,
      timeStamp = timeStamp,
      hash = hash,
      from = from,
      to = to,
      value = value,
      gas = "21000",
      gasPrice = gasPrice,
      gasUsed = gasUsed,
      isError = isError,
      txReceiptStatus = txReceiptStatus,
      confirmations = confirmations,
    )
  }
}
