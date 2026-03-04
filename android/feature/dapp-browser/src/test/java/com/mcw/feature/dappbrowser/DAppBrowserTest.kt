package com.mcw.feature.dappbrowser

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for dApp Browser — EIP-1193 provider, origin validation, rate limiting,
 * gas thresholds, domain policy, RPC parsing, function signature decoding, and WebView security.
 *
 * TDD anchors from task 10:
 * - testWindowEthereumInjection — verify JS injection script contains window.ethereum + Object.freeze
 * - testOriginValidation — call from different origin -> rejected
 * - testRateLimiting — 15 rapid calls -> 5 queued
 * - testGasWarning — gasLimit=1,000,001 -> warning shown
 * - testGasReject — gasLimit=15,000,001 -> rejected with error
 * - testEthSignRejection — eth_sign call -> error 'Unsupported method'
 * - testWalletAddEthereumChainAllowlist — chainId 1/56/137 -> allowed, 999 -> rejected
 */
class DAppBrowserTest {

  // ===== Window Ethereum Injection =====

  @Test
  fun testWindowEthereumInjection_containsProviderObject() {
    val js = EthereumProviderJs.generateInjectionScript()
    assertTrue(
      "Injection script should define window.ethereum",
      js.contains("window.ethereum")
    )
  }

  @Test
  fun testWindowEthereumInjection_hasObjectFreeze() {
    val js = EthereumProviderJs.generateInjectionScript()
    assertTrue(
      "Injection script should Object.freeze(window.ethereum)",
      js.contains("Object.freeze(window.ethereum)")
    )
  }

  @Test
  fun testWindowEthereumInjection_hasIsMetaMask() {
    val js = EthereumProviderJs.generateInjectionScript()
    assertTrue(
      "Injection script should set isMetaMask: true for dApp compatibility",
      js.contains("isMetaMask")
    )
  }

  @Test
  fun testWindowEthereumInjection_hasRequestMethod() {
    val js = EthereumProviderJs.generateInjectionScript()
    assertTrue(
      "Injection script should define request() method",
      js.contains("request")
    )
  }

  @Test
  fun testWindowEthereumInjection_hasEip6963() {
    val js = EthereumProviderJs.generateInjectionScript()
    assertTrue(
      "Injection script should dispatch EIP-6963 announceProvider event",
      js.contains("eip6963:announceProvider")
    )
  }

  // ===== Origin Validation =====

  @Test
  fun testOriginValidation_matchingOriginAllowed() {
    val validator = OriginValidator()
    val approved = "https://dex.onout.org"
    val current = "https://dex.onout.org/swap"
    assertTrue(
      "Same origin should be allowed",
      validator.validateOrigin(approved, current)
    )
  }

  @Test
  fun testOriginValidation_differentOriginRejected() {
    val validator = OriginValidator()
    val approved = "https://dex.onout.org"
    val current = "https://evil.com/phish"
    assertFalse(
      "Different origin should be rejected",
      validator.validateOrigin(approved, current)
    )
  }

  @Test
  fun testOriginValidation_nullCurrentUrlRejected() {
    val validator = OriginValidator()
    val approved = "https://dex.onout.org"
    assertFalse(
      "Null current URL should be rejected",
      validator.validateOrigin(approved, null)
    )
  }

  @Test
  fun testOriginValidation_httpSchemeRejected() {
    val validator = OriginValidator()
    val approved = "https://dex.onout.org"
    val current = "http://dex.onout.org"
    assertFalse(
      "HTTP scheme mismatch should be rejected",
      validator.validateOrigin(approved, current)
    )
  }

  @Test
  fun testOriginValidation_subdomainMismatchRejected() {
    val validator = OriginValidator()
    val approved = "https://dex.onout.org"
    val current = "https://evil.dex.onout.org"
    assertFalse(
      "Subdomain mismatch should be rejected",
      validator.validateOrigin(approved, current)
    )
  }

  // ===== Rate Limiting =====

  @Test
  fun testRateLimiting_withinLimitAllowed() {
    val rateLimiter = RpcRateLimiter(maxCallsPerSecond = 10)
    // 10 calls should all be allowed
    var allowed = 0
    for (i in 1..10) {
      if (rateLimiter.tryAcquire()) allowed++
    }
    assertEquals("10 calls within limit should all be allowed", 10, allowed)
  }

