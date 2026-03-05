package com.mcw.feature.dappbrowser

/**
 * Analyzes ERC20 approve() calldata for unlimited approval warnings.
 *
 * Per tech-spec: "ERC20 approve() with MAX_UINT256 shows 'Unlimited approval' warning."
 *
 * MAX_UINT256 = 2^256 - 1 = 0xfff...f (64 hex chars of f)
 */
object ApprovalAnalyzer {

  /** ERC20 approve(address,uint256) function selector */
  private const val APPROVE_SELECTOR = "095ea7b3"

  /** MAX_UINT256 as hex string (64 chars, all f) */
  private const val MAX_UINT256_HEX = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

  /**
   * Check if calldata represents an unlimited ERC20 approval.
   *
   * An unlimited approval is detected when:
   * 1. Function selector is approve(address,uint256) = 0x095ea7b3
   * 2. The uint256 amount parameter equals MAX_UINT256
   *
   * @param data the full calldata (0x-prefixed hex string)
   * @return true if this is an unlimited approval, false otherwise
   */
  fun isUnlimitedApproval(data: String): Boolean {
    val hex = data.removePrefix("0x").removePrefix("0X").lowercase()

    // Must be: 4 bytes selector + 32 bytes address + 32 bytes amount = 136 hex chars
    if (hex.length < 136) return false

    // Check selector is approve
    val selector = hex.substring(0, 8)
    if (selector != APPROVE_SELECTOR) return false

    // Amount is the last 32 bytes (64 hex chars)
    val amount = hex.substring(72, 136)
    return amount == MAX_UINT256_HEX
  }

  /**
   * Extract the spender address from an approve() calldata.
   *
   * @param data the full calldata (0x-prefixed hex string)
   * @return the spender address as 0x-prefixed string, or null if not an approve call
   */
  fun extractApproveSpender(data: String): String? {
    val hex = data.removePrefix("0x").removePrefix("0X").lowercase()
    if (hex.length < 136) return null

    val selector = hex.substring(0, 8)
    if (selector != APPROVE_SELECTOR) return null

    // Address is right-padded in the first 32 bytes after selector
    // Last 20 bytes (40 hex chars) of the 32-byte word
    val addressHex = hex.substring(32, 72)
    return "0x$addressHex"
  }
}
