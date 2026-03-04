package com.mcw.core.btc

/**
 * BTC fee rates in satoshis per kilobyte for three speed tiers.
 *
 * Values sourced from Blockcypher API (high/medium/low_fee_per_kb).
 * Fallback defaults from web wallet's DEFAULT_CURRENCY_PARAMETERS:
 * slow=5000, normal=15000, fast=30000 sat/KB.
 */
data class BtcFeeRates(
  val fast: Long,    // sat/KB — high priority, ~1-2 blocks
  val normal: Long,  // sat/KB — medium priority, ~3-6 blocks
  val slow: Long,    // sat/KB — low priority, ~7+ blocks
)