  @Test
  fun testRateLimiting_exceedingLimitQueued() {
    val rateLimiter = RpcRateLimiter(maxCallsPerSecond = 10)
    // Make 15 rapid calls — first 10 allowed, last 5 should be rejected (queued)
    var allowed = 0
    var rejected = 0
    for (i in 1..15) {
      if (rateLimiter.tryAcquire()) allowed++ else rejected++
    }
    assertEquals("First 10 calls should be allowed", 10, allowed)
    assertEquals("Last 5 calls should be rejected/queued", 5, rejected)
  }

  @Test
  fun testRateLimiting_resetsAfterWindow() {
    val rateLimiter = RpcRateLimiter(maxCallsPerSecond = 10)
    // Fill the window
    for (i in 1..10) {
      rateLimiter.tryAcquire()
    }
    // Simulate time passing by resetting the window
    rateLimiter.resetForTesting()
    // Should be allowed again
    assertTrue("After window reset, calls should be allowed", rateLimiter.tryAcquire())
  }

  // ===== Gas Thresholds =====

  @Test
  fun testGasWarning_aboveThreshold() {
    val result = GasValidator.validate(gasLimit = 1_000_001L)
    assertEquals(
      "Gas limit above 1M should trigger warning",
      GasValidationResult.WARNING,
      result
    )
  }

  @Test
  fun testGasWarning_atThreshold() {
    val result = GasValidator.validate(gasLimit = 1_000_000L)
    assertEquals(
      "Gas limit exactly 1M should be OK",
      GasValidationResult.OK,
      result
    )
  }

  @Test
  fun testGasWarning_belowThreshold() {
    val result = GasValidator.validate(gasLimit = 999_999L)
    assertEquals(
      "Gas limit below 1M should be OK",
      GasValidationResult.OK,
      result
    )
  }

  @Test
  fun testGasReject_aboveMaximum() {
    val result = GasValidator.validate(gasLimit = 15_000_001L)
    assertEquals(
      "Gas limit above 15M should be REJECTED",
      GasValidationResult.REJECT,
      result
    )
  }

  @Test
  fun testGasReject_atMaximum() {
    val result = GasValidator.validate(gasLimit = 15_000_000L)
    assertEquals(
      "Gas limit exactly 15M should be WARNING (not reject)",
      GasValidationResult.WARNING,
      result
    )
  }

  @Test
  fun testGasReject_standardTransfer() {
    val result = GasValidator.validate(gasLimit = 21_000L)
    assertEquals(
      "Standard gas limit 21000 should be OK",
      GasValidationResult.OK,
      result
    )
  }

  // ===== eth_sign Rejection =====

  @Test
  fun testEthSignRejection_returnsError() {
    val handler = RpcMethodHandler()
    val result = handler.validateMethod("eth_sign")
    assertFalse(
      "eth_sign should be rejected as unsupported",
      result.isSupported
    )
    assertEquals(
      "Error message should indicate unsupported method",
      "Unsupported method: eth_sign is deprecated and unsafe",
      result.errorMessage
    )
  }

  @Test
  fun testEthSignRejection_ethRequestAccountsAllowed() {
    val handler = RpcMethodHandler()
    val result = handler.validateMethod("eth_requestAccounts")
    assertTrue("eth_requestAccounts should be supported", result.isSupported)
  }

  @Test
  fun testEthSignRejection_personalSignAllowed() {
    val handler = RpcMethodHandler()
    val result = handler.validateMethod("personal_sign")
    assertTrue("personal_sign should be supported", result.isSupported)
  }

  @Test
  fun testEthSignRejection_ethSignTypedDataV4Allowed() {
    val handler = RpcMethodHandler()
    val result = handler.validateMethod("eth_signTypedData_v4")
    assertTrue("eth_signTypedData_v4 should be supported", result.isSupported)
  }

  @Test
  fun testEthSignRejection_unknownMethodRejected() {
    val handler = RpcMethodHandler()
    val result = handler.validateMethod("eth_unknownMethod")
    assertFalse("Unknown method should be rejected", result.isSupported)
  }

