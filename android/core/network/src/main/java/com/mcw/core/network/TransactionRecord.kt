package com.mcw.core.network

import java.math.BigDecimal

/**
 * Unified transaction record for display across all chains.
 *
 * Both BtcManager and EvmManager produce TransactionRecord objects
 * by parsing API responses from Blockcypher and Etherscan respectively.
 *
 * Tech-spec data model (from Data Models section):
 * - hash, direction, amount, fee, currency, timestamp, confirmations, counterpartyAddress
 *
 * @param hash transaction hash (BTC txid or EVM 0x-prefixed hash)
 * @param direction IN (received), OUT (sent), SELF (sent to own address)
 * @param amount transaction amount in native units (BTC or ETH/BNB/MATIC)
 * @param fee transaction fee in native units
 * @param currency currency symbol: "BTC", "ETH", "BNB", "MATIC"
 * @param timestamp unix epoch seconds
 * @param confirmations number of confirmations
 * @param counterpartyAddress the other party's address (recipient for OUT, sender for IN)
 * @param blockNumber block number (0 if unconfirmed)
 * @param isError true if the transaction failed (EVM only, always false for BTC)
 */
data class TransactionRecord(
  val hash: String,
  val direction: TxDirection,
  val amount: BigDecimal,
  val fee: BigDecimal,
  val currency: String,
  val timestamp: Long,
  val confirmations: Int,
  val counterpartyAddress: String,
  val blockNumber: Long = 0L,
  val isError: Boolean = false,
)

/**
 * Transaction direction relative to the wallet address.
 *
 * - IN: funds received (counterparty -> wallet)
 * - OUT: funds sent (wallet -> counterparty)
 * - SELF: wallet sent to itself (e.g., UTXO consolidation or test send)
 */
enum class TxDirection {
  IN,
  OUT,
  SELF,
}
