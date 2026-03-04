package com.mcw.feature.walletconnect

/**
 * Represents a persisted WalletConnect v2 session.
 *
 * Sessions are stored in EncryptedSharedPreferences as JSON via SecureStorage.
 * Each session has a [createdAt] timestamp used for 24-hour expiry enforcement:
 * on every app launch, sessions older than [MAX_SESSION_LIFETIME_MS] are deleted.
 *
 * @property topic Unique session identifier from WalletConnect protocol
 * @property peerName Display name of the connected dApp
 * @property peerUrl URL of the connected dApp
 * @property peerIcon Optional icon URL of the connected dApp
 * @property chains List of CAIP-2 chain IDs approved for this session (e.g., "eip155:1")
 * @property methods List of JSON-RPC methods approved for this session
 * @property createdAt Epoch milliseconds when session was approved
 */
data class WalletConnectSession(
    val topic: String,
    val peerName: String,
    val peerUrl: String,
    val peerIcon: String? = null,
    val chains: List<String> = emptyList(),
    val methods: List<String> = emptyList(),
    val createdAt: Long
) {
    companion object {
        /** Maximum session lifetime: 24 hours in milliseconds */
        const val MAX_SESSION_LIFETIME_MS: Long = 24 * 60 * 60 * 1000L
    }
}