  // ===== wallet_addEthereumChain Allowlist =====

  @Test
  fun testWalletAddEthereumChain_mainnetAllowed() {
    val result = ChainValidator.isAllowedChainId(1L)
    assertTrue("Chain ID 1 (Ethereum Mainnet) should be allowed", result)
  }

  @Test
  fun testWalletAddEthereumChain_bscAllowed() {
    val result = ChainValidator.isAllowedChainId(56L)
    assertTrue("Chain ID 56 (BSC) should be allowed", result)
  }

  @Test
  fun testWalletAddEthereumChain_polygonAllowed() {
    val result = ChainValidator.isAllowedChainId(137L)
    assertTrue("Chain ID 137 (Polygon) should be allowed", result)
  }

  @Test
  fun testWalletAddEthereumChain_unknownRejected() {
    val result = ChainValidator.isAllowedChainId(999L)
    assertFalse("Chain ID 999 should be rejected", result)
  }

  @Test
  fun testWalletAddEthereumChain_zeroRejected() {
    val result = ChainValidator.isAllowedChainId(0L)
    assertFalse("Chain ID 0 should be rejected", result)
  }

  @Test
  fun testWalletAddEthereumChain_negativeRejected() {
    val result = ChainValidator.isAllowedChainId(-1L)
    assertFalse("Chain ID -1 should be rejected", result)
  }

  // ===== Domain Policy =====

  @Test
  fun testDomainPolicy_knownDomainAllowed() {
    val policy = DomainPolicy()
    assertTrue(
      "Known domain dex.onout.org should be allowed",
      policy.isDomainAllowed("dex.onout.org")
    )
  }

  @Test
  fun testDomainPolicy_unknownDomainBlocked() {
    val policy = DomainPolicy()
    assertFalse(
      "Unknown domain evil.com should be blocked",
      policy.isDomainAllowed("evil.com")
    )
  }

  @Test
  fun testDomainPolicy_allKnownDomainsAllowed() {
    val policy = DomainPolicy()
    val knownDomains = listOf("dex.onout.org", "app.aave.com")
    for (domain in knownDomains) {
      assertTrue(
        "Known domain $domain should be allowed",
        policy.isDomainAllowed(domain)
      )
    }
  }

  @Test
  fun testDomainPolicy_customDomainCanBeAdded() {
    val policy = DomainPolicy()
    val custom = "my-custom-dapp.com"
    assertFalse("Initially custom domain should be blocked", policy.isDomainAllowed(custom))
    policy.addAllowedDomain(custom)
    assertTrue("After adding, custom domain should be allowed", policy.isDomainAllowed(custom))
  }

  // ===== URL Validation (WebView security) =====

  @Test
  fun testUrlValidation_httpsAllowed() {
    assertTrue(
      "HTTPS URL should be allowed",
      WebViewUrlValidator.isAllowedUrl("https://dex.onout.org")
    )
  }

  @Test
  fun testUrlValidation_httpBlocked() {
    assertFalse(
      "HTTP URL should be blocked",
      WebViewUrlValidator.isAllowedUrl("http://example.com")
    )
  }

  @Test
  fun testUrlValidation_fileBlocked() {
    assertFalse(
      "file:// URL should be blocked",
      WebViewUrlValidator.isAllowedUrl("file:///exploit.html")
    )
  }

  @Test
  fun testUrlValidation_javascriptBlocked() {
    assertFalse(
      "javascript: URL should be blocked",
      WebViewUrlValidator.isAllowedUrl("javascript:alert(1)")
    )
  }

  @Test
  fun testUrlValidation_dataBlocked() {
    assertFalse(
      "data: URL should be blocked",
      WebViewUrlValidator.isAllowedUrl("data:text/html,<h1>evil</h1>")
    )
  }

  @Test
  fun testUrlValidation_localhostBlocked() {
    assertFalse(
      "localhost URL should be blocked",
      WebViewUrlValidator.isAllowedUrl("https://localhost/exploit")
    )
  }

