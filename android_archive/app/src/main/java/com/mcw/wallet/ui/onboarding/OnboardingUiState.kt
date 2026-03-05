package com.mcw.wallet.ui.onboarding

/**
 * Sealed hierarchy representing all states of the onboarding flow.
 *
 * Flow: Welcome -> MnemonicDisplay -> SeedConfirmation -> SetPassword -> Complete
 *       Welcome -> ImportWallet -> SetPassword -> Complete
 *
 * SeedConfirmation resets to MnemonicDisplay after 3 failed attempts
 * (per tech-spec Architecture section: "retry counter is in-memory StateFlow<Int>").
 */
sealed interface OnboardingUiState {

  /** Initial welcome screen with Create/Import options */
  data object Welcome : OnboardingUiState

  /**
   * Displaying the generated 12-word mnemonic.
   * User must acknowledge they wrote it down before proceeding.
   *
   * @param words the 12 BIP39 mnemonic words
   * @param wasReset true if we returned here after 3 failed confirmation attempts
   */
  data class MnemonicDisplay(
    val words: List<String>,
    val wasReset: Boolean = false,
  ) : OnboardingUiState

  /**
   * Seed confirmation: user must enter 3 randomly selected words.
   *
   * @param challengeIndices the 3 word positions (0-indexed) to verify
   * @param attemptCount how many failed attempts (0-2; at 3 we reset)
   * @param error error message from the last failed attempt, or null
   */
  data class SeedConfirmation(
    val challengeIndices: List<Int>,
    val attemptCount: Int = 0,
    val error: String? = null,
  ) : OnboardingUiState

  /**
   * Import wallet: user enters a 12-word mnemonic.
   *
   * @param error validation error, or null
   */
  data class ImportWallet(
    val error: String? = null,
  ) : OnboardingUiState

  /**
   * Set app password (minimum 8 characters).
   *
   * @param error validation error, or null
   */
  data class SetPassword(
    val error: String? = null,
  ) : OnboardingUiState

  /**
   * Wallet creation/import completed successfully.
   * Navigate to the main wallet screen.
   */
  data object Complete : OnboardingUiState
}
