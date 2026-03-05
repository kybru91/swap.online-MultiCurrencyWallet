package com.mcw.feature.walletconnect

import com.mcw.core.storage.SecureStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Unit tests for WalletConnectManager.
 *
 * Tests cover:
 * - Session proposal handling (TDD anchor: testSessionProposal)
 * - Session expiry (TDD anchor: testSessionExpiry)
 * - Session persistence (TDD anchor: testSessionPersistence)
 * - Relay validation (TDD anchor: testRelayValidation)
 * - Invalid QR URI (TDD anchor: testInvalidQrUri)
 * - Session lifecycle (approve, reject, remove, cleanup)
 * - Error handling (relay failure, untrusted relay)
 * - Active session count (for dApps tab badge)
 *
 * Uses a fake TimeProvider to control time for expiry tests.
 * SecureStorage is mocked with an in-memory backing store to simulate
 * real save/load behavior without EncryptedSharedPreferences.
 */
class WalletConnectManagerTest {

    private lateinit var mockStorage: SecureStorage
    private lateinit var fakeTimeProvider: FakeTimeProvider
    private lateinit var manager: WalletConnectManager

    // In-memory backing store for WC sessions JSON
    private var storedSessionsJson: String? = null

    @Before
    fun setUp() {
        storedSessionsJson = null

        mockStorage = mock {
            on { saveWalletConnectSessions(any()) } doAnswer { invocation ->
                storedSessionsJson = invocation.getArgument<String>(0)
                Unit
            }
            on { getWalletConnectSessions() } doAnswer {
                storedSessionsJson
            }
        }

        fakeTimeProvider = FakeTimeProvider()
        manager = WalletConnectManager(mockStorage, fakeTimeProvider)
    }

    // =========================================================================
    // TDD Anchor: testSessionProposal
    // pair URI -> proposal parsed correctly
    // =========================================================================

    @Test
    fun testSessionProposal_validUri_parsedCorrectly() {
        val uri = "wc:7f6e504bfad60b485450578e05678ed3e8e8c4751d3c6160be17160d63ec90f9@2?relay-protocol=irn&symKey=587d5484ce2a2a6ee3ba1962fdd7e8588e06200c46823bd18fbd67def96ad303"

        val result = manager.processQrCode(uri)

        assertNotNull("Parsed URI should not be null", result)
        assertEquals("7f6e504bfad60b485450578e05678ed3e8e8c4751d3c6160be17160d63ec90f9", result!!.topic)
        assertEquals(2, result.version)
        assertEquals("irn", result.relayProtocol)
        assertEquals("587d5484ce2a2a6ee3ba1962fdd7e8588e06200c46823bd18fbd67def96ad303", result.symKey)
        assertNull("No error should be set", manager.error.value)
    }

    @Test
    fun testSessionProposal_onProposal_pendingProposalSet() {
        val proposal = SessionProposal(
            proposerPublicKey = "abc123",
            name = "My dApp",
            description = "A test dApp",
            url = "https://mydapp.com",
            icon = "https://mydapp.com/icon.png",
            requiredChains = listOf("eip155:1", "eip155:137"),
            requiredMethods = listOf("eth_sendTransaction", "personal_sign")
        )

        manager.onSessionProposal(proposal)

        val pending = manager.pendingProposal.value
        assertNotNull("Pending proposal should be set", pending)
        assertEquals("My dApp", pending!!.name)
        assertEquals("https://mydapp.com", pending.url)
        assertEquals(listOf("eip155:1", "eip155:137"), pending.requiredChains)
        assertEquals(listOf("eth_sendTransaction", "personal_sign"), pending.requiredMethods)
    }

