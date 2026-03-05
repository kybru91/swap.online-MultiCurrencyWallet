package com.mcw.feature.dappbrowser

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the dApp Browser screen.
 *
 * Manages:
 * - Connected dApp state (origin, chain, accounts)
 * - Transaction confirmation dialog state
 * - Domain policy decisions
 * - RPC request processing and response dispatch
 *
 * Note: Not @HiltViewModel because it requires a WebView reference for the bridge.
 * Created manually in the Composable with parameters.
 */
class DAppBrowserViewModel(
  private val ethAddress: String,
  initialChainId: Long = 1L,
) : ViewModel() {

  val methodHandler = RpcMethodHandler()
  val chainValidator = ChainValidator
  val gasValidator = GasValidator
  val domainPolicy = DomainPolicy()
  val originValidator = OriginValidator()
  val rateLimiter = RpcRateLimiter()

  private val _uiState = MutableStateFlow(DAppBrowserUiState())
  val uiState: StateFlow<DAppBrowserUiState> = _uiState.asStateFlow()

  private val _activeChainId = MutableStateFlow(initialChainId)
  val activeChainId: StateFlow<Long> = _activeChainId.asStateFlow()

  /** Pending confirmation request (only one at a time per tech-spec) */
  private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
  val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

  /**
   * Process an RPC request from the JS bridge.
   *
   * @param callbackId the JS callback ID
   * @param method the EIP-1193 method name
   * @param paramsJson the JSON-encoded params array
   * @param bridge the bridge instance for sending responses
   */
  fun handleRequest(callbackId: Int, method: String, paramsJson: String, bridge: EthereumBridge) {
    // Validate method
    val validation = methodHandler.validateMethod(method)
    if (!validation.isSupported) {
      bridge.sendError(
        callbackId,
        EthereumBridge.ERROR_UNSUPPORTED_METHOD,
        validation.errorMessage ?: "Unsupported method",
      )
      return
    }

    when (method) {
      "eth_requestAccounts" -> handleRequestAccounts(callbackId, bridge)
      "eth_accounts" -> handleAccounts(callbackId, bridge)
      "eth_chainId" -> handleChainId(callbackId, bridge)
      "eth_sendTransaction" -> handleSendTransaction(callbackId, paramsJson, bridge)
      "personal_sign" -> handlePersonalSign(callbackId, paramsJson, bridge)
      "eth_signTypedData_v4" -> handleSignTypedData(callbackId, paramsJson, bridge)
      "wallet_switchEthereumChain" -> handleSwitchChain(callbackId, paramsJson, bridge)
      "wallet_addEthereumChain" -> handleAddChain(callbackId, paramsJson, bridge)
    }
  }

  private fun handleRequestAccounts(callbackId: Int, bridge: EthereumBridge) {
    // Show connection dialog, then return accounts
    // For now, auto-approve and return the wallet address
    _uiState.value = _uiState.value.copy(isConnected = true)
    bridge.sendResult(callbackId, "[\"$ethAddress\"]")
    bridge.emitEvent("accountsChanged", "[\"$ethAddress\"]")
    bridge.emitEvent("connect", "\"${chainIdToHex(_activeChainId.value)}\"")
  }

  private fun handleAccounts(callbackId: Int, bridge: EthereumBridge) {
    if (_uiState.value.isConnected) {
      bridge.sendResult(callbackId, "[\"$ethAddress\"]")
    } else {
      bridge.sendResult(callbackId, "[]")
    }
  }

  private fun handleChainId(callbackId: Int, bridge: EthereumBridge) {
    bridge.sendResult(callbackId, "\"${chainIdToHex(_activeChainId.value)}\"")
  }

  private fun handleSendTransaction(callbackId: Int, paramsJson: String, bridge: EthereumBridge) {
    val request = RpcRequestParser.parse("{\"method\":\"eth_sendTransaction\",\"params\":$paramsJson}")
    if (request == null || request.params.isEmpty()) {
      bridge.sendError(callbackId, EthereumBridge.ERROR_INVALID_PARAMS, "Invalid transaction params")
      return
    }

    // Parse transaction object
    val txObj = request.params[0]
    val txJson = txObj.toString()

    // Validate gas limit if present
    val gasLimitStr = extractJsonField(txJson, "gas") ?: extractJsonField(txJson, "gasLimit")
    if (gasLimitStr != null) {
      val gasLimit = ChainValidator.parseChainId(gasLimitStr) // reuse hex parser
      if (gasLimit != null) {
        val gasResult = GasValidator.validate(gasLimit)
        if (gasResult == GasValidationResult.REJECT) {
          bridge.sendError(callbackId, EthereumBridge.ERROR_INVALID_PARAMS, "Gas limit exceeds maximum (15M)")
          return
        }
      }
    }

    // Validate calldata size
    val data = extractJsonField(txJson, "data")
    if (data != null && !RpcParamValidator.isCalldataWithinLimit(data)) {
      bridge.sendError(callbackId, EthereumBridge.ERROR_INVALID_PARAMS, "Calldata exceeds 64KB limit")
      return
    }

    // Decode function signature for display
    val decodedFunction = if (data != null) FunctionSignatureDecoder.decode(data) else null
    val isUnlimitedApproval = if (data != null) ApprovalAnalyzer.isUnlimitedApproval(data) else false
    val gasWarning = if (gasLimitStr != null) {
      val gasLimit = ChainValidator.parseChainId(gasLimitStr)
      gasLimit != null && GasValidator.validate(gasLimit) == GasValidationResult.WARNING
    } else false

    // Show confirmation dialog
    _pendingConfirmation.value = PendingConfirmation(
      callbackId = callbackId,
      method = "eth_sendTransaction",
      displayInfo = TransactionDisplayInfo(
        to = extractJsonField(txJson, "to") ?: "unknown",
        value = extractJsonField(txJson, "value") ?: "0x0",
        decodedFunction = decodedFunction,
        isUnlimitedApproval = isUnlimitedApproval,
        gasWarning = gasWarning,
      ),
      bridge = bridge,
      rawParams = paramsJson,
    )
  }

  private fun handlePersonalSign(callbackId: Int, paramsJson: String, bridge: EthereumBridge) {
    _pendingConfirmation.value = PendingConfirmation(
      callbackId = callbackId,
      method = "personal_sign",
      displayInfo = SigningDisplayInfo(
        message = paramsJson,
        isHumanReadable = isHumanReadableMessage(paramsJson),
      ),
      bridge = bridge,
      rawParams = paramsJson,
    )
  }

  private fun handleSignTypedData(callbackId: Int, paramsJson: String, bridge: EthereumBridge) {
    _pendingConfirmation.value = PendingConfirmation(
      callbackId = callbackId,
      method = "eth_signTypedData_v4",
      displayInfo = SigningDisplayInfo(
        message = paramsJson,
        isHumanReadable = true,
      ),
      bridge = bridge,
      rawParams = paramsJson,
    )
  }

  private fun handleSwitchChain(callbackId: Int, paramsJson: String, bridge: EthereumBridge) {
    val chainIdStr = extractChainIdFromParams(paramsJson)
    if (chainIdStr == null) {
      bridge.sendError(callbackId, EthereumBridge.ERROR_INVALID_PARAMS, "Invalid chain ID param")
      return
    }

    val chainId = ChainValidator.parseChainId(chainIdStr)
    if (chainId == null) {
      bridge.sendError(callbackId, EthereumBridge.ERROR_INVALID_PARAMS, "Invalid chain ID format")
      return
    }

    if (!ChainValidator.isAllowedChainId(chainId)) {
      bridge.sendError(callbackId, EthereumBridge.ERROR_INVALID_PARAMS, "Unsupported chain")
      return
    }

    _activeChainId.value = chainId
    bridge.sendResult(callbackId, "null")
    bridge.emitEvent("chainChanged", "\"${chainIdToHex(chainId)}\"")
  }

  private fun handleAddChain(callbackId: Int, paramsJson: String, bridge: EthereumBridge) {
    val chainIdStr = extractChainIdFromParams(paramsJson)
    if (chainIdStr == null) {
      bridge.sendError(callbackId, EthereumBridge.ERROR_INVALID_PARAMS, "Invalid chain ID param")
      return
    }

    val chainId = ChainValidator.parseChainId(chainIdStr)
    if (chainId == null) {
      bridge.sendError(callbackId, EthereumBridge.ERROR_INVALID_PARAMS, "Invalid chain ID format")
      return
    }

    if (!ChainValidator.isAllowedChainId(chainId)) {
      bridge.sendError(callbackId, EthereumBridge.ERROR_INVALID_PARAMS, "Unsupported chain")
      return
    }

    // Chain is already configured — just confirm success
    // Per tech-spec: "NEVER allow overriding RPC URL for already-configured chains"
    bridge.sendResult(callbackId, "null")
  }

  /**
   * User approved the pending confirmation.
   */
  fun approveConfirmation() {
    val pending = _pendingConfirmation.value ?: return
    _pendingConfirmation.value = null
    // In a full implementation, this would trigger signing via :core:evm
    // For now, send a placeholder response
    pending.bridge.sendResult(pending.callbackId, "\"0x0000000000000000000000000000000000000000000000000000000000000000\"")
  }

  /**
   * User rejected the pending confirmation.
   */
  fun rejectConfirmation() {
    val pending = _pendingConfirmation.value ?: return
    _pendingConfirmation.value = null
    pending.bridge.sendError(pending.callbackId, EthereumBridge.ERROR_USER_REJECTED, "User rejected the request")
  }

  private fun chainIdToHex(chainId: Long): String {
    return "0x${chainId.toString(16)}"
  }

  private fun extractJsonField(json: String, field: String): String? {
    // Simple JSON field extraction — works for flat objects
    val pattern = "\"$field\"\\s*:\\s*\"([^\"]*)\""
    val match = Regex(pattern).find(json)
    return match?.groupValues?.get(1)
  }

  private fun extractChainIdFromParams(paramsJson: String): String? {
    // Params is typically: [{"chainId": "0x1"}]
    val pattern = "\"chainId\"\\s*:\\s*\"([^\"]*)\""
    val match = Regex(pattern).find(paramsJson)
    return match?.groupValues?.get(1)
  }

  private fun isHumanReadableMessage(message: String): Boolean {
    // Check if the message is UTF-8 decodable text (not raw hex)
    val cleaned = message.trim().removePrefix("[").removePrefix("\"").removeSuffix("]").removeSuffix("\"")
    return !cleaned.startsWith("0x") || cleaned.length < 10
  }
}

/**
 * UI state for the dApp browser screen.
 */
data class DAppBrowserUiState(
  val currentUrl: String = "",
  val isConnected: Boolean = false,
  val isLoading: Boolean = false,
  val domainBlocked: Boolean = false,
  val blockedDomain: String = "",
)

/**
 * A pending confirmation request waiting for user approval.
 */
data class PendingConfirmation(
  val callbackId: Int,
  val method: String,
  val displayInfo: Any, // TransactionDisplayInfo or SigningDisplayInfo
  val bridge: EthereumBridge,
  val rawParams: String,
)

/**
 * Display info for a transaction confirmation dialog.
 */
data class TransactionDisplayInfo(
  val to: String,
  val value: String,
  val decodedFunction: DecodedFunction?,
  val isUnlimitedApproval: Boolean,
  val gasWarning: Boolean,
)

/**
 * Display info for a message signing confirmation dialog.
 */
data class SigningDisplayInfo(
  val message: String,
  val isHumanReadable: Boolean,
)
