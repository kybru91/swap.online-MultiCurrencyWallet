package com.mcw.core.btc

/**
 * Thrown when UTXO selection cannot find enough inputs to cover
 * the requested amount plus dust margin (546 sat).
 */
class InsufficientFundsException(message: String) : Exception(message)
