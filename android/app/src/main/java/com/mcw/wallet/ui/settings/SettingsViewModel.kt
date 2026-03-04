package com.mcw.wallet.ui.settings

import com.mcw.core.network.RpcUrlValidator
import com.mcw.core.storage.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Settings screen.
 *
 * Manages:
 * - Custom RPC URL configuration with HTTPS/private-IP validation
 * - Network selector (ETH/BSC/Polygon) with restart notification
 * - Backup wallet flow (show mnemonic, confirm user wrote it down)
 *
 * Not @HiltViewModel — same pattern as other ViewModels in the project
 * (CryptoManager lacks @Inject constructor). Will be wired via factory
 * in the integration phase.
 */
class SettingsViewModel(
  private val secureStorage: SecureStorage,
) {

  private val _uiState = MutableStateFlow(
    SettingsUiState(
      selectedNetwork = SupportedNetwork.fromChainId(secureStorage.getActiveChainId())
        ?: SupportedNetwork.ETH_MAINNET
    )
  )
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  /**
   * Update the custom RPC URL input and validate it.
   */
  fun onRpcUrlChanged(url: String) {
    if (url.isBlank()) {
      _uiState.value = _uiState.value.copy(
        customRpcUrl = url,
        rpcValidationError = null,
        rpcSaved = false,
      )
      return
    }

    val result = RpcUrlValidator.validate(url)
    _uiState.value = _uiState.value.copy(
      customRpcUrl = url,
      rpcValidationError = if (result.isValid) null else result.errorMessage,
      rpcSaved = false,
    )
  }

  /**
   * Save the custom RPC URL if valid.
   */
  fun saveCustomRpcUrl() {
    val url = _uiState.value.customRpcUrl
    if (url.isBlank()) return

    val result = RpcUrlValidator.validate(url)
    if (!result.isValid) {
      _uiState.value = _uiState.value.copy(rpcValidationError = result.errorMessage)
      return
    }

    // Save to secure storage as custom_rpc_config
    // Format: simple JSON with chain -> URL mapping
    secureStorage.saveCustomRpcConfig(
      _uiState.value.selectedNetwork.chainId,
      url,
    )
    _uiState.value = _uiState.value.copy(rpcSaved = true)
  }

  /**
   * Select a new network. Shows restart dialog if network actually changes.
   */
  fun onNetworkSelected(network: SupportedNetwork) {
    if (network == _uiState.value.selectedNetwork) return
    _uiState.value = _uiState.value.copy(
      showRestartDialog = true,
      pendingNetwork = network,
    )
  }

  /**
   * Confirm the network switch: persist new chain ID.
   * Caller should restart the app after this.
   */
  fun confirmNetworkSwitch() {
    val pending = _uiState.value.pendingNetwork ?: return
    secureStorage.saveActiveChainId(pending.chainId)
    _uiState.value = _uiState.value.copy(
      selectedNetwork = pending,
      showRestartDialog = false,
      pendingNetwork = null,
      customRpcUrl = "",
      rpcValidationError = null,
      rpcSaved = false,
    )
  }

  /**
   * Cancel the network switch.
   */
  fun dismissRestartDialog() {
    _uiState.value = _uiState.value.copy(
      showRestartDialog = false,
      pendingNetwork = null,
    )
  }

  /**
   * Show the mnemonic for backup. Reads from encrypted storage.
   * The screen should set FLAG_SECURE when mnemonic is visible.
   */
  fun showBackupMnemonic() {
    val words = secureStorage.getMnemonic()
    if (words == null) {
      _uiState.value = _uiState.value.copy(
        errorMessage = "No wallet found. Please create or import a wallet first."
      )
      return
    }
    _uiState.value = _uiState.value.copy(
      showMnemonic = true,
      mnemonicWords = words,
      backupConfirmed = false,
    )
  }

  /**
   * User confirms they wrote down the mnemonic.
   */
  fun confirmBackup() {
    _uiState.value = _uiState.value.copy(
      backupConfirmed = true,
    )
  }

  /**
   * Hide the mnemonic display.
   */
  fun hideBackupMnemonic() {
    _uiState.value = _uiState.value.copy(
      showMnemonic = false,
      mnemonicWords = emptyList(),
      backupConfirmed = false,
    )
  }

  /**
   * Dismiss error message.
   */
  fun dismissError() {
    _uiState.value = _uiState.value.copy(errorMessage = null)
  }
}
