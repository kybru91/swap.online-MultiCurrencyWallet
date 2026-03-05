package com.mcw.core.btc

import java.math.BigDecimal

/**
 * BTC balance for an address, with confirmed and unconfirmed amounts in BTC.
 *
 * Converted from satoshis (Bitpay API response) by dividing by 1e8.
 * Uses BigDecimal to avoid floating-point precision loss.
 */
data class BtcBalance(
  val balance: BigDecimal,       // confirmed balance in BTC
  val unconfirmed: BigDecimal,   // unconfirmed balance in BTC
)
