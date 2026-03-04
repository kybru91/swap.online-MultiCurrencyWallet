package com.mcw.feature.walletconnect

/**
 * Parsed WalletConnect v2 session proposal data for display in the approval dialog.
 *
 * This is a UI-friendly representation of the protocol's session proposal event,
 * containing only the fields needed by SessionProposalDialog to display the dApp
 * details and requested permissions to the user.
 *
 * @property proposerPublicKey The proposer's public key (used as identifier for approve/reject)
 * @property name dApp display name
 * @property description dApp description
 * @property url dApp URL
 * @property icon Optional dApp icon URL
 * @property requiredChains CAIP-2 chain IDs the dApp requires (e.g., ["eip155:1"])
 * @property requiredMethods JSON-RPC methods the dApp requires (e.g., ["eth_sendTransaction"])
 * @property relayUrl Relay server URL from the proposal
 */
data class SessionProposal(
    val proposerPublicKey: String,
    val name: String,
    val description: String,
    val url: String,
    val icon: String? = null,
    val requiredChains: List<String> = emptyList(),
    val requiredMethods: List<String> = emptyList(),
    val relayUrl: String = ""
)
