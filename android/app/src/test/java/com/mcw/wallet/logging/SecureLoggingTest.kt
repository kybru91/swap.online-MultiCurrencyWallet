package com.mcw.wallet.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for secret-safe logging policy.
 *
 * Tech-spec: "Transaction lifecycle logging (Crashlytics + logcat) must NEVER include
 * private keys, mnemonic words, or password hashes. Release builds strip all DEBUG-level
 * logcat via Timber production tree. Crashlytics custom logs include only: tx hash, status,
 * chain, error message (no addresses, amounts, or signing data)."
 */
class SecureLoggingTest {

  // --- TDD Anchor: testCrashlyticsLogging ---

  @Test
  fun testCrashlyticsLogging() {
    // Log a tx event and verify no sensitive data is included
    val event = TxLogEvent(
      txHash = "0xabc123def456",
      status = TxStatus.BROADCAST,
      chain = "ETH",
      errorMessage = null,
    )
    // Verify only allowed fields
    assertEquals("0xabc123def456", event.txHash)
    assertEquals(TxStatus.BROADCAST, event.status)
    assertEquals("ETH", event.chain)
    assertNull(event.errorMessage)
    // Convert to log string
    val logString = event.toLogString()
    assertFalse("Log should not contain private key patterns",
      logString.contains("privateKey", ignoreCase = true))
    assertFalse("Log should not contain mnemonic patterns",
      logString.contains("mnemonic", ignoreCase = true))
    assertFalse("Log should not contain password patterns",
      logString.contains("password", ignoreCase = true))
    // Should contain the allowed fields
    assertTrue("Log should contain tx hash", logString.contains("0xabc123def456"))
    assertTrue("Log should contain status", logString.contains("BROADCAST"))
    assertTrue("Log should contain chain", logString.contains("ETH"))
  }

  @Test
  fun testCrashlyticsLoggingWithError() {
    val event = TxLogEvent(
      txHash = "0xfail789",
      status = TxStatus.FAILED,
      chain = "BSC",
      errorMessage = "nonce too low",
    )
    val logString = event.toLogString()
    assertTrue("Log should contain error message", logString.contains("nonce too low"))
    assertTrue("Log should contain FAILED status", logString.contains("FAILED"))
  }

  @Test
  fun testTxLogEventOnlyContainsSafeFields() {
    // Verify TxLogEvent data class has exactly the allowed fields
    // Tech-spec: "only tx hash, status, chain, error message"
    val event = TxLogEvent(
      txHash = "0x123",
      status = TxStatus.CREATED,
      chain = "MATIC",
      errorMessage = null,
    )
    val logString = event.toLogString()
    // No amounts
    assertFalse("Log should not contain amount", logString.contains("amount", ignoreCase = true))
    // No addresses
    assertFalse("Log should not contain 'address'", logString.contains("address", ignoreCase = true))
  }

  // --- SecureLogSanitizer tests ---

  @Test
  fun testSanitizePrivateKey() {
    val input = "Transaction signed with key 5HueCGU8rMjxEXxiPuD5BDku4MkFqeZyd4dZ1jvhTVqvbTLvyTJ"
    val sanitized = SecureLogSanitizer.sanitize(input)!!
    assertFalse("Sanitized output should not contain WIF private key",
      sanitized.contains("5HueCGU8rMjxEXxiPuD5BDku4MkFqeZyd4dZ1jvhTVqvbTLvyTJ"))
    assertTrue("Sanitized output should contain redaction marker",
      sanitized.contains("[REDACTED"))
  }

  @Test
  fun testSanitizeEthPrivateKey() {
    val input = "key=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    val sanitized = SecureLogSanitizer.sanitize(input)!!
    assertFalse("Sanitized output should not contain ETH private key hex",
      sanitized.contains("ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"))
    assertTrue("Sanitized output should contain redaction marker",
      sanitized.contains("[REDACTED"))
  }

  @Test
  fun testSanitizeMnemonic() {
    val input = "User mnemonic: abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    val sanitized = SecureLogSanitizer.sanitize(input)!!
    assertFalse("Sanitized output should not contain full mnemonic",
      sanitized.contains("abandon abandon abandon abandon abandon"))
    assertTrue("Sanitized output should contain redaction marker",
      sanitized.contains("[REDACTED"))
  }

  @Test
  fun testSanitizeBcryptHash() {
    val input = "Password hash: \$2a\$12\$LJ3m4ys8LdXHD7JzERZeOOAjlB0CwT5C5qXHqnqvU0jqPqB3Mz4HO"
    val sanitized = SecureLogSanitizer.sanitize(input)!!
    assertFalse("Sanitized output should not contain bcrypt hash",
      sanitized.contains("\$2a\$12\$"))
    assertTrue("Sanitized output should contain redaction marker",
      sanitized.contains("[REDACTED"))
  }

  @Test
  fun testSanitizeSafeMessage() {
    // Messages without sensitive data should pass through unchanged
    val input = "Transaction 0xabc123 broadcast on ETH"
    val sanitized = SecureLogSanitizer.sanitize(input)
    assertEquals("Safe message should not be modified", input, sanitized)
  }

  @Test
  fun testSanitizeNullMessage() {
    val sanitized = SecureLogSanitizer.sanitize(null)
    assertNull("Null message should remain null", sanitized)
  }

  // --- TxStatus enum tests ---

  @Test
  fun testTxStatusValues() {
    val statuses = TxStatus.entries
    assertTrue("Should have CREATED status", statuses.contains(TxStatus.CREATED))
    assertTrue("Should have SIGNED status", statuses.contains(TxStatus.SIGNED))
    assertTrue("Should have BROADCAST status", statuses.contains(TxStatus.BROADCAST))
    assertTrue("Should have CONFIRMED status", statuses.contains(TxStatus.CONFIRMED))
    assertTrue("Should have FAILED status", statuses.contains(TxStatus.FAILED))
  }
}
