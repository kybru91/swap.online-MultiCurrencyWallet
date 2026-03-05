package com.mcw.feature.dappbrowser

/**
 * Validates and categorizes EIP-1193 RPC methods.
 *
 * Per tech-spec: Implements eth_requestAccounts, eth_accounts, eth_chainId,
 * eth_sendTransaction, personal_sign, eth_signTypedData_v4,
 * wallet_switchEthereumChain, wallet_addEthereumChain.
 *
 * Rejects eth_sign (deprecated, unsafe) and all unknown methods.
 */
class RpcMethodHandler {

  companion object {
    /**
     * Set of EIP-1193 methods supported by this wallet.
     * Only these methods are exposed via the @JavascriptInterface bridge.
     */
    val SUPPORTED_METHODS: Set<String> = setOf(
      "eth_requestAccounts",
      "eth_accounts",
      "eth_chainId",
      "eth_sendTransaction",
      "personal_sign",
      "eth_signTypedData_v4",
      "wallet_switchEthereumChain",
      "wallet_addEthereumChain",
    )

    /**
     * Methods that are explicitly rejected with a specific error message.
     * These are known methods that we refuse to support for security reasons.
     */
    private val REJECTED_METHODS: Map<String, String> = mapOf(
      "eth_sign" to "Unsupported method: eth_sign is deprecated and unsafe",
    )

    /**
     * Methods that require user confirmation (transaction signing, message signing).
     * These trigger the native confirmation dialog before proceeding.
     */
    val CONFIRMATION_REQUIRED_METHODS: Set<String> = setOf(
      "eth_sendTransaction",
      "personal_sign",
      "eth_signTypedData_v4",
    )

    /**
     * Methods that modify chain state (switch/add chain).
     * These emit chainChanged events after successful completion.
     */
    val CHAIN_METHODS: Set<String> = setOf(
      "wallet_switchEthereumChain",
      "wallet_addEthereumChain",
    )
  }

  /**
   * Validate whether a given RPC method is supported.
   *
   * @param method the EIP-1193 method name (e.g., "eth_requestAccounts")
   * @return [MethodValidation] with isSupported flag and optional error message
   */
  fun validateMethod(method: String): MethodValidation {
    // Check explicitly rejected methods first
    val rejectedMessage = REJECTED_METHODS[method]
    if (rejectedMessage != null) {
      return MethodValidation(isSupported = false, errorMessage = rejectedMessage)
    }

    // Check supported methods
    if (method in SUPPORTED_METHODS) {
      return MethodValidation(isSupported = true)
    }

    // Unknown method
    return MethodValidation(
      isSupported = false,
      errorMessage = "Unsupported method: $method",
    )
  }

  /**
   * Check if a method requires user confirmation dialog.
   */
  fun requiresConfirmation(method: String): Boolean {
    return method in CONFIRMATION_REQUIRED_METHODS
  }
}

/**
 * Result of RPC method validation.
 *
 * @param isSupported whether the method is supported by the wallet
 * @param errorMessage human-readable error message if not supported (null if supported)
 */
data class MethodValidation(
  val isSupported: Boolean,
  val errorMessage: String? = null,
)
