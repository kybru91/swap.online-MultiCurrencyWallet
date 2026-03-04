package com.mcw.feature.dappbrowser

import java.net.URI
import java.net.URISyntaxException

/**
 * URL validation for WebView navigation per tech-spec Decision 9.
 *
 * Security rules:
 * - Only HTTPS scheme allowed
 * - Blocked: file://, http://, data://, javascript://, about: (except about:blank)
 * - Blocked: localhost, private IPs (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8)
 * - Blocked: 0.0.0.0
 *
 * Reuses the same private IP check logic as RpcUrlValidator from :core:network.
 */
object WebViewUrlValidator {

  /**
   * Validate a URL for WebView navigation.
   *
   * @param url the URL to validate
   * @return true if the URL is safe for navigation, false if blocked
   */
  fun isAllowedUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return false

    // Quick check for blocked schemes (case-insensitive)
    val lower = trimmed.lowercase()
    if (lower.startsWith("javascript:")) return false
    if (lower.startsWith("data:")) return false
    if (lower.startsWith("file:")) return false
    if (lower.startsWith("http:") && !lower.startsWith("https:")) return false

    // Parse URI
    val uri: URI = try {
      URI(trimmed)
    } catch (e: URISyntaxException) {
      return false
    }

    // HTTPS only
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https") return false

    // Must have a host
    val host = uri.host?.lowercase() ?: return false

    // Block localhost
    if (host == "localhost") return false

    // Block private/loopback IPs
    if (isPrivateIp(host)) return false

    return true
  }

  /**
   * Check if a host string is a private or loopback IP address.
   *
   * Checks:
   * - 127.0.0.0/8 (loopback)
   * - 10.0.0.0/8
   * - 172.16.0.0/12
   * - 192.168.0.0/16
   * - 0.0.0.0
   */
  private fun isPrivateIp(host: String): Boolean {
    // Parse as IPv4 dot-notation
    val parts = host.split(".")
    if (parts.size != 4) return false

    val octets = try {
      parts.map { it.toInt() }
    } catch (e: NumberFormatException) {
      return false // Not an IP address (hostname)
    }

    // Validate each octet is in 0..255
    if (octets.any { it < 0 || it > 255 }) return false

    val b0 = octets[0]
    val b1 = octets[1]

    // 0.0.0.0
    if (octets.all { it == 0 }) return true

    // 127.0.0.0/8
    if (b0 == 127) return true

    // 10.0.0.0/8
    if (b0 == 10) return true

    // 172.16.0.0/12
    if (b0 == 172 && b1 in 16..31) return true

    // 192.168.0.0/16
    if (b0 == 192 && b1 == 168) return true

    return false
  }
}
