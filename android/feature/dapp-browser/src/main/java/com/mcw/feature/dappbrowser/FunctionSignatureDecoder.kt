package com.mcw.feature.dappbrowser

/**
 * Decodes common ERC20/DEX function selectors into human-readable names.
 *
 * Per tech-spec: "Transaction confirmation dialog: decode common function signatures
 * (transfer, approve, swap) for human-readable display."
 *
 * Function selector = first 4 bytes of keccak256 hash of function signature.
 */
object FunctionSignatureDecoder {

  /**
   * Known function selectors mapped to their decoded info.
   *
   * These are the most common ERC20 and DEX function selectors
   * encountered in dApp interactions.
   */
  private val KNOWN_SELECTORS: Map<String, DecodedFunction> = mapOf(
    // ERC20 standard
    "a9059cbb" to DecodedFunction("transfer", "ERC20 Transfer", "transfer(address,uint256)"),
    "095ea7b3" to DecodedFunction("approve", "ERC20 Approve", "approve(address,uint256)"),
    "23b872dd" to DecodedFunction("transferFrom", "ERC20 TransferFrom", "transferFrom(address,address,uint256)"),

    // Uniswap V2 Router
    "38ed1739" to DecodedFunction("swapExactTokensForTokens", "Swap Tokens", "swapExactTokensForTokens(uint256,uint256,address[],address,uint256)"),
    "8803dbee" to DecodedFunction("swapTokensForExactTokens", "Swap Tokens", "swapTokensForExactTokens(uint256,uint256,address[],address,uint256)"),
    "7ff36ab5" to DecodedFunction("swapExactETHForTokens", "Swap ETH for Tokens", "swapExactETHForTokens(uint256,address[],address,uint256)"),
    "18cbafe5" to DecodedFunction("swapExactTokensForETH", "Swap Tokens for ETH", "swapExactTokensForETH(uint256,uint256,address[],address,uint256)"),
    "fb3bdb41" to DecodedFunction("swapETHForExactTokens", "Swap ETH for Tokens", "swapETHForExactTokens(uint256,address[],address,uint256)"),

    // Uniswap V3 Router
    "414bf389" to DecodedFunction("exactInputSingle", "Swap (V3)", "exactInputSingle((address,address,uint24,address,uint256,uint256,uint256,uint160))"),
    "c04b8d59" to DecodedFunction("exactInput", "Swap (V3)", "exactInput((bytes,address,uint256,uint256,uint256))"),
    "db3e2198" to DecodedFunction("exactOutputSingle", "Swap (V3)", "exactOutputSingle((address,address,uint24,address,uint256,uint256,uint256,uint160))"),

    // Common DeFi
    "e8e33700" to DecodedFunction("addLiquidity", "Add Liquidity", "addLiquidity(address,address,uint256,uint256,uint256,uint256,address,uint256)"),
    "f305d719" to DecodedFunction("addLiquidityETH", "Add Liquidity (ETH)", "addLiquidityETH(address,uint256,uint256,uint256,address,uint256)"),
    "baa2abde" to DecodedFunction("removeLiquidity", "Remove Liquidity", "removeLiquidity(address,address,uint256,uint256,uint256,address,uint256)"),

    // Permit / Permit2 (for signing warnings)
    "d505accf" to DecodedFunction("permit", "Permit (Gasless Approval)", "permit(address,address,uint256,uint256,uint8,bytes32,bytes32)"),
  )

  /**
   * Decode a function selector from transaction data.
   *
   * @param data the full calldata (0x-prefixed hex string) or just the 4-byte selector
   * @return [DecodedFunction] if the selector is known, null for unknown selectors
   */
  fun decode(data: String): DecodedFunction? {
    if (data.isEmpty() || data == "0x") return null

    val hex = data.removePrefix("0x").removePrefix("0X")
    if (hex.length < 8) return null // Need at least 4 bytes (8 hex chars)

    val selector = hex.substring(0, 8).lowercase()
    return KNOWN_SELECTORS[selector]
  }
}

/**
 * A decoded function signature.
 *
 * @param name the function name (e.g., "transfer", "approve", "swapExactTokensForTokens")
 * @param displayName human-readable display name (e.g., "ERC20 Transfer", "Swap Tokens")
 * @param signature the full function signature (e.g., "transfer(address,uint256)")
 */
data class DecodedFunction(
  val name: String,
  val displayName: String,
  val signature: String,
)