    @Test
    fun testSessionProposal_approveCreatesSession() {
        val proposal = SessionProposal(
            proposerPublicKey = "key1",
            name = "DApp1",
            description = "Test",
            url = "https://dapp1.com",
            icon = "https://dapp1.com/icon.png"
        )

        fakeTimeProvider.time = 1000000L
        manager.onSessionProposal(proposal)

        val session = manager.approveSession(
            topic = "topic1",
            approvedChains = listOf("eip155:1"),
            approvedMethods = listOf("eth_sendTransaction")
        )

        assertEquals("topic1", session.topic)
        assertEquals("DApp1", session.peerName)
        assertEquals("https://dapp1.com", session.peerUrl)
        assertEquals("https://dapp1.com/icon.png", session.peerIcon)
        assertEquals(listOf("eip155:1"), session.chains)
        assertEquals(listOf("eth_sendTransaction"), session.methods)
        assertEquals(1000000L, session.createdAt)
        // Pending proposal should be cleared after approval
        assertNull("Pending proposal should be cleared", manager.pendingProposal.value)
    }

    // =========================================================================
    // TDD Anchor: testSessionExpiry
    // session created 25h ago -> removed on app launch
    // =========================================================================

    @Test
    fun testSessionExpiry_25hOldSession_removedOnAppLaunch() {
        // Set time to "now" (e.g., hour 26)
        val twentySixHoursMs = 26 * 60 * 60 * 1000L
        fakeTimeProvider.time = twentySixHoursMs

        // Pre-populate storage with a session created at hour 0 (26h ago -- expired)
        // and a session created at hour 3 (23h ago -- still valid)
        val expiredSession = WalletConnectSession(
            topic = "expired-topic",
            peerName = "OldDApp",
            peerUrl = "https://old.com",
            createdAt = 0L // 26 hours ago relative to fakeTimeProvider
        )
        val validSession = WalletConnectSession(
            topic = "valid-topic",
            peerName = "NewDApp",
            peerUrl = "https://new.com",
            createdAt = 3 * 60 * 60 * 1000L // 23 hours ago -- still valid
        )

        storedSessionsJson = SessionSerializer.toJson(listOf(expiredSession, validSession))

        // Simulate app launch
        manager.initialize()

        // Expired session should be removed, valid session should remain
        assertEquals(1, manager.sessions.value.size)
        assertEquals("valid-topic", manager.sessions.value[0].topic)
        assertNull("Expired session should be gone", manager.getSession("expired-topic"))
    }

    @Test
    fun testSessionExpiry_exactly24hOldSession_removed() {
        // Session at exactly 24h boundary should be removed (>= check)
        val twentyFourHoursMs = 24 * 60 * 60 * 1000L
        fakeTimeProvider.time = twentyFourHoursMs

        val borderlineSession = WalletConnectSession(
            topic = "borderline",
            peerName = "BorderDApp",
            peerUrl = "https://border.com",
            createdAt = 0L // exactly 24h ago
        )

        storedSessionsJson = SessionSerializer.toJson(listOf(borderlineSession))

        manager.initialize()

        assertEquals("Session at exactly 24h should be removed", 0, manager.sessions.value.size)
    }

    @Test
    fun testSessionExpiry_23h59mOldSession_retained() {
        // Session at 23h59m should be retained (just under 24h)
        val almostExpired = 24 * 60 * 60 * 1000L - 60_000L // 23h59m
        fakeTimeProvider.time = almostExpired

        val session = WalletConnectSession(
            topic = "almost-expired",
            peerName = "AlmostDApp",
            peerUrl = "https://almost.com",
            createdAt = 0L
        )

        storedSessionsJson = SessionSerializer.toJson(listOf(session))

        manager.initialize()

        assertEquals("Session at 23h59m should be retained", 1, manager.sessions.value.size)
    }

    @Test
    fun testSessionExpiry_cleanupReturnsExpiredTopics() {
        fakeTimeProvider.time = 25 * 60 * 60 * 1000L

        val expired1 = WalletConnectSession(
            topic = "expired1", peerName = "D1", peerUrl = "https://d1.com", createdAt = 0L
        )
        val expired2 = WalletConnectSession(
            topic = "expired2", peerName = "D2", peerUrl = "https://d2.com", createdAt = 1000L
        )
        val valid = WalletConnectSession(
            topic = "valid", peerName = "D3", peerUrl = "https://d3.com",
            createdAt = 2 * 60 * 60 * 1000L // 23h ago
        )

        storedSessionsJson = SessionSerializer.toJson(listOf(expired1, expired2, valid))

        manager.initialize()
        // After initialize, only valid remains
        assertEquals(1, manager.sessions.value.size)
        assertEquals("valid", manager.sessions.value[0].topic)
    }

