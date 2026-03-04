package com.mcw.core.btc

import com.mcw.core.network.api.BitpayApi
import com.mcw.core.network.api.BitpayBroadcastRequest
import com.mcw.core.network.api.BlockcypherApi
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.ScriptBuilder
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.max

/**
 * Bitcoin operations: balance fetching, UTXO selection, fee estimation,
 * transaction construction, signing, and broadcast.
 *
 * Ports algorithm patterns from web wallet's btc.ts (Decision 11).
 *
 * API dependencies:
 * - Bitpay (Bitcore) API: balance, UTXOs, broadcast
 * - Blockcypher API: fee rate estimation
 *
 * Key constants (from web's TRANSACTION.ts):
 * - DUST_SAT = 546 — minimum viable UTXO value
 * - P2PKH_IN_SIZE = 148 bytes per input
 * - P2PKH_OUT_SIZE = 34 bytes per output
 * - TX_SIZE = 15 bytes overhead
 * - Default fee rates: slow=5000, normal=15000, fast=30000 sat/KB
 */
class BtcManager @Inject constructor(
  private val bitpayApi: BitpayApi,
  private val blockcypherApi: BlockcypherApi,
  private val networkParams: NetworkParameters = MainNetParams.get(),
) {

  companion object {
    /** Minimum viable UTXO value — outputs below this are uneconomical to spend */
    const val DUST_SAT = 546L

    /** Transaction size constants for P2PKH (from web's TRANSACTION.ts) */
    const val P2PKH_IN_SIZE = 148
    const val P2PKH_OUT_SIZE = 34
    const val TX_SIZE = 15

    /** Fallback fee rates in sat/KB (from web's DEFAULT_CURRENCY_PARAMETERS) */
    const val DEFAULT_FEE_SLOW = 5_000L
    const val DEFAULT_FEE_NORMAL = 15_000L
    const val DEFAULT_FEE_FAST = 30_000L

    /** Satoshis per BTC */
    private val SATOSHI_DIVISOR = BigDecimal("100000000")
  }

  // Secondary constructor for testing without Hilt
  constructor(
    bitpayApi: BitpayApi,
    blockcypherApi: BlockcypherApi,
  ) : this(bitpayApi, blockcypherApi, MainNetParams.get())

  /**
   * Fetches the BTC balance for an address from the Bitpay API.
   *
   * Converts satoshis to BTC using BigDecimal division for precision.
   * Web equivalent: btc.ts fetchBalance()
   *
   * @param address BTC address (P2PKH format)
   * @return BtcBalance with confirmed and unconfirmed amounts in BTC
   */
  suspend fun fetchBalance(address: String): BtcBalance {
    val response = bitpayApi.getBalance(address)
    return BtcBalance(
      balance = BigDecimal(response.balance).divide(SATOSHI_DIVISOR, 8, RoundingMode.HALF_EVEN),
      unconfirmed = BigDecimal(response.unconfirmed).divide(SATOSHI_DIVISOR, 8, RoundingMode.HALF_EVEN),
    )
  }

  /**
   * Fetches unspent transaction outputs for an address from the Bitpay API.
   *
   * Maps from Bitpay's response format to our UnspentOutput model.
   * Web equivalent: btc.ts fetchUnspents()
   *
   * @param address BTC address
   * @return list of unspent outputs available for spending
   */
  suspend fun fetchUnspentOutputs(address: String): List<UnspentOutput> {
    val response = bitpayApi.getUnspentOutputs(address)
    return response.map { utxo ->
      UnspentOutput(
        txid = utxo.txid,
        vout = utxo.vout,
        valueSatoshis = utxo.value,
        script = utxo.script,
      )
    }
  }

  /**
   * Selects UTXOs to cover the requested amount plus DUST margin.
   *
   * Algorithm ported from web's btc.ts prepareUnspents():
   * 1. Sort UTXOs ascending by value
   * 2. Try to find a single UTXO that covers amount + DUST
   *    (smallest sufficient single input to minimize change)
   * 3. If no single UTXO suffices, accumulate from smallest upward
   *    until the total covers amount + DUST
   * 4. If total of all UTXOs is insufficient, throw InsufficientFundsException
   *
   * @param utxos available unspent outputs
   * @param amountSatoshis amount to send in satoshis
   * @return selected UTXOs sufficient for the transaction
   * @throws InsufficientFundsException if total UTXOs < amount + DUST
   */
  fun selectUtxos(utxos: List<UnspentOutput>, amountSatoshis: Long): List<UnspentOutput> {
    val needAmount = amountSatoshis + DUST_SAT

    // Sort ascending by value (smallest first)
    val sorted = utxos.sortedBy { it.valueSatoshis }

    // Try to find smallest single UTXO that covers the full need
    val singleUtxo = sorted.firstOrNull { it.valueSatoshis >= needAmount }
    if (singleUtxo != null) {
      return listOf(singleUtxo)
    }

    // Accumulate from smallest upward
    var accumulated = 0L
    val selected = mutableListOf<UnspentOutput>()
    for (utxo in sorted) {
      if (accumulated >= needAmount) break
      selected.add(utxo)
      accumulated += utxo.valueSatoshis
    }

    if (accumulated < needAmount) {
      throw InsufficientFundsException(
        "Insufficient funds: have $accumulated sat, need $needAmount sat " +
          "(amount=$amountSatoshis + dust=$DUST_SAT)"
      )
    }

    return selected
  }

  /**
   * Calculates the transaction fee in satoshis.
   *
   * Formula from web's btc.ts estimateFeeValue():
   *   fee = max(DUST_SAT, feeRate * txSize / 1024)
   *
   * The DUST_SAT minimum ensures the fee is never so low that miners
   * would ignore the transaction.
   *
   * @param feeRateSatPerKb fee rate in satoshis per kilobyte
   * @param txSizeBytes transaction size in bytes
   * @return fee in satoshis (at least DUST_SAT)
   */
  fun calculateFee(feeRateSatPerKb: Long, txSizeBytes: Int): Long {
    // Ceiling division: (rate * size + 1023) / 1024
    val calculatedFee = (feeRateSatPerKb * txSizeBytes + 1023) / 1024
    return max(DUST_SAT, calculatedFee)
  }

  /**
   * Calculates the estimated transaction size in bytes for P2PKH transactions.
   *
   * Formula from web's btc.ts calculateTxSize():
   *   size = txIn * P2PKH_IN_SIZE + txOut * P2PKH_OUT_SIZE + (TX_SIZE + txIn - txOut)
   *
   * @param txIn number of inputs
   * @param txOut number of outputs
   * @return estimated transaction size in bytes
   */
  fun calculateTxSize(txIn: Int, txOut: Int): Int {
    return txIn * P2PKH_IN_SIZE + txOut * P2PKH_OUT_SIZE + (TX_SIZE + txIn - txOut)
  }

  /**
   * Calculates change and effective fee, absorbing dust change.
   *
   * From web's btc.ts prepareRawTx(): if skipValue (change) > 546,
   * add a change output; otherwise, the dust is added to the miner fee.
   *
   * @param totalInputSatoshis sum of all selected UTXO values
   * @param amountSatoshis amount being sent to recipient
   * @param feeSatoshis base transaction fee
   * @return ChangeResult with changeSatoshis=0 if dust, and adjusted fee
   */
  fun calculateChange(
    totalInputSatoshis: Long,
    amountSatoshis: Long,
    feeSatoshis: Long,
  ): ChangeResult {
    val rawChange = totalInputSatoshis - amountSatoshis - feeSatoshis

    return if (rawChange < DUST_SAT) {
      // Dust: absorb into fee, no change output
      ChangeResult(
        changeSatoshis = 0L,
        effectiveFeeSatoshis = feeSatoshis + rawChange,
      )
    } else {
      // Normal change output
      ChangeResult(
        changeSatoshis = rawChange,
        effectiveFeeSatoshis = feeSatoshis,
      )
    }
  }

  /**
   * Fetches current BTC fee rates from the Blockcypher API.
   *
   * Falls back to default rates if the API call fails.
   * Web equivalent: btc.ts getFeesRateBlockcypher()
   *
   * @return BtcFeeRates with fast/normal/slow rates in sat/KB
   */
  suspend fun estimateFees(): BtcFeeRates {
    return try {
      val response = blockcypherApi.getFeeRates()
      BtcFeeRates(
        fast = response.highFeePerKb,
        normal = response.mediumFeePerKb,
        slow = response.lowFeePerKb,
      )
    } catch (e: Exception) {
      // Fallback to defaults from DEFAULT_CURRENCY_PARAMETERS
      BtcFeeRates(
        fast = DEFAULT_FEE_FAST,
        normal = DEFAULT_FEE_NORMAL,
        slow = DEFAULT_FEE_SLOW,
      )
    }
  }

  /**
   * Builds, signs, and serializes a BTC transaction.
   *
   * Creates a P2PKH transaction with:
   * - Inputs from selected UTXOs
   * - Recipient output with the send amount
   * - Change output (if change >= DUST_SAT)
   * - All inputs signed with the provided private key
   *
   * Ports the pattern from web's btc.ts prepareRawTx().
   * Uses bitcoinj's Transaction class (not PSBT) per tech-spec risk mitigation.
   *
   * @param utxos selected unspent outputs to spend
   * @param recipientAddress BTC address of the recipient
   * @param amountSatoshis amount to send in satoshis
   * @param feeSatoshis transaction fee in satoshis
   * @param changeAddress address for change output (sender's address)
   * @param privateKeyWIF BTC private key in WIF format for signing
   * @return signed transaction as hex string
   */
  fun buildTransaction(
    utxos: List<UnspentOutput>,
    recipientAddress: String,
    amountSatoshis: Long,
    feeSatoshis: Long,
    changeAddress: String,
    privateKeyWIF: String,
  ): String {
    val ecKey = ECKey.fromPrivate(
      org.bitcoinj.core.DumpedPrivateKey.fromBase58(networkParams, privateKeyWIF).key.privKeyBytes
    )

    val tx = Transaction(networkParams)

    // Add recipient output
    val recipientAddr = LegacyAddress.fromBase58(networkParams, recipientAddress)
    tx.addOutput(Coin.valueOf(amountSatoshis), recipientAddr)

    // Calculate and add change output (if not dust)
    val totalInput = utxos.sumOf { it.valueSatoshis }
    val changeResult = calculateChange(totalInput, amountSatoshis, feeSatoshis)

    if (changeResult.changeSatoshis > 0) {
      val changeAddr = LegacyAddress.fromBase58(networkParams, changeAddress)
      tx.addOutput(Coin.valueOf(changeResult.changeSatoshis), changeAddr)
    }

    // Add inputs from UTXOs
    for (utxo in utxos) {
      val outPoint = TransactionOutPoint(
        networkParams,
        utxo.vout.toLong(),
        Sha256Hash.wrap(utxo.txid),
      )
      val scriptBytes = org.bitcoinj.core.Utils.HEX.decode(utxo.script)
      val script = org.bitcoinj.script.Script(scriptBytes)
      tx.addSignedInput(outPoint, script, ecKey, Transaction.SigHash.ALL, true)
    }

    return org.bitcoinj.core.Utils.HEX.encode(tx.bitcoinSerialize())
  }

  /**
   * Broadcasts a signed transaction to the Bitcoin network via Bitpay API.
   *
   * Web equivalent: btc.ts broadcastTx()
   *
   * @param signedTxHex serialized signed transaction in hex format
   * @return transaction ID (txid) on success
   * @throws BroadcastException if the API returns an error
   */
  suspend fun broadcastTransaction(signedTxHex: String): String {
    return try {
      val response = bitpayApi.broadcastTransaction(BitpayBroadcastRequest(rawTx = signedTxHex))
      response.txid
    } catch (e: Exception) {
      throw BroadcastException("Broadcast failed: ${e.message}", e)
    }
  }
}
