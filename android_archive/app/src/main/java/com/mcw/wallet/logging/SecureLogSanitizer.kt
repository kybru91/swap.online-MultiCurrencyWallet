package com.mcw.wallet.logging

/**
 * Sanitizes log messages to remove sensitive data before sending to Crashlytics.
 *
 * Secret-safe logging policy (tech-spec):
 * "Transaction lifecycle logging must NEVER include private keys, mnemonic words,
 * or password hashes."
 *
 * Patterns detected and redacted:
 * - BTC WIF private keys (Base58Check, 51-52 chars starting with 5/K/L)
 * - ETH private keys (0x + 64 hex chars)
 * - Long hex strings (64+ hex chars, potential raw keys)
 * - BIP39 mnemonic phrases (5+ consecutive lowercase words)
 * - bcrypt password hashes ($2a$, $2b$, $2y$ prefixed)
 */
object SecureLogSanitizer {

  // BTC WIF private key: starts with 5, K, or L followed by Base58 chars, 51-52 total
  private val WIF_PATTERN = Regex("""[5KL][1-9A-HJ-NP-Za-km-z]{50,51}""")

  // ETH private key: 0x followed by 64 hex chars
  private val ETH_KEY_PATTERN = Regex("""0x[0-9a-fA-F]{64}""")

  // Raw hex private key: 64 hex chars (without 0x prefix) in key= or key: context
  private val RAW_HEX_KEY_PATTERN = Regex("""(?<=key[=:]\s?)[0-9a-fA-F]{64}""")

  // BIP39 mnemonic: 5+ consecutive lowercase words separated by spaces
  // (real mnemonics are 12 words, but detect fragments too)
  private val MNEMONIC_PATTERN = Regex("""(?:[a-z]{3,10}\s){4,}[a-z]{3,10}""")

  // bcrypt hash: $2a$, $2b$, or $2y$ followed by cost and hash
  private val BCRYPT_PATTERN = Regex("""\$2[aby]\$\d{1,2}\$[./A-Za-z0-9]{53}""")

  /**
   * Remove sensitive data patterns from a log message.
   * @param message the raw log message
   * @return sanitized message with sensitive data replaced by [REDACTED]
   */
  fun sanitize(message: String?): String? {
    if (message == null) return null

    var result = message
    result = WIF_PATTERN.replace(result, "[REDACTED:WIF_KEY]")
    result = ETH_KEY_PATTERN.replace(result, "[REDACTED:ETH_KEY]")
    result = RAW_HEX_KEY_PATTERN.replace(result, "[REDACTED:HEX_KEY]")
    result = MNEMONIC_PATTERN.replace(result, "[REDACTED:MNEMONIC]")
    result = BCRYPT_PATTERN.replace(result, "[REDACTED:BCRYPT]")
    return result
  }
}
