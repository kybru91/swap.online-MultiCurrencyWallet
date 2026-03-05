package com.mcw.wallet.ui.settings

/**
 * UI state for the Settings screen.
 *
 * Holds all state needed to render settings options:
 * - Network selector (ETH/BSC/Polygon)
 * - Custom RPC URL configuration with validation
 * - Backup wallet flow (mnemonic display)
 */
data class SettingsUiState(
  /** Currently selected EVM network */
  val selectedNetwork: SupportedNetwork = SupportedNetwork.ETH_MAINNET,

  /** Custom RPC URL input by user */
  val customRpcUrl: String = "",

  /** Validation error for custom RPC URL, null if valid or empty */
  val rpcValidationError: String? = null,

  /** Whether the custom RPC URL was successfully saved */
  val rpcSaved: Boolean = false,

  /** Whether to show the network change restart dialog */
  val showRestartDialog: Boolean = false,

  /** The network the user wants to switch to (pending restart confirmation) */
  val pendingNetwork: SupportedNetwork? = null,

  /** Whether to show the mnemonic for backup */
  val showMnemonic: Boolean = false,

  /** The mnemonic words for backup display */
  val mnemonicWords: List<String> = emptyList(),

  /** Whether the user has confirmed they wrote down the mnemonic */
  val backupConfirmed: Boolean = false,

  /** General error message */
  val errorMessage: String? = null,

  // Debug report
  val isDebugSending: Boolean = false,
  val debugReportUrl: String? = null,
  val debugError: String? = null,
)
