package com.mcw.feature.dappbrowser

/**
 * Domain allowlist policy for the dApp browser.
 *
 * Per tech-spec Decision 9: "Domain policy: known dApp domains (dex.onout.org,
 * app.aave.com) navigate freely; navigation to unknown/external domains is BLOCKED
 * by default — show 'External navigation blocked: {domain}' message. User can add
 * domains to allowlist via Settings."
 *
 * The default allowlist contains known dApp domains from the tech-spec.
 * Users can add custom domains via Settings (persisted in-memory for the session,
 * since no persistent caching per Decision 8).
 */
class DomainPolicy {

  companion object {
    /**
     * Default known dApp domains from tech-spec.
     * These domains are always allowed.
     */
    val DEFAULT_ALLOWED_DOMAINS: Set<String> = setOf(
      "dex.onout.org",
      "app.aave.com",
      "app.uniswap.org",
      "pancakeswap.finance",
      "app.1inch.io",
      "quickswap.exchange",
    )
  }

  /** Mutable set of allowed domains (default + user-added) */
  private val allowedDomains: MutableSet<String> = DEFAULT_ALLOWED_DOMAINS.toMutableSet()

  /**
   * Check if a domain is in the allowlist.
   *
   * @param domain the domain to check (e.g., "dex.onout.org")
   * @return true if the domain is allowed, false if blocked
   */
  fun isDomainAllowed(domain: String): Boolean {
    val normalized = domain.lowercase().trim()
    return normalized in allowedDomains
  }

  /**
   * Add a domain to the allowlist.
   *
   * @param domain the domain to add (e.g., "my-dapp.com")
   */
  fun addAllowedDomain(domain: String) {
    allowedDomains.add(domain.lowercase().trim())
  }

  /**
   * Remove a domain from the allowlist.
   * Cannot remove default domains.
   *
   * @param domain the domain to remove
   * @return true if removed, false if not found or is a default domain
   */
  fun removeAllowedDomain(domain: String): Boolean {
    val normalized = domain.lowercase().trim()
    if (normalized in DEFAULT_ALLOWED_DOMAINS) return false
    return allowedDomains.remove(normalized)
  }

  /**
   * Get all currently allowed domains.
   */
  fun getAllowedDomains(): Set<String> = allowedDomains.toSet()
}
