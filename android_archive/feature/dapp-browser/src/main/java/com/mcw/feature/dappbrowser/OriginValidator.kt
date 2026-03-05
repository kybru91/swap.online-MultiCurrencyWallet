package com.mcw.feature.dappbrowser

import java.net.URI
import java.net.URISyntaxException

/**
 * Validates WebView URL origin on every JS bridge call.
 *
 * Per tech-spec Decision 9: "Validate WebView.url origin on every bridge call —
 * reject if URL has changed from the approved dApp origin."
 *
 * Origin comparison uses scheme + host + port (standard Web Origin definition).
 * Path, query, and fragment are ignored.
 */
class OriginValidator {

  /**
   * Check if the current WebView URL has the same origin as the approved dApp URL.
   *
   * @param approvedUrl the URL that was originally approved (e.g., "https://dex.onout.org")
   * @param currentUrl the current WebView.url value (may be null if WebView has no URL)
   * @return true if origins match, false if mismatch or either URL is invalid/null
   */
  fun validateOrigin(approvedUrl: String, currentUrl: String?): Boolean {
    if (currentUrl == null) return false

    val approvedOrigin = extractOrigin(approvedUrl) ?: return false
    val currentOrigin = extractOrigin(currentUrl) ?: return false

    return approvedOrigin == currentOrigin
  }

  /**
   * Extract the origin (scheme://host:port) from a URL.
   *
   * @param url the URL to extract origin from
   * @return normalized origin string, or null if URL is invalid
   */
  private fun extractOrigin(url: String): String? {
    return try {
      val uri = URI(url)
      val scheme = uri.scheme?.lowercase() ?: return null
      val host = uri.host?.lowercase() ?: return null
      val port = uri.port // -1 if not specified

      if (port == -1 || isDefaultPort(scheme, port)) {
        "$scheme://$host"
      } else {
        "$scheme://$host:$port"
      }
    } catch (e: URISyntaxException) {
      null
    }
  }

  /**
   * Check if a port is the default for its scheme (443 for HTTPS, 80 for HTTP).
   * Default ports are omitted from the origin string for normalization.
   */
  private fun isDefaultPort(scheme: String, port: Int): Boolean {
    return (scheme == "https" && port == 443) || (scheme == "http" && port == 80)
  }
}
