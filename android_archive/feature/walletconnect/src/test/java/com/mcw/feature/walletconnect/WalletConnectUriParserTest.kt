package com.mcw.feature.walletconnect

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for WalletConnectUriParser.
 *
 * Tests URI parsing, validation, and edge cases.
 * Covers: valid v2 URIs, missing params, wrong version, relay validation,
 * whitespace handling, and quick-check method.
 */
class WalletConnectUriParserTest {

    @Test
    fun testParse_validV2Uri() {
        val uri = "wc:7f6e504bfad60b485450578e05678ed3e8e8c4751d3c6160be17160d63ec90f9@2?relay-protocol=irn&symKey=587d5484ce2a2a6ee3ba1962fdd7e8588e06200c46823bd18fbd67def96ad303"

        val result = WalletConnectUriParser.parse(uri)

        assertEquals("7f6e504bfad60b485450578e05678ed3e8e8c4751d3c6160be17160d63ec90f9", result.topic)
        assertEquals(2, result.version)
        assertEquals("irn", result.relayProtocol)
        assertEquals("587d5484ce2a2a6ee3ba1962fdd7e8588e06200c46823bd18fbd67def96ad303", result.symKey)
        assertNull(result.relayUrl)
        assertEquals(uri, result.fullUri)
    }

    @Test
    fun testParse_uriWithRelayUrl_allowed() {
        val uri = "wc:topic123@2?relay-protocol=irn&symKey=key456&relay-url=wss%3A%2F%2Frelay.walletconnect.com"

        val result = WalletConnectUriParser.parse(uri)

        assertEquals("topic123", result.topic)
        assertEquals("wss://relay.walletconnect.com", result.relayUrl)
    }

    @Test
    fun testParse_uriWithWhitespace() {
        val uri = "  wc:topic123@2?relay-protocol=irn&symKey=key456  "

        val result = WalletConnectUriParser.parse(uri)

        assertEquals("topic123", result.topic)
    }

    @Test
    fun testParse_notWcScheme_throws() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.parse("https://example.com")
        }
        assertEquals("Invalid QR code", exception.message)
    }

    @Test
    fun testParse_emptyString_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.parse("")
        }
    }

    @Test
    fun testParse_v1Uri_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.parse("wc:abc123@1?bridge=https://bridge.com&key=xyz")
        }
    }

    @Test
    fun testParse_missingRelayProtocol_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.parse("wc:topic@2?symKey=key")
        }
    }

    @Test
    fun testParse_missingSymKey_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.parse("wc:topic@2?relay-protocol=irn")
        }
    }

    @Test
    fun testParse_missingVersionSeparator_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.parse("wc:topicwithoutversion")
        }
    }

    @Test
    fun testParse_emptyTopic_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.parse("wc:@2?relay-protocol=irn&symKey=key")
        }
    }

    @Test
    fun testParse_nonNumericVersion_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.parse("wc:topic@abc?relay-protocol=irn&symKey=key")
        }
    }

    @Test
    fun testParse_untrustedRelayUrl_throws() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.parse("wc:topic@2?relay-protocol=irn&symKey=key&relay-url=wss%3A%2F%2Fevil.relay.com")
        }
        assertTrue(exception.message!!.contains("Untrusted relay"))
    }

    @Test
    fun testValidateRelayUrl_walletconnectCom() {
        // Should not throw
        WalletConnectUriParser.validateRelayUrl("wss://relay.walletconnect.com")
    }

    @Test
    fun testValidateRelayUrl_walletconnectOrg() {
        // Should not throw
        WalletConnectUriParser.validateRelayUrl("wss://relay.walletconnect.org")
    }

    @Test
    fun testValidateRelayUrl_customRelay_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.validateRelayUrl("wss://custom.relay.com")
        }
    }

    @Test
    fun testValidateRelayUrl_subdomain_throws() {
        // relay.walletconnect.com.evil.io should be rejected
        assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.validateRelayUrl("wss://relay.walletconnect.com.evil.io")
        }
    }

    @Test
    fun testIsWalletConnectUri_validV2() {
        assertTrue(WalletConnectUriParser.isWalletConnectUri("wc:topic@2?relay-protocol=irn&symKey=key"))
    }

    @Test
    fun testIsWalletConnectUri_v1_returnsFalse() {
        assertFalse(WalletConnectUriParser.isWalletConnectUri("wc:topic@1?bridge=bridge&key=key"))
    }

    @Test
    fun testIsWalletConnectUri_randomString_returnsFalse() {
        assertFalse(WalletConnectUriParser.isWalletConnectUri("hello world"))
    }

    @Test
    fun testIsWalletConnectUri_httpUrl_returnsFalse() {
        assertFalse(WalletConnectUriParser.isWalletConnectUri("https://example.com"))
    }

    @Test
    fun testAllowedRelays_containsExpectedValues() {
        assertEquals(2, WalletConnectUriParser.ALLOWED_RELAYS.size)
        assertTrue(WalletConnectUriParser.ALLOWED_RELAYS.contains("relay.walletconnect.com"))
        assertTrue(WalletConnectUriParser.ALLOWED_RELAYS.contains("relay.walletconnect.org"))
    }
}
