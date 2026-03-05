package com.mcw.core.btc

import com.mcw.core.network.TxDirection
import com.mcw.core.network.api.BitpayApi
import com.mcw.core.network.api.BlockcypherAddressResponse
import com.mcw.core.network.api.BlockcypherApi
import com.mcw.core.network.api.BlockcypherTxRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

/**
 * Unit tests for BTC transaction history parsing.
 *
 * TDD anchors from task 12:
 * - testParseBtcTransaction — Blockcypher response -> TransactionRecord with correct direction/amount
 * - testDetermineDirectionIn — tx to my address -> IN
 * - testDetermineDirectionOut — tx from my address -> OUT
 * - testDetermineDirectionSelf — tx from my address to my address -> SELF
 *
 * Tests use real Blockcypher API response fixture format.
 */
class BtcHistoryTest {

  private lateinit var bitpayApi: BitpayApi
  private lateinit var blockcypherApi: BlockcypherApi
  private lateinit var btcManager: BtcManager

  private val walletAddress = "1DEP8i3QJCsjoHoFY2n4l3L2X8GKEyJSbb"

  @Before
  fun setUp() {
    bitpayApi = mock()
    blockcypherApi = mock()
    btcManager = BtcManager(bitpayApi, blockcypherApi)
  }

  // ===== Direction determination tests =====

  @Test
  fun testDetermineDirectionIn_receivedFunds() {
    // tx_input_n == -1 means this address received funds (an output belongs to this address)
    val txRef = BlockcypherTxRef(
      txHash = "abc123",
      blockHeight = 100000,
      txInputN = -1,
      txOutputN = 0,
      value = 50000,
      confirmations = 6,
      confirmed = "2024-01-15T10:30:00Z",
    )

    val record = btcManager.parseBtcTxRefs("abc123", listOf(txRef), walletAddress)

    assertEquals("Direction should be IN when tx_input_n == -1", TxDirection.IN, record.direction)
  }

  @Test
  fun testDetermineDirectionOut_sentFunds() {
    // tx_input_n >= 0 means this address spent funds (an input belongs to this address)
    val txRef = BlockcypherTxRef(
      txHash = "def456",
      blockHeight = 100001,
      txInputN = 0,
      txOutputN = 0,
      value = 30000,
      confirmations = 3,
      confirmed = "2024-01-16T12:00:00Z",
    )

    val record = btcManager.parseBtcTxRefs("def456", listOf(txRef), walletAddress)

    assertEquals("Direction should be OUT when tx_input_n >= 0", TxDirection.OUT, record.direction)
  }

  @Test
  fun testDetermineDirectionSelf_sentToSelf() {
    // Both input (tx_input_n >= 0) and output (tx_input_n == -1) belong to wallet address
    // in the same transaction => SELF
    val refs = listOf(
      BlockcypherTxRef(
        txHash = "self123",
        blockHeight = 100002,
        txInputN = 0,  // spent from this address
        txOutputN = 0,
        value = 100000,
        confirmations = 10,
        confirmed = "2024-01-17T14:00:00Z",
      ),
      BlockcypherTxRef(
        txHash = "self123",
        blockHeight = 100002,
        txInputN = -1,  // received back to this address
        txOutputN = 1,
        value = 90000,
        confirmations = 10,
        confirmed = "2024-01-17T14:00:00Z",
      ),
    )

    val record = btcManager.parseBtcTxRefs("self123", refs, walletAddress)

    assertEquals("Direction should be SELF when both IN and OUT refs exist", TxDirection.SELF, record.direction)
  }

  // ===== Transaction parsing tests =====

  @Test
  fun testParseBtcTransaction_receivedWithCorrectAmount() {
    // Simulate a Blockcypher txref where wallet received 0.00050000 BTC (50000 satoshis)
    val txRef = BlockcypherTxRef(
      txHash = "14b1052855bbf6561bc4db8aa501762e7cc20f0a7be59e76e29ac5da61da225e",
      blockHeight = 302013,
      txInputN = -1,
      txOutputN = 0,
      value = 50000,
      confirmations = 87238,
      confirmed = "2014-05-22T03:46:25Z",
    )

    val record = btcManager.parseBtcTxRefs(
      "14b1052855bbf6561bc4db8aa501762e7cc20f0a7be59e76e29ac5da61da225e",
      listOf(txRef),
      walletAddress,
    )

    assertEquals("Hash should match", "14b1052855bbf6561bc4db8aa501762e7cc20f0a7be59e76e29ac5da61da225e", record.hash)
    assertEquals("Direction should be IN", TxDirection.IN, record.direction)
    assertEquals("Amount should be 0.00050000 BTC", BigDecimal("0.00050000"), record.amount)
    assertEquals("Currency should be BTC", "BTC", record.currency)
    assertEquals("Confirmations should be 87238", 87238, record.confirmations)
    assertEquals("Block height should be 302013", 302013L, record.blockNumber)
    assertTrue("Timestamp should be > 0", record.timestamp > 0)
  }

