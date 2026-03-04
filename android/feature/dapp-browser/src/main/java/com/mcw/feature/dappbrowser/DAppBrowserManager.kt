package com.mcw.feature.dappbrowser

import javax.inject.Inject

/**
 * WebView-based dApp browser manager with injected window.ethereum provider.
 *
 * Provides access to the dApp browser's core components:
 * - [DomainPolicy] for URL filtering
 * - [RpcMethodHandler] for EIP-1193 method validation
 * - [ChainValidator] for chain ID allowlisting
 * - [GasValidator] for gas limit thresholds
 * - [OriginValidator] for bridge origin checks
 * - [RpcRateLimiter] for rate limiting bridge calls
 *
 * The manager is injected via Hilt and provides a central point
 * for other modules to interact with the dApp browser configuration.
 */
class DAppBrowserManager @Inject constructor() {

  val domainPolicy = DomainPolicy()
  val methodHandler = RpcMethodHandler()

  /**
   * Check if a URL is safe for WebView navigation.
   */
  fun isUrlAllowed(url: String): Boolean {
    return WebViewUrlValidator.isAllowedUrl(url)
  }

  /**
   * Check if a domain is in the allowlist.
   */
  fun isDomainAllowed(domain: String): Boolean {
    return domainPolicy.isDomainAllowed(domain)
  }

  /**
   * Add a domain to the allowlist.
   */
  fun addAllowedDomain(domain: String) {
    domainPolicy.addAllowedDomain(domain)
  }

  /**
   * Validate gas limit thresholds.
   */
  fun validateGas(gasLimit: Long): GasValidationResult {
    return GasValidator.validate(gasLimit)
  }

  /**
   * Check if a chain ID is in the allowlist.
   */
  fun isChainAllowed(chainId: Long): Boolean {
    return ChainValidator.isAllowedChainId(chainId)
  }
}
