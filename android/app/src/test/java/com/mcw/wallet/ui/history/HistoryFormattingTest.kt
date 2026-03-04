package com.mcw.wallet.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit tests for HistoryScreen formatting helper functions.
 *
 * These are pure functions (no Android/Compose dependency) so they
 * can run as JVM unit tests.
 */
class HistoryFormattingTest {

  // ===== truncateHash =====

  @Test
  fun testTruncateHash_longHash() {
    val hash = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
    val result = truncateHash(hash)
    assertEquals("Long hash should be truncated", "0xabcdef...567890", result)
  }

  @Test
  fun testTruncateHash_shortHash() {
    val hash = "0xabc123"
    val result = truncateHash(hash)
    assertEquals("Short hash should not be truncated", "0xabc123", result)
  }

  @Test
  fun testTruncateHash_exactlyAtLimit() {
    // 16 chars
    val hash = "0xabcdef12345678"
    val result = truncateHash(hash)
    assertEquals("Hash at limit should not be truncated", "0xabcdef12345678", result)
  }

  @Test
  fun testTruncateHash_17Chars() {
    // 17 chars -> should truncate
    val hash = "0xabcdef123456789"
    val result = truncateHash(hash)
    assertEquals("17-char hash should be truncated", "0xabcdef...456789", result)
  }

  // ===== formatTimestamp =====

  @Test
  fun testFormatTimestamp_zeroReturnsPending() {
    val result = formatTimestamp(0)
    assertEquals("Zero timestamp should return Pending", "Pending", result)
  }

  @Test
  fun testFormatTimestamp_negativeReturnsPending() {
    val result = formatTimestamp(-1)
    assertEquals("Negative timestamp should return Pending", "Pending", result)
  }

  @Test
  fun testFormatTimestamp_validTimestamp() {
    // 1700000000 = 2023-11-14T22:13:20Z
    val result = formatTimestamp(1700000000)
    // We can't assert the exact format since it depends on the default locale,
    // but we can verify it's not "Pending" and contains a year
    assertTrue("Valid timestamp should not be Pending", result != "Pending")
    assertTrue("Should contain 2023", result.contains("2023"))
  }

  // ===== formatAmount =====

  @Test
  fun testFormatAmount_zeroBalance() {
    val result = formatAmount(BigDecimal.ZERO)
    assertEquals("Zero should display as 0.00", "0.00", result)
  }

  @Test
  fun testFormatAmount_normalBalance() {
    val result = formatAmount(BigDecimal("1.50000000"))
    // stripTrailingZeros gives scale=1 ("1.5"), then we enforce min 2 decimals -> "1.50"
    assertEquals("1.5 BTC should display as 1.50 (min 2 decimals)", "1.50", result)
  }

  @Test
  fun testFormatAmount_smallAmount() {
    val result = formatAmount(BigDecimal("0.00050000"))
    assertEquals("0.0005 should display as 0.0005", "0.0005", result)
  }

  @Test
  fun testFormatAmount_wholeNumber() {
    val result = formatAmount(BigDecimal("10.00000000"))
    assertEquals("Whole number should show 2 decimals", "10.00", result)
  }

  // ===== formatConfirmations =====

  @Test
  fun testFormatConfirmations_zero() {
    assertEquals("Unconfirmed", formatConfirmations(0))
  }

  @Test
  fun testFormatConfirmations_one() {
    assertEquals("1 confirmation", formatConfirmations(1))
  }

  @Test
  fun testFormatConfirmations_multiple() {
    assertEquals("50 confirmations", formatConfirmations(50))
  }

  @Test
  fun testFormatConfirmations_overThousand() {
    assertEquals("1000+ confirmations", formatConfirmations(87238))
  }

  @Test
  fun testFormatConfirmations_negative() {
    assertEquals("Unconfirmed", formatConfirmations(-1))
  }

  // helper for assertTrue with message
  private fun assertTrue(message: String, condition: Boolean) {
    org.junit.Assert.assertTrue(message, condition)
  }
}
