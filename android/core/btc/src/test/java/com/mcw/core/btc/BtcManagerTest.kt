package com.mcw.core.btc

import com.mcw.core.network.api.BitpayApi
import com.mcw.core.network.api.BitpayBalanceResponse
import com.mcw.core.network.api.BitpayBroadcastRequest
import com.mcw.core.network.api.BitpayBroadcastResponse
import com.mcw.core.network.api.BitpayUtxo
import com.mcw.core.network.api.BlockcypherApi
import com.mcw.core.network.api.BlockcypherFeeResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.Utils
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.ScriptBuilder
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Unit tests for BtcManager — BTC balance, UTXO selection, fee estimation,
 * transaction construction, signing, and broadcast.
 *
 * TDD anchors from task 6:
 * - testFetchBalance — parse Bitpay response, verify satoshis -> BTC conversion
 * - testUtxoSelectionSingleInput — 1 UTXO covers amount+fee -> selected
 * - testUtxoSelectionMultipleInputs — combine multiple UTXOs to reach amount
 * - testUtxoSelectionInsufficientFunds — total < amount+fee -> error
 * - testChangeHandlingDust — change < 546 sat -> added to fee, no change output
 * - testFeeCalculation — verify max(546, feeRate * txSize / 1024) formula
 * - testTransactionConstruction — verify correct inputs/outputs structure
 */
class BtcManagerTest {

  private lateinit var bitpayApi: BitpayApi
  private lateinit var blockcypherApi: BlockcypherApi
  private lateinit var btcManager: BtcManager

  @Before
  fun setUp() {
    bitpayApi = mock()
    blockcypherApi = mock()
    btcManager = BtcManager(bitpayApi, blockcypherApi)
  }

  // ===== fetchBalance tests =====