    // =========================================================================
    // TDD Anchor: testSessionPersistence
    // approve session -> stored, restart -> restored
    // =========================================================================

    @Test
    fun testSessionPersistence_approveAndRestore() {
        fakeTimeProvider.time = 5000L

        // Simulate a proposal and approval
        val proposal = SessionProposal(
            proposerPublicKey = "key1",
            name = "PersistDApp",
            description = "Test persistence",
            url = "https://persist.com",
            icon = "https://persist.com/icon.png",
            requiredChains = listOf("eip155:1"),
            requiredMethods = listOf("personal_sign")
        )

        manager.onSessionProposal(proposal)
        manager.approveSession(
            topic = "persist-topic",
            approvedChains = listOf("eip155:1"),
            approvedMethods = listOf("personal_sign")
        )

        // Verify session is stored
        assertNotNull("Sessions should be persisted", storedSessionsJson)
        assertTrue("JSON should contain topic", storedSessionsJson!!.contains("persist-topic"))

        // Simulate app restart: create new manager with same storage mock
        val newManager = WalletConnectManager(mockStorage, fakeTimeProvider)
        newManager.initialize()

        // Session should be restored
        assertEquals(1, newManager.sessions.value.size)
        val restored = newManager.sessions.value[0]
        assertEquals("persist-topic", restored.topic)
        assertEquals("PersistDApp", restored.peerName)
        assertEquals("https://persist.com", restored.peerUrl)
        assertEquals("https://persist.com/icon.png", restored.peerIcon)
        assertEquals(listOf("eip155:1"), restored.chains)
        assertEquals(listOf("personal_sign"), restored.methods)
        assertEquals(5000L, restored.createdAt)
    }

    @Test
    fun testSessionPersistence_multipleSessions() {
        fakeTimeProvider.time = 1000L

        // Approve two sessions
        val proposal1 = SessionProposal(
            proposerPublicKey = "key1", name = "DApp1", description = "d1",
            url = "https://d1.com"
        )
        manager.onSessionProposal(proposal1)
        manager.approveSession("topic1", listOf("eip155:1"), listOf("eth_sendTransaction"))

        fakeTimeProvider.time = 2000L
        val proposal2 = SessionProposal(
            proposerPublicKey = "key2", name = "DApp2", description = "d2",
            url = "https://d2.com"
        )
        manager.onSessionProposal(proposal2)
        manager.approveSession("topic2", listOf("eip155:137"), listOf("personal_sign"))

        // Restart
        val newManager = WalletConnectManager(mockStorage, fakeTimeProvider)
        newManager.initialize()

        assertEquals(2, newManager.sessions.value.size)
        assertEquals("topic1", newManager.sessions.value[0].topic)
        assertEquals("topic2", newManager.sessions.value[1].topic)
    }

    @Test
    fun testSessionPersistence_removeSessionPersists() {
        fakeTimeProvider.time = 1000L

        val proposal = SessionProposal(
            proposerPublicKey = "key1", name = "DApp1", description = "d1",
            url = "https://d1.com"
        )
        manager.onSessionProposal(proposal)
        manager.approveSession("topic1", listOf("eip155:1"), listOf("eth_sendTransaction"))

        // Remove session
        manager.removeSession("topic1")

        // Restart
        val newManager = WalletConnectManager(mockStorage, fakeTimeProvider)
        newManager.initialize()

        assertEquals("Removed session should not be restored", 0, newManager.sessions.value.size)
    }

    // =========================================================================
    // TDD Anchor: testRelayValidation
    // relay.walletconnect.com -> allowed, custom.relay.com -> rejected
    // =========================================================================

    @Test
    fun testRelayValidation_walletconnectCom_allowed() {
        // Should not throw
        WalletConnectUriParser.validateRelayUrl("wss://relay.walletconnect.com")
    }

