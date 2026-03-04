package com.mcw.wallet.ui.onboarding

import androidx.lifecycle.ViewModel
import com.mcw.core.crypto.CryptoManager
import com.mcw.core.crypto.MnemonicException
import com.mcw.core.storage.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel managing the onboarding flow: create wallet, import wallet,
 * seed confirmation with retry logic, and password setup.
 *
 * Seed confirmation retry logic (from tech-spec Architecture section):
 * - User is prompted for 3 random words from the generated mnemonic
 * - On incorrect entry: "Incorrect words, try again" (up to 3 attempts)
 * - After 3 failed attempts: show all 12 words again, reset retry counter
 * - Mnemonic stays the same during reset (no re-generation)
 *
 * Password requirements (tech-spec Security model):
 * - Minimum 8 characters (overrides user-spec 6-char minimum per security review)
 * - Stored as bcrypt hash (cost 12) in EncryptedSharedPreferences
 *
 * Note: This ViewModel does NOT use @HiltViewModel because CryptoManager
 * is a plain class (no @Inject constructor). Instead, it is created via
 * a factory or manually in tests. In production, the OnboardingScreen
 * composable uses hiltViewModel() with a provided factory.
 */
class OnboardingViewModel(
  private val cryptoManager: CryptoManager,
  private val secureStorage: SecureStorage,
) : ViewModel() {

  companion object {
    /** Maximum seed confirmation attempts before reset */
    const val MAX_CONFIRMATION_ATTEMPTS = 3

    /** Number of random words to verify during seed confirmation */
    const val CHALLENGE_WORD_COUNT = 3

    /** Minimum password length (Decision: 8+ chars, overrides user-spec 6) */
    const val MIN_PASSWORD_LENGTH = 8
  }

  private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Welcome)
  val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

  /** The generated or imported mnemonic, kept in-memory for the onboarding flow */
  private var currentMnemonic: String? = null
  private var currentWords: List<String> = emptyList()

  /**
   * Generates a new BIP39 mnemonic and transitions to the display screen.
   */
  fun generateMnemonic() {
    val mnemonic = cryptoManager.generateMnemonic()
    currentMnemonic = mnemonic
    currentWords = mnemonic.split(" ")
    _uiState.value = OnboardingUiState.MnemonicDisplay(words = currentWords)
  }

  /**
   * User acknowledged they wrote down the mnemonic.
   * Generates 3 random challenge indices and transitions to confirmation.
   */
  fun onMnemonicConfirmed() {
    val indices = generateChallengeIndices()
    _uiState.value = OnboardingUiState.SeedConfirmation(
      challengeIndices = indices,
      attemptCount = 0,
      error = null,
    )
  }

  /**
   * Submits the seed confirmation answers.
   *
   * @param answers map of word index to the user's answer
   */
  fun submitSeedConfirmation(answers: Map<Int, String>) {
    val state = _uiState.value
    if (state !is OnboardingUiState.SeedConfirmation) return

    // Validate: trim whitespace and lowercase before comparison
    val allCorrect = state.challengeIndices.all { idx ->
      val userAnswer = answers[idx]?.trim()?.lowercase() ?: ""
      userAnswer == currentWords[idx]
    }

    if (allCorrect) {
      // Correct -- proceed to password setup
      _uiState.value = OnboardingUiState.SetPassword()
    } else {
      val newAttempt = state.attemptCount + 1
      if (newAttempt >= MAX_CONFIRMATION_ATTEMPTS) {
        // Reset: show mnemonic again with same words
        _uiState.value = OnboardingUiState.MnemonicDisplay(
          words = currentWords,
          wasReset = true,
        )
      } else {
        // Retry with same challenge indices
        _uiState.value = state.copy(
          attemptCount = newAttempt,
          error = "Incorrect words, try again",
        )
      }
    }
  }

  /**
   * Starts the import wallet flow.
   */
  fun startImport() {
    _uiState.value = OnboardingUiState.ImportWallet()
  }

  /**
   * Validates and imports a mnemonic entered by the user.
   *
   * @param mnemonic the raw mnemonic string (may have extra whitespace)
   */
  fun importMnemonic(mnemonic: String) {
    try {
      cryptoManager.validateMnemonic(mnemonic)
      currentMnemonic = mnemonic.trim().lowercase().replace("\\s+".toRegex(), " ")
      currentWords = currentMnemonic!!.split(" ")
      _uiState.value = OnboardingUiState.SetPassword()
    } catch (e: MnemonicException) {
      _uiState.value = OnboardingUiState.ImportWallet(error = e.message)
    }
  }

  /**
   * Sets the app password, derives keys, and stores everything.
   *
   * @param password the chosen password
   * @param confirmPassword the confirmation password
   */
  fun setPassword(password: String, confirmPassword: String) {
    // Validate length
    if (password.length < MIN_PASSWORD_LENGTH) {
      _uiState.value = OnboardingUiState.SetPassword(
        error = "Password must be at least $MIN_PASSWORD_LENGTH characters"
      )
      return
    }

    // Validate match
    if (password != confirmPassword) {
      _uiState.value = OnboardingUiState.SetPassword(
        error = "Passwords do not match"
      )
      return
    }

    val mnemonic = currentMnemonic ?: return

    // Derive keys
    val walletKeys = cryptoManager.deriveKeys(mnemonic)

    // Hash password with bcrypt (cost 12)
    val passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt(12))

    // Store everything
    secureStorage.saveMnemonic(walletKeys.mnemonic)
    secureStorage.savePrivateKeys(walletKeys.btcPrivateKeyWIF, walletKeys.ethPrivateKeyHex)
    secureStorage.savePasswordHash(passwordHash)

    // Clear sensitive in-memory data
    currentMnemonic = null
    currentWords = emptyList()

    _uiState.value = OnboardingUiState.Complete
  }

  /**
   * Navigates back to the welcome screen.
   */
  fun goBack() {
    _uiState.value = OnboardingUiState.Welcome
  }

  /**
   * Generates 3 unique random indices from 0..11 for seed confirmation.
   */
  private fun generateChallengeIndices(): List<Int> {
    return (0..11).shuffled().take(CHALLENGE_WORD_COUNT).sorted()
  }
}
