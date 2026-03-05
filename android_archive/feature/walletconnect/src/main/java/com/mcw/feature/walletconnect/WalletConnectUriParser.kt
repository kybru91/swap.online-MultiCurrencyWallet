package com.mcw.feature.walletconnect

import java.net.URI
import java.net.URLDecoder

/**
 * Parses and validates WalletConnect v2 URIs.
 *
 * A valid WalletConnect v2 URI has the format:
 * `wc:{topic}@2?relay-protocol={protocol}&symKey={key}`
 *
 * Validation steps:
 * 1. Must start with "wc:" scheme
 * 2. Must contain "@2" version indicator (v2 only)
 * 3. Must have "relay-protocol" query parameter
 * 4. Must have "symKey" query parameter
 * 5. Relay URL (if present) must be relay.walletconnect.com or relay.walletconnect.org
 *
 * Uses java.net.URI for host extraction (no Android framework dependency)
 * to enable pure JUnit testing without Robolectric.
 */
object WalletConnectUriParser {

    /** Allowed relay servers — only official WalletConnect relays are trusted */
    val ALLOWED_RELAYS = setOf(
        "relay.walletconnect.com",
        "relay.walletconnect.org"
    )

    /**
     * Parsed components of a WalletConnect v2 URI.
     */
    data class ParsedUri(
        val topic: String,
        val version: Int,
        val relayProtocol: String,
        val symKey: String,
        val relayUrl: String?,
        val fullUri: String
    )

    /**
     * Parses a WalletConnect URI string.
     *
     * @param uri The raw URI string (e.g., from QR scan)
     * @return [ParsedUri] on success
     * @throws IllegalArgumentException with descriptive message on invalid URI
     */
    fun parse(uri: String): ParsedUri {
        val trimmed = uri.trim()

        // Must start with wc: scheme
        if (!trimmed.startsWith("wc:")) {
            throw IllegalArgumentException("Invalid QR code")
        }

        // Extract topic and version: wc:{topic}@{version}?...
        val schemeStripped = trimmed.removePrefix("wc:")
        val atIndex = schemeStripped.indexOf('@')
        if (atIndex == -1) {
            throw IllegalArgumentException("Invalid QR code")
        }

        val topic = schemeStripped.substring(0, atIndex)
        if (topic.isBlank()) {
            throw IllegalArgumentException("Invalid QR code")
        }

        // Extract version number (between @ and ?)
        val afterAt = schemeStripped.substring(atIndex + 1)
        val queryIndex = afterAt.indexOf('?')
        val versionStr = if (queryIndex != -1) afterAt.substring(0, queryIndex) else afterAt
        val version = versionStr.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid QR code")

        // Only support v2
        if (version != 2) {
            throw IllegalArgumentException("Invalid QR code")
        }

        // Parse query parameters manually (no Android Uri dependency)
        val queryString = if (queryIndex != -1) afterAt.substring(queryIndex + 1) else ""
        val params = parseQueryParams(queryString)

        val relayProtocol = params["relay-protocol"]
        if (relayProtocol.isNullOrBlank()) {
            throw IllegalArgumentException("Invalid QR code")
        }

        val symKey = params["symKey"]
        if (symKey.isNullOrBlank()) {
            throw IllegalArgumentException("Invalid QR code")
        }

        // Validate relay URL if present
        val relayUrl = params["relay-url"]
        if (relayUrl != null) {
            validateRelayUrl(relayUrl)
        }

        return ParsedUri(
            topic = topic,
            version = version,
            relayProtocol = relayProtocol,
            symKey = symKey,
            relayUrl = relayUrl,
            fullUri = trimmed
        )
    }

    /**
     * Validates that a relay URL points to an allowed relay server.
     *
     * Extracts the host from the URL using java.net.URI and checks it
     * against [ALLOWED_RELAYS]. This prevents subdomain attacks (e.g.,
     * relay.walletconnect.com.evil.io would have host "relay.walletconnect.com.evil.io").
     *
     * @throws IllegalArgumentException if the relay is not trusted
     */
    fun validateRelayUrl(url: String) {
        val host = try {
            URI(url).host ?: url
        } catch (_: Exception) {
            url
        }

        if (host !in ALLOWED_RELAYS) {
            throw IllegalArgumentException("Untrusted relay server: $host")
        }
    }

    /**
     * Checks whether a string looks like a WalletConnect URI.
     * Quick check before full parsing — useful for QR scanner filtering.
     */
    fun isWalletConnectUri(text: String): Boolean {
        return text.trim().startsWith("wc:") && text.contains("@2")
    }

    /**
     * Parses URL query parameters from a query string.
     * Handles URL-encoded values via URLDecoder.
     *
     * @param query The query string (without leading '?')
     * @return Map of parameter name to decoded value
     */
    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()

        return query.split("&").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val key = URLDecoder.decode(parts[0], "UTF-8")
                val value = URLDecoder.decode(parts[1], "UTF-8")
                key to value
            } else {
                null
            }
        }.toMap()
    }
}
