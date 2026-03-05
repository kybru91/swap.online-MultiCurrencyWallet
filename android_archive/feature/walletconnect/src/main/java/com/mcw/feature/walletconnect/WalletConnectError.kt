package com.mcw.feature.walletconnect

/**
 * Sealed class representing errors from WalletConnect operations.
 *
 * Each error type maps to a specific user-facing message and recovery action.
 * The UI layer matches on these types to display appropriate dialogs/toasts.
 */
sealed class WalletConnectError(val message: String) {

    /** QR code does not contain a valid WalletConnect URI (wc: scheme with required params) */
    class InvalidQrUri : WalletConnectError("Invalid QR code")

    /** Relay server in URI is not relay.walletconnect.com or relay.walletconnect.org */
    class UntrustedRelay(val relay: String) :
        WalletConnectError("Untrusted relay server: $relay")

    /** Failed to connect to WalletConnect relay — show retry button */
    class RelayConnectionFailed :
        WalletConnectError("Failed to connect to WalletConnect relay")

    /** Session has exceeded the 24-hour maximum lifetime */
    class SessionExpired(val topic: String) :
        WalletConnectError("Session expired")

    /** Generic pairing failure */
    class PairingFailed(val reason: String) :
        WalletConnectError("Pairing failed: $reason")
}
