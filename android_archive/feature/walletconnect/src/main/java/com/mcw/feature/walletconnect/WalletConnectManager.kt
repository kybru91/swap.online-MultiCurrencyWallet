package com.mcw.feature.walletconnect

import com.mcw.core.storage.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WalletConnect v2 wallet-side SDK integration manager.
 *
 * Responsibilities:
 * - Parse and validate WalletConnect URIs from QR scanner
 * - Manage session lifecycle: pair, approve, reject, cleanup
 * - Persist sessions in EncryptedSharedPreferences via [SecureStorage]
 * - Enforce 24-hour max session lifetime (cleanup on app launch)
 * - Validate relay servers (only relay.walletconnect.com / relay.walletconnect.org)
 * - Expose active session count for dApps tab badge
 *
 * The actual WalletConnect Sign SDK pairing will be wired during integration phase.
 * This manager handles session state and validation; the SDK callback bridge is external.
 *
 * Thread safety: all session state is managed via [_sessions] StateFlow.
 * Public methods are not synchronized — callers must ensure sequential access
 * for approve/reject operations (the UI naturally serializes these via user interaction).
 */
@Singleton
class WalletConnectManager @Inject constructor(
    private val secureStorage: SecureStorage,
    private val timeProvider: TimeProvider
) {

    /**
     * Current active sessions, observable by UI for badge count and session list.
     */
    private val _sessions = MutableStateFlow<List<WalletConnectSession>>(emptyList())
    val sessions: StateFlow<List<WalletConnectSession>> = _sessions.asStateFlow()

    /**
     * Most recent error, observable by UI for error display.
     * Null when no error is pending.
     */
    private val _error = MutableStateFlow<WalletConnectError?>(null)
    val error: StateFlow<WalletConnectError?> = _error.asStateFlow()

    /**
     * Current pending session proposal, observable by UI for approval dialog.
     * Null when no proposal is pending.
     */
    private val _pendingProposal = MutableStateFlow<SessionProposal?>(null)
    val pendingProposal: StateFlow<SessionProposal?> = _pendingProposal.asStateFlow()

    /**
     * Number of active (non-expired) sessions for dApps tab badge.
     */
    val activeSessionCount: Int
        get() = _sessions.value.size

    /**
     * Initializes the manager: loads persisted sessions and cleans up expired ones.
     * Must be called on every app launch.
     */
    fun initialize() {
        loadSessions()
        cleanupExpiredSessions()
    }

    /**
     * Processes a scanned QR code string.
     *
     * Validates the URI format and relay server, then initiates pairing.
     * On validation failure, sets [error] state with appropriate [WalletConnectError].
     *
     * @param qrContent Raw string from QR scanner
     * @return Parsed URI data on success, null on failure (error set in [_error])
     */
    fun processQrCode(qrContent: String): WalletConnectUriParser.ParsedUri? {
        return try {
            // parse() internally validates relay URL if present
            val parsed = WalletConnectUriParser.parse(qrContent)

            _error.value = null
            parsed
        } catch (e: IllegalArgumentException) {
            val errorMsg = e.message ?: "Invalid QR code"
            _error.value = if (errorMsg.startsWith("Untrusted relay")) {
                WalletConnectError.UntrustedRelay(errorMsg.substringAfter("Untrusted relay server: "))
            } else {
                WalletConnectError.InvalidQrUri()
            }
            null
        }
    }

    /**
     * Handles a session proposal from the WalletConnect Sign SDK.
     *
     * Stores the proposal for display in the approval dialog. The UI observes
     * [pendingProposal] and shows SessionProposalDialog when non-null.
     *
     * @param proposal Parsed proposal data
     */
    fun onSessionProposal(proposal: SessionProposal) {
        // Validate relay URL from the proposal
        if (proposal.relayUrl.isNotBlank()) {
            try {
                WalletConnectUriParser.validateRelayUrl(proposal.relayUrl)
            } catch (e: IllegalArgumentException) {
                val host = proposal.relayUrl
                _error.value = WalletConnectError.UntrustedRelay(host)
                return
            }
        }

        _pendingProposal.value = proposal
    }

    /**
     * Approves the current pending session proposal.
     *
     * Creates a [WalletConnectSession] with the current timestamp, persists it,
     * and clears the pending proposal.
     *
     * @param topic Session topic assigned by the protocol after approval
     * @param approvedChains CAIP-2 chain IDs the user approved
     * @param approvedMethods JSON-RPC methods the user approved
     * @return The created session
     * @throws IllegalStateException if no proposal is pending
     */
    fun approveSession(
        topic: String,
        approvedChains: List<String>,
        approvedMethods: List<String>
    ): WalletConnectSession {
        val proposal = _pendingProposal.value
            ?: throw IllegalStateException("No pending session proposal")

        val session = WalletConnectSession(
            topic = topic,
            peerName = proposal.name,
            peerUrl = proposal.url,
            peerIcon = proposal.icon,
            chains = approvedChains,
            methods = approvedMethods,
            createdAt = timeProvider.currentTimeMillis()
        )

        val currentSessions = _sessions.value.toMutableList()
        currentSessions.add(session)
        _sessions.value = currentSessions
        persistSessions(currentSessions)

        _pendingProposal.value = null
        return session
    }

    /**
     * Rejects the current pending session proposal.
     * Clears the pending proposal without creating a session.
     */
    fun rejectSession() {
        _pendingProposal.value = null
    }

    /**
     * Removes a session by topic.
     * Called when user disconnects or session is expired.
     *
     * @param topic Session topic to remove
     */
    fun removeSession(topic: String) {
        val currentSessions = _sessions.value.toMutableList()
        currentSessions.removeAll { it.topic == topic }
        _sessions.value = currentSessions
        persistSessions(currentSessions)
    }

    /**
     * Cleans up sessions that have exceeded the 24-hour maximum lifetime.
     *
     * Called on every app launch (from [initialize]). No background job needed —
     * sessions are only relevant when the app is active.
     *
     * @return List of expired session topics that were removed
     */
    fun cleanupExpiredSessions(): List<String> {
        val now = timeProvider.currentTimeMillis()
        val currentSessions = _sessions.value
        val (active, expired) = currentSessions.partition { session ->
            (now - session.createdAt) < WalletConnectSession.MAX_SESSION_LIFETIME_MS
        }

        if (expired.isNotEmpty()) {
            _sessions.value = active
            persistSessions(active)
        }

        return expired.map { it.topic }
    }

    /**
     * Checks whether a specific session has expired.
     *
     * @param topic Session topic to check
     * @return true if session exists and has exceeded 24-hour lifetime
     */
    fun isSessionExpired(topic: String): Boolean {
        val session = _sessions.value.find { it.topic == topic } ?: return true
        val now = timeProvider.currentTimeMillis()
        return (now - session.createdAt) >= WalletConnectSession.MAX_SESSION_LIFETIME_MS
    }

    /**
     * Reports a relay connection failure.
     * Sets error state for UI to display with retry button.
     */
    fun onRelayConnectionFailed() {
        _error.value = WalletConnectError.RelayConnectionFailed()
    }

    /**
     * Clears the current error state.
     * Called when user dismisses error or retries.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Gets a session by topic.
     *
     * @param topic Session topic
     * @return The session, or null if not found
     */
    fun getSession(topic: String): WalletConnectSession? {
        return _sessions.value.find { it.topic == topic }
    }

    // --- Private Helpers ---

    /**
     * Loads sessions from EncryptedSharedPreferences.
     */
    private fun loadSessions() {
        val json = secureStorage.getWalletConnectSessions()
        _sessions.value = SessionSerializer.fromJson(json)
    }

    /**
     * Persists sessions to EncryptedSharedPreferences.
     */
    private fun persistSessions(sessions: List<WalletConnectSession>) {
        val json = SessionSerializer.toJson(sessions)
        secureStorage.saveWalletConnectSessions(json)
    }
}
