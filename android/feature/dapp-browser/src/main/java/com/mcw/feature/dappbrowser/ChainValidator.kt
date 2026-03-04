package com.mcw.feature.dappbrowser

/**
 * Validates chain IDs for wallet_addEthereumChain and wallet_switchEthereumChain.
 *
 * Per tech-spec Decision 9: "wallet_addEthereumChain restricted to allowlisted
 * chain IDs (1, 56, 137) — reject unknown chains. Never allow dApp to override
 * RPC URL for already-configured chains."
 */
object ChainValidator {

  /**
   * Allowlisted chain IDs. Only these chains can be added or switched to.
   *
   * - 1: Ethereum Mainnet
   * - 56: BNB Smart Chain
   * - 137: Polygon
   */
  val ALLOWED_CHAIN_IDS: Set<Long> = setOf(1L, 56L, 137L)

  /**
   * Map of chain ID to human-readable name for display.
   */
  val CHAIN_NAMES: Map<Long, String> = mapOf(
    1L to "Ethereum Mainnet",
    56L to "BNB Smart Chain",
    137L to "Polygon",
  )

  /**
   * Map of chain ID to hex representation (for EIP-1193 responses).
   */
  val CHAIN_ID_HEX: Map<Long, String> = mapOf(
    1L to "0x1",
    56L to "0x38",
    137L to "0x89",
  )

  /**
   * Check if a chain ID is in the allowlist.
   *
   * @param chainId the chain ID to check (decimal)
   * @return true if the chain is allowed, false otherwise
   */
  fun isAllowedChainId(chainId: Long): Boolean {
    return chainId in ALLOWED_CHAIN_IDS
  }

  /**
   * Parse a chain ID from a hex string (e.g., "0x1") or decimal string (e.g., "1").
   *
   * @param chainIdStr the chain ID string (hex with "0x" prefix or decimal)
   * @return the chain ID as Long, or null if parsing fails
   */
  fun parseChainId(chainIdStr: String): Long? {
    val trimmed = chainIdStr.trim()
    return try {
      if (trimmed.startsWith("0x", ignoreCase = true)) {
        java.lang.Long.parseLong(trimmed.removePrefix("0x").removePrefix("0X"), 16)
      } else {
        trimmed.toLongOrNull()
      }
    } catch (e: NumberFormatException) {
      null
    }
  }
}