  @Test
  fun testUrlValidation_privateIp127Blocked() {
    assertFalse(
      "127.0.0.1 URL should be blocked",
      WebViewUrlValidator.isAllowedUrl("https://127.0.0.1")
    )
  }

  @Test
  fun testUrlValidation_privateIp192Blocked() {
    assertFalse(
      "192.168.1.1 URL should be blocked",
      WebViewUrlValidator.isAllowedUrl("https://192.168.1.1")
    )
  }

  @Test
  fun testUrlValidation_privateIp10Blocked() {
    assertFalse(
      "10.0.0.1 URL should be blocked",
      WebViewUrlValidator.isAllowedUrl("https://10.0.0.1")
    )
  }

  @Test
  fun testUrlValidation_privateIp172Blocked() {
    assertFalse(
      "172.16.0.1 URL should be blocked",
      WebViewUrlValidator.isAllowedUrl("https://172.16.0.1")
    )
  }

  // ===== RPC Request Parsing =====

  @Test
  fun testRpcParsing_validRequest() {
    val json = """{"method":"eth_requestAccounts","params":[]}"""
    val request = RpcRequestParser.parse(json)
    assertNotNull("Valid request should parse", request)
    assertEquals("eth_requestAccounts", request!!.method)
    assertEquals(0, request.params.size)
  }

  @Test
  fun testRpcParsing_requestWithParams() {
    val json = """{"method":"eth_sendTransaction","params":[{"to":"0xabcd","value":"0x1"}]}"""
    val request = RpcRequestParser.parse(json)
    assertNotNull("Request with params should parse", request)
    assertEquals("eth_sendTransaction", request!!.method)
    assertEquals(1, request.params.size)
  }

  @Test
  fun testRpcParsing_invalidJson() {
    val json = """not a valid json"""
    val request = RpcRequestParser.parse(json)
    assertNull("Invalid JSON should return null", request)
  }

  @Test
  fun testRpcParsing_missingMethod() {
    val json = """{"params":[]}"""
    val request = RpcRequestParser.parse(json)
    assertNull("Missing method should return null", request)
  }

  // ===== RPC Parameter Validation =====

  @Test
  fun testRpcValidation_addressFormatValid() {
    assertTrue(
      "Valid EVM address should pass",
      RpcParamValidator.isValidAddress("0x9858EfFD232B4033E47d90003D41EC34EcaEda94")
    )
  }

  @Test
  fun testRpcValidation_addressFormatInvalid_noPrefix() {
    assertFalse(
      "Address without 0x prefix should fail",
      RpcParamValidator.isValidAddress("9858EfFD232B4033E47d90003D41EC34EcaEda94")
    )
  }

  @Test
  fun testRpcValidation_addressFormatInvalid_tooShort() {
    assertFalse(
      "Short address should fail",
      RpcParamValidator.isValidAddress("0x1234")
    )
  }

  @Test
  fun testRpcValidation_calldataSizeWithinLimit() {
    // 64KB limit = 65536 bytes = 131072 hex chars (+ "0x" prefix)
    val data = "0x" + "aa".repeat(65536)
    assertTrue(
      "Calldata within 64KB limit should pass",
      RpcParamValidator.isCalldataWithinLimit(data)
    )
  }

  @Test
  fun testRpcValidation_calldataSizeExceedsLimit() {
    val data = "0x" + "aa".repeat(65537)
    assertFalse(
      "Calldata exceeding 64KB limit should fail",
      RpcParamValidator.isCalldataWithinLimit(data)
    )
  }

  // ===== Function Signature Decoding =====

  @Test
  fun testFunctionSigDecode_transfer() {
    // ERC20 transfer(address,uint256) selector = 0xa9059cbb
    val decoded = FunctionSignatureDecoder.decode("0xa9059cbb")
    assertNotNull("transfer selector should be decoded", decoded)
    assertEquals("transfer", decoded!!.name)
  }

  @Test
  fun testFunctionSigDecode_approve() {
    // ERC20 approve(address,uint256) selector = 0x095ea7b3
    val decoded = FunctionSignatureDecoder.decode("0x095ea7b3")
    assertNotNull("approve selector should be decoded", decoded)
    assertEquals("approve", decoded!!.name)
  }