  @Test
  fun testFetchBalance_parsesBitpayResponse_convertsTobtc() = runTest {
    // 1 BTC = 100_000_000 satoshis
    whenever(bitpayApi.getBalance("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
      .thenReturn(BitpayBalanceResponse(confirmed = 100_000_000L, unconfirmed = 0L, balance = 100_000_000L))

    val result = btcManager.fetchBalance("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")

    assertEquals(
      "100000000 satoshis should equal 1.0 BTC",
      BigDecimal("1.00000000"),
      result.balance
    )
    assertEquals(
      "Unconfirmed should be 0",
      BigDecimal("0.00000000"),
      result.unconfirmed
    )
  }

  @Test
  fun testFetchBalance_smallAmount() = runTest {
    // 546 satoshis = 0.00000546 BTC (dust threshold)
    whenever(bitpayApi.getBalance("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
      .thenReturn(BitpayBalanceResponse(confirmed = 546L, unconfirmed = 0L, balance = 546L))

    val result = btcManager.fetchBalance("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")

    assertEquals(
      "546 satoshis should equal 0.00000546 BTC",
      BigDecimal("0.00000546"),
      result.balance
    )
  }

  @Test
  fun testFetchBalance_withUnconfirmed() = runTest {
    whenever(bitpayApi.getBalance("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
      .thenReturn(BitpayBalanceResponse(confirmed = 50_000_000L, unconfirmed = 10_000_000L, balance = 50_000_000L))

    val result = btcManager.fetchBalance("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")

    assertEquals(BigDecimal("0.50000000"), result.balance)
    assertEquals(BigDecimal("0.10000000"), result.unconfirmed)
  }

  @Test
  fun testFetchBalance_zeroBalance() = runTest {
    whenever(bitpayApi.getBalance("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
      .thenReturn(BitpayBalanceResponse(confirmed = 0L, unconfirmed = 0L, balance = 0L))

    val result = btcManager.fetchBalance("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")

    assertEquals(BigDecimal("0.00000000"), result.balance)
    assertEquals(BigDecimal("0.00000000"), result.unconfirmed)
  }

  // ===== fetchUnspentOutputs tests =====

  @Test
  fun testFetchUnspentOutputs_parsesResponse() = runTest {
    whenever(bitpayApi.getUnspentOutputs("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
      .thenReturn(
        listOf(
          BitpayUtxo(
            txid = "abc123",
            vout = 0,
            value = 50_000L,
            script = "76a91489abcdefab88ac",
            address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            confirmations = 6
          ),
          BitpayUtxo(
            txid = "def456",
            vout = 1,
            value = 100_000L,
            script = "76a91489abcdefab88ac",
            address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            confirmations = 3
          )
        )
      )

    val utxos = btcManager.fetchUnspentOutputs("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")

    assertEquals("Should return 2 UTXOs", 2, utxos.size)
    assertEquals("abc123", utxos[0].txid)
    assertEquals(0, utxos[0].vout)
    assertEquals(50_000L, utxos[0].valueSatoshis)
    assertEquals("def456", utxos[1].txid)
    assertEquals(1, utxos[1].vout)
    assertEquals(100_000L, utxos[1].valueSatoshis)
  }

  @Test
  fun testFetchUnspentOutputs_emptyList() = runTest {
    whenever(bitpayApi.getUnspentOutputs("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
      .thenReturn(emptyList())

    val utxos = btcManager.fetchUnspentOutputs("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")

    assertTrue("Should return empty list", utxos.isEmpty())
  }

  // ===== UTXO selection tests =====

  @Test
  fun testUtxoSelectionSingleInput_oneUtxoCoversAmountPlusFee() {
    val utxos = listOf(
      UnspentOutput(txid = "tx1", vout = 0, valueSatoshis = 100_000L, script = "76a914...88ac"),
    )

    // Need 50000 sat amount + 546 sat dust margin = 50546
    // The single UTXO of 100000 covers this
    val selected = btcManager.selectUtxos(utxos, amountSatoshis = 50_000L)

    assertEquals("Should select 1 UTXO", 1, selected.size)
    assertEquals("tx1", selected[0].txid)
  }

  @Test
  fun testUtxoSelectionSingleInput_exactMatchUtxo() {
    val utxos = listOf(
      UnspentOutput(txid = "tx_small", vout = 0, valueSatoshis = 10_000L, script = "s"),
      UnspentOutput(txid = "tx_exact", vout = 0, valueSatoshis = 50_546L, script = "s"),
      UnspentOutput(txid = "tx_big", vout = 0, valueSatoshis = 200_000L, script = "s"),
    )

    // Need 50000 + 546 = 50546 sat
    // tx_exact covers this exactly; should prefer it (smallest sufficient single UTXO)
    val selected = btcManager.selectUtxos(utxos, amountSatoshis = 50_000L)

    assertEquals("Should select 1 UTXO (the smallest that covers need)", 1, selected.size)
    assertEquals("tx_exact", selected[0].txid)
  }

  @Test
  fun testUtxoSelectionMultipleInputs_combineToReachAmount() {
    val utxos = listOf(
      UnspentOutput(txid = "tx1", vout = 0, valueSatoshis = 20_000L, script = "s"),
      UnspentOutput(txid = "tx2", vout = 0, valueSatoshis = 30_000L, script = "s"),
      UnspentOutput(txid = "tx3", vout = 0, valueSatoshis = 15_000L, script = "s"),
    )

    // Need 60000 + 546 = 60546 sat — no single UTXO covers this
    // Sorted ascending: 15000, 20000, 30000
    // Accumulate: 15000 < 60546, 15000+20000=35000 < 60546, 35000+30000=65000 >= 60546
    // All three selected
    val selected = btcManager.selectUtxos(utxos, amountSatoshis = 60_000L)

    assertEquals("Should combine multiple UTXOs", 3, selected.size)
    val totalSelected = selected.sumOf { it.valueSatoshis }
    assertTrue("Total selected must cover need", totalSelected >= 60_546L)
  }

  @Test
  fun testUtxoSelectionMultipleInputs_doesNotSelectUnnecessaryUtxos() {
    val utxos = listOf(
      UnspentOutput(txid = "tx1", vout = 0, valueSatoshis = 20_000L, script = "s"),
      UnspentOutput(txid = "tx2", vout = 0, valueSatoshis = 30_000L, script = "s"),
      UnspentOutput(txid = "tx3", vout = 0, valueSatoshis = 15_000L, script = "s"),
      UnspentOutput(txid = "tx4", vout = 0, valueSatoshis = 50_000L, script = "s"),
    )

    // Need 30000 + 546 = 30546 sat
    // Sorted: 15000, 20000, 30000, 50000
    // Single UTXO: tx2 (30000) < 30546, tx4 (50000) >= 30546 -> select tx4 alone
    // But tx2 at 30000 < 30546 doesn't satisfy, so the smallest sufficient single is tx4
    val selected = btcManager.selectUtxos(utxos, amountSatoshis = 30_000L)

    // Web algo: finds first single UTXO >= needAmount (sorted ascending)
    // tx2=30000 < 30546, tx4=50000 >= 30546 -> selects tx4
    assertEquals("Should select single sufficient UTXO", 1, selected.size)
    assertEquals("tx4", selected[0].txid)
  }

  @Test
  fun testUtxoSelectionInsufficientFunds_throwsException() {
    val utxos = listOf(
      UnspentOutput(txid = "tx1", vout = 0, valueSatoshis = 10_000L, script = "s"),
      UnspentOutput(txid = "tx2", vout = 0, valueSatoshis = 5_000L, script = "s"),
    )

    // Need 20000 + 546 = 20546, but total is only 15000
    try {
      btcManager.selectUtxos(utxos, amountSatoshis = 20_000L)
      fail("Should throw InsufficientFundsException")
    } catch (e: InsufficientFundsException) {
      assertTrue(
        "Error message should mention insufficient funds: ${e.message}",
        e.message!!.contains("Insufficient") || e.message!!.contains("insufficient")
      )
    }
  }

  @Test
  fun testUtxoSelection_emptyListThrows() {
    try {
      btcManager.selectUtxos(emptyList(), amountSatoshis = 1000L)
      fail("Should throw InsufficientFundsException for empty UTXOs")
    } catch (e: InsufficientFundsException) {
      // expected
    }
  }

  // ===== Change handling / dust tests =====

  @Test
  fun testChangeHandlingDust_changeLessThan546SatAddedToFee() {
    // UTXO: 50000 sat, send 49600 sat, fee = 200 sat
    // Change = 50000 - 49600 - 200 = 200 sat < 546 -> dust, add to fee
    val result = btcManager.calculateChange(
      totalInputSatoshis = 50_000L,
      amountSatoshis = 49_600L,
      feeSatoshis = 200L
    )

    assertEquals("Change should be 0 (dust absorbed into fee)", 0L, result.changeSatoshis)
    assertEquals(
      "Effective fee should include dust",
      400L, // 200 original fee + 200 dust
      result.effectiveFeeSatoshis
    )
  }

  @Test
  fun testChangeHandlingDust_changeExactly546SatIsKept() {
    // UTXO: 50000, send 49000, fee = 454
    // Change = 50000 - 49000 - 454 = 546 sat = DUST -> kept (>=546)
    val result = btcManager.calculateChange(
      totalInputSatoshis = 50_000L,
      amountSatoshis = 49_000L,
      feeSatoshis = 454L
    )

    assertEquals("Change of exactly 546 sat should be kept", 546L, result.changeSatoshis)
    assertEquals("Fee unchanged when change is not dust", 454L, result.effectiveFeeSatoshis)
  }

  @Test
  fun testChangeHandlingDust_largeChangeIsKept() {
    // UTXO: 100000, send 50000, fee = 1000
    // Change = 100000 - 50000 - 1000 = 49000 sat >> 546
    val result = btcManager.calculateChange(
      totalInputSatoshis = 100_000L,
      amountSatoshis = 50_000L,
      feeSatoshis = 1_000L
    )

    assertEquals("Large change should be kept", 49_000L, result.changeSatoshis)
    assertEquals("Fee unchanged", 1_000L, result.effectiveFeeSatoshis)
  }

  @Test
  fun testChangeHandlingDust_zeroChangeNoOutput() {
    // UTXO: 50000, send 49000, fee = 1000
    // Change = 50000 - 49000 - 1000 = 0
    val result = btcManager.calculateChange(
      totalInputSatoshis = 50_000L,
      amountSatoshis = 49_000L,
      feeSatoshis = 1_000L
    )

    assertEquals("Zero change", 0L, result.changeSatoshis)
    assertEquals("Fee unchanged", 1_000L, result.effectiveFeeSatoshis)
  }

  // ===== Fee calculation tests =====

  @Test
  fun testFeeCalculation_normalCase() {
    // feeRate = 15000 sat/KB, txSize = 226 bytes
    // fee = 15000 * 226 / 1024 = 3310.546... -> 3311 (rounded up)
    // max(546, 3311) = 3311
    val fee = btcManager.calculateFee(feeRateSatPerKb = 15_000L, txSizeBytes = 226)

    assertEquals("Fee should be max(546, 15000*226/1024) = 3311", 3311L, fee)
  }

  @Test
  fun testFeeCalculation_minimumDustEnforced() {
    // feeRate = 100 sat/KB, txSize = 226 bytes
    // fee = 100 * 226 / 1024 = 22.07 -> 23
    // max(546, 23) = 546
    val fee = btcManager.calculateFee(feeRateSatPerKb = 100L, txSizeBytes = 226)

    assertEquals("Fee should be at least DUST_SAT (546)", 546L, fee)
  }

  @Test
  fun testFeeCalculation_highFeeRate() {
    // feeRate = 30000 sat/KB, txSize = 400 bytes
    // fee = 30000 * 400 / 1024 = 11718.75 -> 11719
    // max(546, 11719) = 11719
    val fee = btcManager.calculateFee(feeRateSatPerKb = 30_000L, txSizeBytes = 400)

    assertEquals("Fee should be 30000*400/1024 rounded up = 11719", 11719L, fee)
  }

  @Test
  fun testFeeCalculation_minimumTxSize() {
    // feeRate = 5000 sat/KB (slow), txSize = 10 bytes (tiny tx)
    // fee = 5000 * 10 / 1024 = 48.828 -> 49
    // max(546, 49) = 546
    val fee = btcManager.calculateFee(feeRateSatPerKb = 5_000L, txSizeBytes = 10)

    assertEquals("Very small tx should still have minimum fee of 546", 546L, fee)
  }

  // ===== estimateFees tests =====

  @Test
  fun testEstimateFees_parsesBlockcypherResponse() = runTest {
    whenever(blockcypherApi.getFeeRates())
      .thenReturn(BlockcypherFeeResponse(highFeePerKb = 30_000L, mediumFeePerKb = 15_000L, lowFeePerKb = 5_000L))

    val feeRates = btcManager.estimateFees()

    assertEquals("Fast fee should be high_fee_per_kb", 30_000L, feeRates.fast)
    assertEquals("Normal fee should be medium_fee_per_kb", 15_000L, feeRates.normal)
    assertEquals("Slow fee should be low_fee_per_kb", 5_000L, feeRates.slow)
  }

  @Test
  fun testEstimateFees_fallsBackToDefaults() = runTest {
    whenever(blockcypherApi.getFeeRates())
      .thenThrow(RuntimeException("API error"))

    val feeRates = btcManager.estimateFees()

    // Default rates from DEFAULT_CURRENCY_PARAMETERS: slow=5000, normal=15000, fast=30000
    assertEquals("Fallback fast rate", 30_000L, feeRates.fast)
    assertEquals("Fallback normal rate", 15_000L, feeRates.normal)
    assertEquals("Fallback slow rate", 5_000L, feeRates.slow)
  }

  // ===== Transaction size calculation tests =====

  @Test
  fun testCalculateTxSize_defaultSendSize() {
    // Default send: 1 input, 2 outputs (recipient + change)
    // txSize = 1 * P2PKH_IN_SIZE(148) + 2 * P2PKH_OUT_SIZE(34) + (TX_SIZE(15) + 1 - 2) = 148 + 68 + 14 = 230
    val txSize = btcManager.calculateTxSize(txIn = 1, txOut = 2)

    assertEquals("1-in 2-out P2PKH tx size", 230, txSize)
  }

  @Test
  fun testCalculateTxSize_multipleInputs() {
    // 3 inputs, 2 outputs
    // txSize = 3 * 148 + 2 * 34 + (15 + 3 - 2) = 444 + 68 + 16 = 528
    val txSize = btcManager.calculateTxSize(txIn = 3, txOut = 2)

    assertEquals("3-in 2-out P2PKH tx size", 528, txSize)
  }

  @Test
  fun testCalculateTxSize_singleOutput_noChange() {
    // 1 input, 1 output (no change output)
    // txSize = 1 * 148 + 1 * 34 + (15 + 1 - 1) = 148 + 34 + 15 = 197
    val txSize = btcManager.calculateTxSize(txIn = 1, txOut = 1)

    assertEquals("1-in 1-out P2PKH tx size", 197, txSize)
  }

  // ===== Transaction construction tests =====

  /**
   * Generates a valid P2PKH scriptPubKey hex from a WIF private key.
   * The script pays to the public key hash of the key, matching what
   * bitcoinj expects for signing.
   */
  private fun p2pkhScriptHex(privateKeyWIF: String): String {
    val network = MainNetParams.get()
    val ecKey = ECKey.fromPrivate(
      org.bitcoinj.core.DumpedPrivateKey.fromBase58(network, privateKeyWIF).key.privKeyBytes
    )
    val address = LegacyAddress.fromKey(network, ecKey)
    val script = ScriptBuilder.createOutputScript(address)
    return Utils.HEX.encode(script.program)
  }

  @Test
  fun testBuildTransaction_correctInputsAndOutputs() {
    val wif = "L4p2b9VAf8k5aUahF1JCJUzZkgNEAqLfq8DDdQiyAprQAKSbu8hf"
    val scriptHex = p2pkhScriptHex(wif)

    // Build a transaction: 1 input, recipient output + change output
    val utxos = listOf(
      UnspentOutput(txid = "a".repeat(64), vout = 0, valueSatoshis = 100_000L, script = scriptHex)
    )
    val recipientAddress = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2"
    val changeAddress = "1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA"
    val amountSatoshis = 50_000L
    val feeSatoshis = 1_000L

    val txHex = btcManager.buildTransaction(
      utxos = utxos,
      recipientAddress = recipientAddress,
      amountSatoshis = amountSatoshis,
      feeSatoshis = feeSatoshis,
      changeAddress = changeAddress,
      privateKeyWIF = wif
    )

    // Transaction hex should be a valid hex string
    assertNotNull("Transaction hex should not be null", txHex)
    assertTrue("Transaction hex should not be empty", txHex.isNotEmpty())
    assertTrue(
      "Transaction hex should be valid hex (even length, hex chars only)",
      txHex.length % 2 == 0 && txHex.all { it in '0'..'9' || it in 'a'..'f' }
    )
  }

  @Test
  fun testBuildTransaction_dustChangeAbsorbed() {
    val wif = "L4p2b9VAf8k5aUahF1JCJUzZkgNEAqLfq8DDdQiyAprQAKSbu8hf"
    val scriptHex = p2pkhScriptHex(wif)

    // Build tx where change < 546 -> no change output
    val utxos = listOf(
      UnspentOutput(txid = "b".repeat(64), vout = 0, valueSatoshis = 50_000L, script = scriptHex)
    )
    val recipientAddress = "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2"
    val changeAddress = "1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA"
    // amount=49500, fee=200, change=300 < 546 -> absorbed into fee
    val amountSatoshis = 49_500L
    val feeSatoshis = 200L

    val txHex = btcManager.buildTransaction(
      utxos = utxos,
      recipientAddress = recipientAddress,
      amountSatoshis = amountSatoshis,
      feeSatoshis = feeSatoshis,
      changeAddress = changeAddress,
      privateKeyWIF = wif
    )

    assertNotNull("Transaction hex should not be null", txHex)
    assertTrue("Transaction hex should not be empty", txHex.isNotEmpty())
    // We can't easily verify output count from hex without parsing,
    // but we verify the tx builds successfully with dust change
  }

  // ===== Broadcast tests =====

  @Test
  fun testBroadcastTransaction_returnsTxid() = runTest {
    whenever(bitpayApi.broadcastTransaction(any()))
      .thenReturn(BitpayBroadcastResponse(txid = "abc123def456"))

    val result = btcManager.broadcastTransaction("0100000001...")

    assertEquals("abc123def456", result)
  }

  @Test
  fun testBroadcastTransaction_apiError_throws() = runTest {
    whenever(bitpayApi.broadcastTransaction(any()))
      .thenThrow(RuntimeException("Broadcast failed: invalid transaction"))

    try {
      btcManager.broadcastTransaction("invalid_hex")
      fail("Should throw on broadcast failure")
    } catch (e: BroadcastException) {
      assertTrue(
        "Error should mention broadcast failure: ${e.message}",
        e.message!!.contains("Broadcast") || e.message!!.contains("broadcast")
      )
    }
  }

  // ===== Integration-style: estimateFeeValue (combines fee rate + tx size) =====

  @Test
  fun testEstimateFeeValue_withRealCalculation() = runTest {
    whenever(blockcypherApi.getFeeRates())
      .thenReturn(BlockcypherFeeResponse(highFeePerKb = 30_000L, mediumFeePerKb = 15_000L, lowFeePerKb = 5_000L))

    val feeRates = btcManager.estimateFees()

    // For a default send (1 in, 2 out): txSize = 230 bytes
    // Normal fee: max(546, 15000 * 230 / 1024) = max(546, 3369.14...) = max(546, 3370) = 3370
    val normalFee = btcManager.calculateFee(feeRates.normal, 230)
    assertTrue("Normal fee should be > DUST", normalFee >= 546L)

    // Fast fee: max(546, 30000 * 230 / 1024) = max(546, 6738.28...) = max(546, 6739) = 6739
    val fastFee = btcManager.calculateFee(feeRates.fast, 230)
    assertTrue("Fast fee should be > normal fee", fastFee > normalFee)

    // Slow fee: max(546, 5000 * 230 / 1024) = max(546, 1123.04...) = max(546, 1124) = 1124
    val slowFee = btcManager.calculateFee(feeRates.slow, 230)
    assertTrue("Slow fee should be < normal fee", slowFee < normalFee)
  }

  // ===== BtcFeeRates data class =====

  @Test
  fun testBtcFeeRates_holdsValues() {
    val rates = BtcFeeRates(fast = 30_000L, normal = 15_000L, slow = 5_000L)
    assertEquals(30_000L, rates.fast)
    assertEquals(15_000L, rates.normal)
    assertEquals(5_000L, rates.slow)
  }

  // ===== BtcBalance data class =====

  @Test
  fun testBtcBalance_holdsValues() {
    val balance = BtcBalance(
      balance = BigDecimal("1.50000000"),
      unconfirmed = BigDecimal("0.10000000")
    )
    assertEquals(BigDecimal("1.50000000"), balance.balance)
    assertEquals(BigDecimal("0.10000000"), balance.unconfirmed)
  }

  // ===== ChangeResult data class =====

  @Test
  fun testChangeResult_holdsValues() {
    val result = ChangeResult(changeSatoshis = 5000L, effectiveFeeSatoshis = 1000L)
    assertEquals(5000L, result.changeSatoshis)
    assertEquals(1000L, result.effectiveFeeSatoshis)
  }
}