  @Test
  fun testParseBtcTransaction_sentWithCorrectAmount() {
    val txRef = BlockcypherTxRef(
      txHash = "sent_tx_hash_abc",
      blockHeight = 400000,
      txInputN = 0,
      txOutputN = 0,
      value = 200000,
      confirmations = 50,
      confirmed = "2024-06-01T08:00:00Z",
    )

    val record = btcManager.parseBtcTxRefs("sent_tx_hash_abc", listOf(txRef), walletAddress)

    assertEquals("Direction should be OUT", TxDirection.OUT, record.direction)
    assertEquals("Amount should be 0.00200000 BTC", BigDecimal("0.00200000"), record.amount)
    assertEquals("Currency should be BTC", "BTC", record.currency)
    assertEquals("Block height should be 400000", 400000L, record.blockNumber)
  }

  @Test
  fun testParseBtcTransaction_multipleInputOutputRefs() {
    // Simulate a transaction where the address has 2 received outputs
    val refs = listOf(
      BlockcypherTxRef(
        txHash = "multi_ref_tx",
        blockHeight = 500000,
        txInputN = -1,
        txOutputN = 0,
        value = 30000,
        confirmations = 20,
        confirmed = "2024-07-01T12:00:00Z",
      ),
      BlockcypherTxRef(
        txHash = "multi_ref_tx",
        blockHeight = 500000,
        txInputN = -1,
        txOutputN = 2,
        value = 20000,
        confirmations = 20,
        confirmed = "2024-07-01T12:00:00Z",
      ),
    )

    val record = btcManager.parseBtcTxRefs("multi_ref_tx", refs, walletAddress)

    assertEquals("Direction should be IN", TxDirection.IN, record.direction)
    // 30000 + 20000 = 50000 satoshis = 0.00050000 BTC
    assertEquals("Amount should sum both outputs: 0.00050000 BTC", BigDecimal("0.00050000"), record.amount)
  }

  @Test
  fun testParseBtcTransaction_selfTransaction_amountIsReceivedValue() {
    val refs = listOf(
      BlockcypherTxRef(
        txHash = "self_tx_full",
        blockHeight = 600000,
        txInputN = 0,
        txOutputN = 0,
        value = 100000,
        confirmations = 5,
        confirmed = "2024-08-01T16:00:00Z",
      ),
      BlockcypherTxRef(
        txHash = "self_tx_full",
        blockHeight = 600000,
        txInputN = -1,
        txOutputN = 1,
        value = 95000,
        confirmations = 5,
        confirmed = "2024-08-01T16:00:00Z",
      ),
    )

    val record = btcManager.parseBtcTxRefs("self_tx_full", refs, walletAddress)

    assertEquals("Direction should be SELF", TxDirection.SELF, record.direction)
    // For SELF transactions, amount is the received value (net after fee)
    assertEquals("Amount should be received value: 0.00095000 BTC", BigDecimal("0.00095000"), record.amount)
  }

  @Test
  fun testParseBtcTransaction_zeroConfirmations() {
    val txRef = BlockcypherTxRef(
      txHash = "unconfirmed_tx",
      blockHeight = 0,
      txInputN = -1,
      txOutputN = 0,
      value = 10000,
      confirmations = 0,
      confirmed = null,
    )

    val record = btcManager.parseBtcTxRefs("unconfirmed_tx", listOf(txRef), walletAddress)

    assertEquals("Confirmations should be 0", 0, record.confirmations)
    assertEquals("Block height should be 0", 0L, record.blockNumber)
    assertEquals("Timestamp should be 0 for null confirmed", 0L, record.timestamp)
  }

  // ===== Timestamp parsing tests =====

  @Test
  fun testParseBlockcypherTimestamp_validFormat() {
    val timestamp = BtcManager.parseBlockcypherTimestamp("2014-05-22T03:46:25Z")
    // Verify it parses to a reasonable epoch second value (May 2014)
    assertTrue("Timestamp should be positive", timestamp > 0)
    // 2014-05-22T03:46:25Z should be around 1400727985 +/- timezone offset
    // The exact value depends on SimpleDateFormat parsing; we verify range
    assertTrue("Timestamp should be in May 2014 range", timestamp in 1400700000L..1400760000L)
  }

  @Test
  fun testParseBlockcypherTimestamp_null() {
    val timestamp = BtcManager.parseBlockcypherTimestamp(null)
    assertEquals("Null timestamp should return 0", 0L, timestamp)
  }

  @Test
  fun testParseBlockcypherTimestamp_invalidFormat() {
    val timestamp = BtcManager.parseBlockcypherTimestamp("not-a-date")
    assertEquals("Invalid timestamp should return 0", 0L, timestamp)
  }

  @Test
  fun testParseBlockcypherTimestamp_emptyString() {
    val timestamp = BtcManager.parseBlockcypherTimestamp("")
    assertEquals("Empty timestamp should return 0", 0L, timestamp)
  }

  // ===== fetchTransactionHistory integration tests (with mocks) =====