  @Test
  fun testFunctionSigDecode_swap() {
    // Uniswap V2 swapExactTokensForTokens selector = 0x38ed1739
    val decoded = FunctionSignatureDecoder.decode("0x38ed1739")
    assertNotNull("swap selector should be decoded", decoded)
    assertTrue(
      "Swap function name should contain 'swap'",
      decoded!!.name.contains("swap", ignoreCase = true)
    )
  }

  @Test
  fun testFunctionSigDecode_unknown() {
    val decoded = FunctionSignatureDecoder.decode("0xdeadbeef")
    assertNull("Unknown selector should return null", decoded)
  }

  @Test
  fun testFunctionSigDecode_emptyData() {
    val decoded = FunctionSignatureDecoder.decode("")
    assertNull("Empty data should return null", decoded)
  }

  @Test
  fun testFunctionSigDecode_nativeTransfer() {
    val decoded = FunctionSignatureDecoder.decode("0x")
    assertNull("Native transfer (no data) should return null", decoded)
  }

  // ===== Unlimited Approval Warning =====

  @Test
  fun testUnlimitedApproval_maxUint256Detected() {
    // MAX_UINT256 = 2^256 - 1 = ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
    val maxUint = "0x095ea7b3" +
        "000000000000000000000000abcdef1234567890abcdef1234567890abcdef12" +
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    assertTrue(
      "MAX_UINT256 approve should be detected as unlimited",
      ApprovalAnalyzer.isUnlimitedApproval(maxUint)
    )
  }

  @Test
  fun testUnlimitedApproval_normalAmountNotDetected() {
    val normalApprove = "0x095ea7b3" +
        "000000000000000000000000abcdef1234567890abcdef1234567890abcdef12" +
        "00000000000000000000000000000000000000000000000000000000000f4240"
    assertFalse(
      "Normal approve amount should not be unlimited",
      ApprovalAnalyzer.isUnlimitedApproval(normalApprove)
    )
  }

  @Test
  fun testUnlimitedApproval_notApproveFunction() {
    val transferData = "0xa9059cbb" +
        "000000000000000000000000abcdef1234567890abcdef1234567890abcdef12" +
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    assertFalse(
      "Non-approve function should not be flagged",
      ApprovalAnalyzer.isUnlimitedApproval(transferData)
    )
  }

  // ===== Chain ID Parsing (hex to decimal) =====

  @Test
  fun testChainIdParsing_hex1() {
    assertEquals(1L, ChainValidator.parseChainId("0x1"))
  }

  @Test
  fun testChainIdParsing_hex38() {
    assertEquals(56L, ChainValidator.parseChainId("0x38"))
  }

  @Test
  fun testChainIdParsing_hex89() {
    assertEquals(137L, ChainValidator.parseChainId("0x89"))
  }

  @Test
  fun testChainIdParsing_invalidHex() {
    assertNull("Invalid hex should return null", ChainValidator.parseChainId("not-hex"))
  }

  @Test
  fun testChainIdParsing_decimalString() {
    assertEquals(1L, ChainValidator.parseChainId("1"))
  }

  // ===== Supported Methods List =====

  @Test
  fun testSupportedMethods_allRequired() {
    val handler = RpcMethodHandler()
    val required = listOf(
      "eth_requestAccounts",
      "eth_accounts",
      "eth_chainId",
      "eth_sendTransaction",
      "personal_sign",
      "eth_signTypedData_v4",
      "wallet_switchEthereumChain",
      "wallet_addEthereumChain",
    )
    for (method in required) {
      assertTrue(
        "$method should be supported",
        handler.validateMethod(method).isSupported
      )
    }
  }

  // ===== wallet_switchEthereumChain Validation =====

  @Test
  fun testWalletSwitchChain_allowedChain() {
    assertTrue(
      "Switching to chain 1 should be allowed",
      ChainValidator.isAllowedChainId(1L)
    )
  }

  @Test
  fun testWalletSwitchChain_unknownChain() {
    assertFalse(
      "Switching to unknown chain should be rejected",
      ChainValidator.isAllowedChainId(42161L) // Arbitrum - not in allowlist
    )
  }
}
