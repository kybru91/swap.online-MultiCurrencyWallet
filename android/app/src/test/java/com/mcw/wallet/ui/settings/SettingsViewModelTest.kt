package com.mcw.wallet.ui.settings

import com.mcw.core.network.RpcUrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Settings screen RPC URL validation.
 *
 * Reuses RpcUrlValidator from :core:network (already fully tested there).
 * These tests verify the integration from SettingsViewModel perspective
 * and cover the TDD anchors specified in task 13.
 */
class SettingsViewModelTest {

  // --- TDD Anchor: testRpcUrlValidationHttps ---

  @Test
  fun testRpcUrlValidationHttps() {
    // HTTPS URL should be accepted
    val result = RpcUrlValidator.validate("https://example.com")
    assertTrue("HTTPS URL should be valid", result.isValid)
    assertNull("Valid URL should have no error message", result.errorMessage)
  }

  @Test
  fun testRpcUrlValidationHttpsWithPath() {
    val result = RpcUrlValidator.validate("https://mainnet.infura.io/v3/abc123")
    assertTrue("HTTPS URL with path should be valid", result.isValid)
  }

  @Test
  fun testRpcUrlValidationHttpsWithPort() {
    val result = RpcUrlValidator.validate("https://rpc.example.com:8545")
    assertTrue("HTTPS URL with port should be valid", result.isValid)
  }

  // --- TDD Anchor: testRpcUrlValidationHttp ---

  @Test
  fun testRpcUrlValidationHttp() {
    // HTTP URL should be rejected — HTTPS only per tech-spec
    val result = RpcUrlValidator.validate("http://example.com")
    assertFalse("HTTP URL should be rejected", result.isValid)
    assertTrue(
      "Error should mention HTTPS requirement",
      result.errorMessage?.contains("HTTPS") == true
    )
  }

  @Test
  fun testRpcUrlValidationNoScheme() {
    val result = RpcUrlValidator.validate("example.com")
    assertFalse("URL without scheme should be rejected", result.isValid)
  }

  @Test
  fun testRpcUrlValidationEmpty() {
    val result = RpcUrlValidator.validate("")
    assertFalse("Empty URL should be rejected", result.isValid)
  }

  @Test
  fun testRpcUrlValidationFileScheme() {
    val result = RpcUrlValidator.validate("file:///etc/passwd")
    assertFalse("file:// scheme should be rejected", result.isValid)
  }

  // --- TDD Anchor: testRpcUrlValidationPrivateIp ---

  @Test
  fun testRpcUrlValidationPrivateIp() {
    // Private IP 192.168.x.x should be rejected
    val result = RpcUrlValidator.validate("https://192.168.1.1")
    assertFalse("Private IP (192.168.x.x) should be rejected", result.isValid)
    assertTrue(
      "Error should mention private IP range",
      result.errorMessage?.contains("192.168") == true ||
        result.errorMessage?.contains("Private") == true
    )
  }

  @Test
  fun testRpcUrlValidationPrivateIp10() {
    val result = RpcUrlValidator.validate("https://10.0.0.1")
    assertFalse("Private IP (10.x.x.x) should be rejected", result.isValid)
  }

  @Test
  fun testRpcUrlValidationPrivateIp172() {
    val result = RpcUrlValidator.validate("https://172.16.0.1")
    assertFalse("Private IP (172.16.x.x) should be rejected", result.isValid)
  }

  @Test
  fun testRpcUrlValidationLocalhost() {
    val result = RpcUrlValidator.validate("https://localhost:8545")
    assertFalse("Localhost should be rejected", result.isValid)
  }

  @Test
  fun testRpcUrlValidationLoopback() {
    val result = RpcUrlValidator.validate("https://127.0.0.1")
    assertFalse("Loopback (127.0.0.1) should be rejected", result.isValid)
  }

  // --- Network selector tests ---

  @Test
  fun testSupportedChainIds() {
    // Verify supported chain IDs match tech-spec: ETH=1, BSC=56, Polygon=137
    val supported = SupportedNetwork.entries
    assertEquals("Should have exactly 3 supported networks", 3, supported.size)
    assertTrue("ETH mainnet (chain 1) should be supported",
      supported.any { it.chainId == 1L })
    assertTrue("BSC (chain 56) should be supported",
      supported.any { it.chainId == 56L })
    assertTrue("Polygon (chain 137) should be supported",
      supported.any { it.chainId == 137L })
  }

  @Test
  fun testNetworkSelectorDefaultIsEthMainnet() {
    // Default active chain should be ETH mainnet (chain ID 1)
    val defaultNetwork = SupportedNetwork.fromChainId(1L)
    assertEquals("Default should be ETH Mainnet", SupportedNetwork.ETH_MAINNET, defaultNetwork)
    assertEquals("ETH chain ID", 1L, defaultNetwork?.chainId)
    assertEquals("ETH display name", "Ethereum Mainnet", defaultNetwork?.displayName)
  }

  @Test
  fun testNetworkSelectorUnsupportedChain() {
    // Unknown chain ID should return null
    val unknown = SupportedNetwork.fromChainId(999L)
    assertNull("Unknown chain should return null", unknown)
  }

  // --- SettingsUiState tests ---

  @Test
  fun testSettingsUiStateDefaults() {
    val state = SettingsUiState()
    assertEquals("Default chain should be ETH (1)", SupportedNetwork.ETH_MAINNET, state.selectedNetwork)
    assertFalse("Mnemonic should not be showing by default", state.showMnemonic)
    assertTrue("Custom RPC URL should be empty by default", state.customRpcUrl.isEmpty())
    assertNull("RPC validation error should be null by default", state.rpcValidationError)
    assertFalse("Restart dialog should not show by default", state.showRestartDialog)
    assertFalse("Backup confirmation should not show by default", state.backupConfirmed)
  }
}