  @Test
  fun testFetchTransactionHistory_parsesResponse() = runTest {
    val response = BlockcypherAddressResponse(
      address = walletAddress,
      balance = 100000,
      nTx = 2,
      txrefs = listOf(
        BlockcypherTxRef(
          txHash = "tx1_received",
          blockHeight = 300000,
          txInputN = -1,
          txOutputN = 0,
          value = 60000,
          confirmations = 100,
          confirmed = "2024-01-01T00:00:00Z",
        ),
        BlockcypherTxRef(
          txHash = "tx2_sent",
          blockHeight = 300010,
          txInputN = 0,
          txOutputN = 0,
          value = 20000,
          confirmations = 90,
          confirmed = "2024-01-02T00:00:00Z",
        ),
      ),
      unconfirmedTxrefs = null,
    )

    whenever(blockcypherApi.getAddressTransactions(eq(walletAddress), any()))
      .thenReturn(response)

    val history = btcManager.fetchTransactionHistory(walletAddress)

    assertNotNull("History should not be null", history)
    assertEquals("Should have 2 transactions", 2, history!!.size)

    // Sorted by timestamp descending: tx2_sent (Jan 2) before tx1_received (Jan 1)
    assertEquals("First tx should be the most recent", "tx2_sent", history[0].hash)
    assertEquals("Second tx should be older", "tx1_received", history[1].hash)

    assertEquals("tx2 should be OUT", TxDirection.OUT, history[0].direction)
    assertEquals("tx1 should be IN", TxDirection.IN, history[1].direction)
  }

  @Test
  fun testFetchTransactionHistory_emptyResponse() = runTest {
    val response = BlockcypherAddressResponse(
      address = walletAddress,
      balance = 0,
      nTx = 0,
      txrefs = null,
      unconfirmedTxrefs = null,
    )

    whenever(blockcypherApi.getAddressTransactions(eq(walletAddress), any()))
      .thenReturn(response)

    val history = btcManager.fetchTransactionHistory(walletAddress)

    assertNotNull("History should not be null", history)
    assertTrue("History should be empty", history!!.isEmpty())
  }

  @Test
  fun testFetchTransactionHistory_networkError_returnsNull() = runTest {
    whenever(blockcypherApi.getAddressTransactions(eq(walletAddress), any()))
      .thenThrow(RuntimeException("Network error"))

    val history = btcManager.fetchTransactionHistory(walletAddress)

    assertNull("History should be null on network error (offline mode)", history)
  }

  @Test
  fun testFetchTransactionHistory_includesUnconfirmed() = runTest {
    val response = BlockcypherAddressResponse(
      address = walletAddress,
      balance = 100000,
      nTx = 1,
      txrefs = listOf(
        BlockcypherTxRef(
          txHash = "confirmed_tx",
          blockHeight = 300000,
          txInputN = -1,
          txOutputN = 0,
          value = 60000,
          confirmations = 100,
          confirmed = "2024-01-01T00:00:00Z",
        ),
      ),
      unconfirmedTxrefs = listOf(
        BlockcypherTxRef(
          txHash = "unconfirmed_tx",
          blockHeight = 0,
          txInputN = -1,
          txOutputN = 0,
          value = 10000,
          confirmations = 0,
          confirmed = null,
        ),
      ),
    )

    whenever(blockcypherApi.getAddressTransactions(eq(walletAddress), any()))
      .thenReturn(response)

    val history = btcManager.fetchTransactionHistory(walletAddress)

    assertNotNull("History should not be null", history)
    assertEquals("Should include both confirmed and unconfirmed", 2, history!!.size)

    // Confirmed tx has timestamp > 0, unconfirmed has 0
    val unconfirmed = history.find { it.hash == "unconfirmed_tx" }
    assertNotNull("Should find unconfirmed tx", unconfirmed)
    assertEquals("Unconfirmed tx should have 0 confirmations", 0, unconfirmed!!.confirmations)
  }

  @Test
  fun testFetchTransactionHistory_mergesDuplicateTxRefs() = runTest {
    // Same tx_hash appears twice (address has 2 outputs in the same tx)
    val response = BlockcypherAddressResponse(
      address = walletAddress,
      balance = 100000,
      nTx = 1,
      txrefs = listOf(
        BlockcypherTxRef(
          txHash = "merged_tx",
          blockHeight = 300000,
          txInputN = -1,
          txOutputN = 0,
          value = 30000,
          confirmations = 50,
          confirmed = "2024-01-01T00:00:00Z",
        ),
        BlockcypherTxRef(
          txHash = "merged_tx",
          blockHeight = 300000,
          txInputN = -1,
          txOutputN = 2,
          value = 20000,
          confirmations = 50,
          confirmed = "2024-01-01T00:00:00Z",
        ),
      ),
      unconfirmedTxrefs = null,
    )

    whenever(blockcypherApi.getAddressTransactions(eq(walletAddress), any()))
      .thenReturn(response)

    val history = btcManager.fetchTransactionHistory(walletAddress)

    assertNotNull("History should not be null", history)
    assertEquals("Duplicate refs should be merged into 1 record", 1, history!!.size)
    assertEquals("Merged amount should be 0.00050000 BTC", BigDecimal("0.00050000"), history[0].amount)
  }
}
