package com.mcw.core.network.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Blockcypher API — BTC fee estimation and transaction history.
 *
 * Base URL: https://api.blockcypher.com/v1/btc/main
 * Source: src/front/config/mainnet/api.js -> blockcypher
 *
 * Endpoints:
 * - GET / — fee rates (high/medium/low fee_per_kb)
 * - GET /addrs/{address} — address info with transaction references
 */
interface BlockcypherApi {

  /**
   * Fetch current BTC fee rates.
   * Web equivalent: GET {blockcypher}/
   * Returns high/medium/low fee_per_kb values.
   */
  @GET(".")
  suspend fun getFeeRates(): BlockcypherFeeResponse

  /**
   * Fetch address info with transaction references.
   *
   * Blockcypher API: GET /v1/btc/main/addrs/{address}
   * Returns address data including txrefs (confirmed tx references)
   * and unconfirmed_txrefs (pending tx references).
   *
   * @param address BTC address (P2PKH format)
   * @param limit max number of txrefs to return (default 50)
   * @return address data with transaction references
   */
  @GET("addrs/{address}")
  suspend fun getAddressTransactions(
    @Path("address") address: String,
    @Query("limit") limit: Int = 50,
  ): BlockcypherAddressResponse
}

@JsonClass(generateAdapter = true)
data class BlockcypherFeeResponse(
  @Json(name = "high_fee_per_kb") val highFeePerKb: Long,
  @Json(name = "medium_fee_per_kb") val mediumFeePerKb: Long,
  @Json(name = "low_fee_per_kb") val lowFeePerKb: Long,
)

/**
 * Blockcypher address response with transaction references.
 *
 * API response format:
 * ```json
 * {
 *   "address": "1DEP8i3QJCsjoHoFY2n4l3L2X8GKEyJSbb",
 *   "total_received": 4433416,
 *   "total_sent": 0,
 *   "balance": 4433416,
 *   "unconfirmed_balance": 0,
 *   "final_balance": 4433416,
 *   "n_tx": 7,
 *   "unconfirmed_n_tx": 0,
 *   "final_n_tx": 7,
 *   "txrefs": [...]
 * }
 * ```
 */
@JsonClass(generateAdapter = true)
data class BlockcypherAddressResponse(
  @Json(name = "address") val address: String,
  @Json(name = "balance") val balance: Long,
  @Json(name = "n_tx") val nTx: Int,
  @Json(name = "txrefs") val txrefs: List<BlockcypherTxRef>? = null,
  @Json(name = "unconfirmed_txrefs") val unconfirmedTxrefs: List<BlockcypherTxRef>? = null,
)

/**
 * A single transaction reference from Blockcypher.
 *
 * Blockcypher txref format:
 * ```json
 * {
 *   "tx_hash": "14b1052855bbf6561bc4db...",
 *   "block_height": 302013,
 *   "tx_input_n": -1,
 *   "tx_output_n": 0,
 *   "value": 20213,
 *   "ref_balance": 4433416,
 *   "spent": false,
 *   "confirmations": 87238,
 *   "confirmed": "2014-05-22T03:46:25Z",
 *   "double_spend": false
 * }
 *
 * Direction logic:
 * - tx_input_n == -1: this address received funds (an output belongs to this address)
 * - tx_input_n >= 0: this address spent funds (an input belongs to this address)
 */
@JsonClass(generateAdapter = true)
data class BlockcypherTxRef(
  @Json(name = "tx_hash") val txHash: String,
  @Json(name = "block_height") val blockHeight: Long = 0L,
  @Json(name = "tx_input_n") val txInputN: Int,
  @Json(name = "tx_output_n") val txOutputN: Int,
  @Json(name = "value") val value: Long,
  @Json(name = "confirmations") val confirmations: Int = 0,
  @Json(name = "confirmed") val confirmed: String? = null,
  @Json(name = "spent") val spent: Boolean = false,
  @Json(name = "double_spend") val doubleSpend: Boolean = false,
)