    @Test
    fun testRelayValidation_walletconnectOrg_allowed() {
        // Should not throw
        WalletConnectUriParser.validateRelayUrl("wss://relay.walletconnect.org")
    }

    @Test
    fun testRelayValidation_customRelay_rejected() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.validateRelayUrl("wss://custom.relay.com")
        }
        assertTrue(
            "Error should mention untrusted relay",
            exception.message!!.contains("Untrusted relay")
        )
    }

    @Test
    fun testRelayValidation_maliciousRelay_rejected() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            WalletConnectUriParser.validateRelayUrl("wss://evil.walletconnect.com.attacker.io")
        }
        assertTrue(exception.message!!.contains("Untrusted relay"))
    }

    @Test
    fun testRelayValidation_proposalWithUntrustedRelay_rejected() {
        val proposal = SessionProposal(
            proposerPublicKey = "key1",
            name = "EvilDApp",
            description = "Evil",
            url = "https://evil.com",
            relayUrl = "wss://evil-relay.com"
        )

        manager.onSessionProposal(proposal)

        // Proposal should be rejected -- not set as pending
        assertNull("Proposal with untrusted relay should not be set", manager.pendingProposal.value)
        // Error should be set
        assertNotNull("Error should be set", manager.error.value)
        assertTrue(manager.error.value is WalletConnectError.UntrustedRelay)
    }

    // =========================================================================
    // TDD Anchor: testInvalidQrUri
    // invalid URI -> error 'Invalid QR code'
    // =========================================================================

    @Test
    fun testInvalidQrUri_randomText() {
        val result = manager.processQrCode("just some random text")

        assertNull("Should return null for random text", result)
        assertNotNull("Error should be set", manager.error.value)
        assertTrue(manager.error.value is WalletConnectError.InvalidQrUri)
        assertEquals("Invalid QR code", manager.error.value!!.message)
    }

    @Test
    fun testInvalidQrUri_httpUrl() {
        val result = manager.processQrCode("https://example.com")

        assertNull(result)
        assertTrue(manager.error.value is WalletConnectError.InvalidQrUri)
        assertEquals("Invalid QR code", manager.error.value!!.message)
    }

    @Test
    fun testInvalidQrUri_wcV1Uri() {
        // WC v1 URI (version 1 -- not supported)
        val result = manager.processQrCode("wc:abc123@1?bridge=https://bridge.com&key=xyz")

        assertNull(result)
        assertTrue(manager.error.value is WalletConnectError.InvalidQrUri)
    }

    @Test
    fun testInvalidQrUri_emptyString() {
        val result = manager.processQrCode("")

        assertNull(result)
        assertTrue(manager.error.value is WalletConnectError.InvalidQrUri)
    }

    @Test
    fun testInvalidQrUri_wcWithoutRequiredParams() {
        // Missing symKey
        val result = manager.processQrCode("wc:topic@2?relay-protocol=irn")

        assertNull(result)
        assertTrue(manager.error.value is WalletConnectError.InvalidQrUri)
    }

    // =========================================================================
    // Session Lifecycle Tests
    // =========================================================================

    @Test
    fun testRejectSession_clearsPendingProposal() {
        val proposal = SessionProposal(
            proposerPublicKey = "key1", name = "DApp", description = "d",
            url = "https://d.com"
        )

        manager.onSessionProposal(proposal)
        assertNotNull(manager.pendingProposal.value)

        manager.rejectSession()
        assertNull("Pending proposal should be cleared after rejection", manager.pendingProposal.value)
    }

    @Test
    fun testApproveSession_withoutProposal_throws() {
        assertThrows(IllegalStateException::class.java) {
            manager.approveSession("topic", listOf("eip155:1"), listOf("eth_sendTransaction"))
        }
    }

    @Test
    fun testRemoveSession_nonExistent_noError() {
        // Should not throw or error when removing a session that does not exist
        manager.removeSession("nonexistent-topic")
        assertEquals(0, manager.sessions.value.size)
    }

    @Test
    fun testActiveSessionCount() {
        fakeTimeProvider.time = 1000L

        assertEquals(0, manager.activeSessionCount)

        val proposal1 = SessionProposal(
            proposerPublicKey = "k1", name = "D1", description = "d1", url = "https://d1.com"
        )
        manager.onSessionProposal(proposal1)
        manager.approveSession("t1", listOf("eip155:1"), listOf("eth_sendTransaction"))

        assertEquals(1, manager.activeSessionCount)

        val proposal2 = SessionProposal(
            proposerPublicKey = "k2", name = "D2", description = "d2", url = "https://d2.com"
        )
        manager.onSessionProposal(proposal2)
        manager.approveSession("t2", listOf("eip155:1"), listOf("personal_sign"))

        assertEquals(2, manager.activeSessionCount)

        manager.removeSession("t1")
        assertEquals(1, manager.activeSessionCount)
    }

    @Test
    fun testIsSessionExpired_validSession() {
        fakeTimeProvider.time = 1000L

        val proposal = SessionProposal(
            proposerPublicKey = "k1", name = "D1", description = "d1", url = "https://d1.com"
        )
        manager.onSessionProposal(proposal)
        manager.approveSession("t1", listOf("eip155:1"), listOf("eth_sendTransaction"))

        // Still at 1000ms -- 0ms elapsed
        assertFalse(manager.isSessionExpired("t1"))

        // Advance to 23h -- still valid
        fakeTimeProvider.time = 1000L + 23 * 60 * 60 * 1000L
        assertFalse(manager.isSessionExpired("t1"))

        // Advance to 24h -- expired
        fakeTimeProvider.time = 1000L + 24 * 60 * 60 * 1000L
        assertTrue(manager.isSessionExpired("t1"))
    }

    @Test
    fun testIsSessionExpired_nonExistentSession() {
        // Non-existent session should be treated as expired
        assertTrue(manager.isSessionExpired("nonexistent"))
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    @Test
    fun testRelayConnectionFailed_setsError() {
        manager.onRelayConnectionFailed()

        assertNotNull(manager.error.value)
        assertTrue(manager.error.value is WalletConnectError.RelayConnectionFailed)
        assertEquals("Failed to connect to WalletConnect relay", manager.error.value!!.message)
    }

    @Test
    fun testClearError() {
        manager.onRelayConnectionFailed()
        assertNotNull(manager.error.value)

        manager.clearError()
        assertNull(manager.error.value)
    }

    @Test
    fun testProcessQrCode_validUri_clearsError() {
        // Set an error first
        manager.onRelayConnectionFailed()
        assertNotNull(manager.error.value)

        // Process a valid URI -- error should be cleared
        val uri = "wc:topic123@2?relay-protocol=irn&symKey=key123"
        manager.processQrCode(uri)

        assertNull("Error should be cleared on valid URI", manager.error.value)
    }

    // =========================================================================
    // Initialization Tests
    // =========================================================================

    @Test
    fun testInitialize_emptyStorage() {
        manager.initialize()
        assertEquals(0, manager.sessions.value.size)
    }

    @Test
    fun testInitialize_corruptedJson() {
        storedSessionsJson = "not valid json{{{"
        manager.initialize()
        assertEquals("Corrupted JSON should result in empty sessions", 0, manager.sessions.value.size)
    }

    @Test
    fun testGetSession_found() {
        fakeTimeProvider.time = 1000L
        val proposal = SessionProposal(
            proposerPublicKey = "k1", name = "D1", description = "d1", url = "https://d1.com"
        )
        manager.onSessionProposal(proposal)
        manager.approveSession("t1", listOf("eip155:1"), listOf("personal_sign"))

        val session = manager.getSession("t1")
        assertNotNull(session)
        assertEquals("D1", session!!.peerName)
    }

    @Test
    fun testGetSession_notFound() {
        assertNull(manager.getSession("nonexistent"))
    }

    /**
     * Fake TimeProvider for controlling time in tests.
     */
    private class FakeTimeProvider : TimeProvider {
        var time: Long = 0L
        override fun currentTimeMillis(): Long = time
    }
}
