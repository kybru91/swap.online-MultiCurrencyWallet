package com.mcw.core.btc

/**
 * Result of change calculation for a BTC transaction.
 *
 * If change < DUST_SAT (546), it is absorbed into the fee instead
 * of creating a dust change output. This prevents creating UTXOs
 * that are uneconomical to spend.
 */
data class ChangeResult(
  val changeSatoshis: Long,         // 0 if dust was absorbed
  val effectiveFeeSatoshis: Long,   // original fee + absorbed dust (if any)
)
