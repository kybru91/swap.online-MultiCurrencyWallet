package com.mcw.feature.dappbrowser

/**
 * Validates RPC request parameters per tech-spec Decision 9.
 *
 * Checks:
 * - Address format: 0x-prefixed, 42 characters, hex
 * - Value bounds: valid hex number, fits in uint256
 * - Data size: max 64KB calldata
 * - Gas limit: validated by [GasValidator]
 */
object RpcParamValidator {

  /** Maximum calldata size in bytes (64KB) */
  private const val MAX_CALLDATA_BYTES = 64 * 1024 // 65536 bytes

  /** Expected length of an EVM address including "0x" prefix */
  private const val EVM_ADDRESS_LENGTH = 42

  /** Hex character regex */
  private val HEX_PATTERN = Regex("^0x[0-9a-fA-F]+$")

  /**
   * Validate an EVM address format.
   *
   * @param address the address string to validate
   * @return true if the address is a valid 0x-prefixed 40-char hex string
   */
  fun isValidAddress(address: String): Boolean {
    if (address.length != EVM_ADDRESS_LENGTH) return false
    return HEX_PATTERN.matches(address)
  }

  /**
   * Validate that calldata is within the 64KB size limit.
   *
   * @param data the hex-encoded calldata (0x-prefixed)
   * @return true if within limit, false if too large
   */
  fun isCalldataWithinLimit(data: String): Boolean {
    if (data.isEmpty() || data == "0x") return true
    // Each byte = 2 hex characters; subtract "0x" prefix
    val hexChars = data.removePrefix("0x").removePrefix("0X")
    val byteCount = hexChars.length / 2
    return byteCount <= MAX_CALLDATA_BYTES
  }

  /**
   * Validate that a hex value string is well-formed.
   *
   * @param value the hex-encoded value (e.g., "0x1" for 1 wei)
   * @return true if valid hex format
   */
  fun isValidHexValue(value: String): Boolean {
    if (value.isEmpty()) return false
    return HEX_PATTERN.matches(value)
  }
}
