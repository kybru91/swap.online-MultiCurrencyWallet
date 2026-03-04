package com.mcw.wallet.ui.onboarding

import com.mcw.core.crypto.CryptoManager
import com.mcw.core.crypto.MnemonicException
import com.mcw.core.crypto.WalletKeys
import com.mcw.core.storage.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for OnboardingViewModel — seed confirmation retry logic,
 * password validation, and wallet creation/import flows.
 *
 * TDD anchors from task 8:
 * - testSeedConfirmationRetry — wrong words -> retry (up to 3 attempts)
 * - testSeedConfirmationReset — 3 failures -> show all 12 words again, reset counter
 * - testPasswordValidation — 8+ chars -> valid, 7 chars -> error
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var cryptoManager: CryptoManager
  private lateinit var secureStorage: SecureStorage
  private lateinit var viewModel: OnboardingViewModel

  private val testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
  private val testWords = testMnemonic.split(" ")
  private val testWalletKeys = WalletKeys(
    mnemonic = testWords,
    btcPrivateKeyWIF = "L1RzGGsYw7iFocm9v4gLPihmTJagbBwRaApK1KnDCqeFE7GEDhsz",
    btcAddress = "1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA",
    ethPrivateKeyHex = "0x1ab42cc412b618bdea3a599e3c9bae199ebf030895b039e9db1e30dafb12b727",
    ethAddress = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94",
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    cryptoManager = mock()
    secureStorage = mock()
    whenever(cryptoManager.generateMnemonic()).thenReturn(testMnemonic)
    whenever(cryptoManager.deriveKeys(any())).thenReturn(testWalletKeys)

    viewModel = OnboardingViewModel(cryptoManager, secureStorage)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // --- Seed Confirmation Retry Tests ---

  @Test
  fun `testSeedConfirmationRetry - wrong words increment attempt counter`() = runTest {
    viewModel.generateMnemonic()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue("Should be in mnemonic display state", state is OnboardingUiState.MnemonicDisplay)

    // Move to confirmation
    viewModel.onMnemonicConfirmed()
    advanceUntilIdle()

    val confirmState = viewModel.uiState.value
    assertTrue("Should be in seed confirmation state", confirmState is OnboardingUiState.SeedConfirmation)
    val seedConfirm = confirmState as OnboardingUiState.SeedConfirmation
    assertEquals("Initial attempt should be 0", 0, seedConfirm.attemptCount)

    // Submit wrong words
    val wrongAnswers = seedConfirm.challengeIndices.associate { it to "wrong" }
    viewModel.submitSeedConfirmation(wrongAnswers)
    advanceUntilIdle()

    val afterFirst = viewModel.uiState.value
    assertTrue("Should still be in seed confirmation", afterFirst is OnboardingUiState.SeedConfirmation)
    val afterFirstConfirm = afterFirst as OnboardingUiState.SeedConfirmation
    assertEquals("Attempt should be 1 after first failure", 1, afterFirstConfirm.attemptCount)
    assertNotNull("Should have error message", afterFirstConfirm.error)
  }

  @Test
  fun `testSeedConfirmationRetry - correct words proceed to password`() = runTest {
    viewModel.generateMnemonic()
    advanceUntilIdle()
    viewModel.onMnemonicConfirmed()
    advanceUntilIdle()

    val confirmState = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
    // Submit correct words
    val correctAnswers = confirmState.challengeIndices.associate { idx -> idx to testWords[idx] }
    viewModel.submitSeedConfirmation(correctAnswers)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue("Should move to password screen", state is OnboardingUiState.SetPassword)
  }

  @Test
  fun `testSeedConfirmationRetry - second wrong attempt increments to 2`() = runTest {
    viewModel.generateMnemonic()
    advanceUntilIdle()
    viewModel.onMnemonicConfirmed()
    advanceUntilIdle()

    val confirmState = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
    val wrongAnswers = confirmState.challengeIndices.associate { it to "wrong" }

    // First failed attempt
    viewModel.submitSeedConfirmation(wrongAnswers)
    advanceUntilIdle()

    // Second failed attempt
    val state2 = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
    val wrongAnswers2 = state2.challengeIndices.associate { it to "invalid" }
    viewModel.submitSeedConfirmation(wrongAnswers2)
    advanceUntilIdle()

    val afterSecond = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
    assertEquals("Attempt should be 2 after second failure", 2, afterSecond.attemptCount)
  }

  // --- Seed Confirmation Reset Tests ---

  @Test
  fun `testSeedConfirmationReset - 3 failures reset to mnemonic display`() = runTest {
    viewModel.generateMnemonic()
    advanceUntilIdle()
    viewModel.onMnemonicConfirmed()
    advanceUntilIdle()

    // Fail 3 times
    for (i in 0 until 3) {
      val state = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
      val wrongAnswers = state.challengeIndices.associate { it to "wrong" }
      viewModel.submitSeedConfirmation(wrongAnswers)
      advanceUntilIdle()
    }

    val resetState = viewModel.uiState.value
    assertTrue(
      "Should reset to mnemonic display after 3 failures",
      resetState is OnboardingUiState.MnemonicDisplay
    )
    // Mnemonic should be the same (not re-generated)
    val display = resetState as OnboardingUiState.MnemonicDisplay
    assertEquals("Mnemonic should be preserved after reset", testWords, display.words)
  }

  @Test
  fun `testSeedConfirmationReset - after reset, attempt counter resets to 0`() = runTest {
    viewModel.generateMnemonic()
    advanceUntilIdle()
    viewModel.onMnemonicConfirmed()
    advanceUntilIdle()

    // Fail 3 times to trigger reset
    for (i in 0 until 3) {
      val state = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
      val wrongAnswers = state.challengeIndices.associate { it to "wrong" }
      viewModel.submitSeedConfirmation(wrongAnswers)
      advanceUntilIdle()
    }

    // Should be back at mnemonic display
    assertTrue(viewModel.uiState.value is OnboardingUiState.MnemonicDisplay)

    // Move to confirmation again
    viewModel.onMnemonicConfirmed()
    advanceUntilIdle()

    val newConfirm = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
    assertEquals("Attempt counter should be 0 after reset", 0, newConfirm.attemptCount)
  }

  // --- Password Validation Tests ---

  @Test
  fun `testPasswordValidation - 8 chars is valid`() = runTest {
    viewModel.generateMnemonic()
    advanceUntilIdle()
    viewModel.onMnemonicConfirmed()
    advanceUntilIdle()

    // Pass seed confirmation
    val confirmState = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
    val correctAnswers = confirmState.challengeIndices.associate { idx -> idx to testWords[idx] }
    viewModel.submitSeedConfirmation(correctAnswers)
    advanceUntilIdle()

    // Set valid password
    viewModel.setPassword("abcd1234", "abcd1234")
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue("Should complete wallet creation with valid password", state is OnboardingUiState.Complete)
  }

  @Test
  fun `testPasswordValidation - 7 chars shows error`() = runTest {
    viewModel.generateMnemonic()
    advanceUntilIdle()
    viewModel.onMnemonicConfirmed()
    advanceUntilIdle()

    // Pass seed confirmation
    val confirmState = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
    val correctAnswers = confirmState.challengeIndices.associate { idx -> idx to testWords[idx] }
    viewModel.submitSeedConfirmation(correctAnswers)
    advanceUntilIdle()

    // Try short password
    viewModel.setPassword("abc1234", "abc1234")
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue("Should stay on password screen", state is OnboardingUiState.SetPassword)
    val pwState = state as OnboardingUiState.SetPassword
    assertNotNull("Should have error message", pwState.error)
    assertTrue(
      "Error should mention minimum length",
      pwState.error!!.contains("8")
    )
  }

  @Test
  fun `testPasswordValidation - mismatched passwords shows error`() = runTest {
    viewModel.generateMnemonic()
    advanceUntilIdle()
    viewModel.onMnemonicConfirmed()
    advanceUntilIdle()

    // Pass seed confirmation
    val confirmState = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
    val correctAnswers = confirmState.challengeIndices.associate { idx -> idx to testWords[idx] }
    viewModel.submitSeedConfirmation(correctAnswers)
    advanceUntilIdle()

    // Mismatched passwords
    viewModel.setPassword("abcd1234", "abcd5678")
    advanceUntilIdle()

    val state = viewModel.uiState.value as OnboardingUiState.SetPassword
    assertNotNull("Should have error for mismatch", state.error)
    assertTrue(
      "Error should mention passwords not matching",
      state.error!!.contains("match", ignoreCase = true)
    )
  }

  // --- Import Wallet Tests ---

  @Test
  fun `import valid mnemonic proceeds to password screen`() = runTest {
    whenever(cryptoManager.validateMnemonic(testMnemonic)).thenReturn(true)

    viewModel.importMnemonic(testMnemonic)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue("Should move to password screen after valid import", state is OnboardingUiState.SetPassword)
  }

  @Test
  fun `import invalid mnemonic shows error`() = runTest {
    // Use thenAnswer instead of thenThrow to avoid Mockito's checked exception validation
    whenever(cryptoManager.validateMnemonic("invalid words"))
      .thenAnswer { throw MnemonicException("Invalid word count: expected 12, got 2") }

    viewModel.importMnemonic("invalid words")
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue("Should be in import state with error", state is OnboardingUiState.ImportWallet)
    val importState = state as OnboardingUiState.ImportWallet
    assertNotNull("Should have error", importState.error)
  }

  // --- Wallet Creation Saves Keys ---

  @Test
  fun `wallet creation saves mnemonic and keys to storage`() = runTest {
    viewModel.generateMnemonic()
    advanceUntilIdle()
    viewModel.onMnemonicConfirmed()
    advanceUntilIdle()

    val confirmState = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
    val correctAnswers = confirmState.challengeIndices.associate { idx -> idx to testWords[idx] }
    viewModel.submitSeedConfirmation(correctAnswers)
    advanceUntilIdle()

    viewModel.setPassword("abcd1234", "abcd1234")
    advanceUntilIdle()

    verify(secureStorage).saveMnemonic(testWords)
    verify(secureStorage).savePrivateKeys(testWalletKeys.btcPrivateKeyWIF, testWalletKeys.ethPrivateKeyHex)

    // Verify bcrypt hash format: $2a$12$ prefix (cost factor 12)
    val hashCaptor = argumentCaptor<String>()
    verify(secureStorage).savePasswordHash(hashCaptor.capture())
    assertTrue(
      "Password hash should start with \$2a\$12\$ (bcrypt cost 12)",
      hashCaptor.firstValue.startsWith("\$2a\$12\$")
    )
  }

  // --- Challenge Indices ---

  @Test
  fun `seed confirmation generates exactly 3 challenge indices`() = runTest {
    viewModel.generateMnemonic()
    advanceUntilIdle()
    viewModel.onMnemonicConfirmed()
    advanceUntilIdle()

    val state = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
    assertEquals("Should have exactly 3 challenge indices", 3, state.challengeIndices.size)
    assertTrue(
      "All indices should be in 0..11",
      state.challengeIndices.all { it in 0..11 }
    )
    assertEquals(
      "All indices should be unique",
      state.challengeIndices.size,
      state.challengeIndices.toSet().size
    )
  }

  // --- Trimming whitespace in seed confirmation ---

  @Test
  fun `seed confirmation trims whitespace from answers`() = runTest {
    viewModel.generateMnemonic()
    advanceUntilIdle()
    viewModel.onMnemonicConfirmed()
    advanceUntilIdle()

    val confirmState = viewModel.uiState.value as OnboardingUiState.SeedConfirmation
    // Submit correct words with extra whitespace
    val correctAnswersWithSpaces = confirmState.challengeIndices.associate { idx ->
      idx to "  ${testWords[idx]}  "
    }
    viewModel.submitSeedConfirmation(correctAnswersWithSpaces)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue("Should accept trimmed answers", state is OnboardingUiState.SetPassword)
  }
}
