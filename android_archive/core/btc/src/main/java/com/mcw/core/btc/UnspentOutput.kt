package com.mcw.core.btc

/**
 * Unspent transaction output (UTXO) for BTC transaction construction.
 *
 * Maps from Bitpay API's BitpayUtxo response format.
 * Used in UTXO selection and transaction building.
 */
data class UnspentOutput(
  val txid: String,          // transaction hash
  val vout: Int,             // output index within the transaction
  val valueSatoshis: Long,   // value in satoshis
  val script: String,        // scriptPubKey hex
)
