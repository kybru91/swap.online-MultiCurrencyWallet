package com.mcw.wallet.ui.onboarding

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
/**
 * Root onboarding screen that observes OnboardingViewModel state
 * and renders the appropriate sub-screen.
 *
 * The ViewModel is passed as a parameter for testability.
 * In production, the caller (WalletApp NavHost) provides the ViewModel
 * via ViewModelProvider.Factory with the required dependencies.
 *
 * @param onNavigateToWallet called when wallet creation/import is complete
 * @param viewModel the onboarding ViewModel
 */
@Composable
fun OnboardingScreen(
  onNavigateToWallet: () -> Unit,
  viewModel: OnboardingViewModel,
) {
  val uiState by viewModel.uiState.collectAsState()

  when (val state = uiState) {
    is OnboardingUiState.Welcome -> WelcomeContent(
      onCreateWallet = { viewModel.generateMnemonic() },
      onImportWallet = { viewModel.startImport() },
    )

    is OnboardingUiState.MnemonicDisplay -> MnemonicDisplayContent(
      words = state.words,
      wasReset = state.wasReset,
      onConfirmed = { viewModel.onMnemonicConfirmed() },
      onBack = { viewModel.goBack() },
    )

    is OnboardingUiState.SeedConfirmation -> SeedConfirmationContent(
      challengeIndices = state.challengeIndices,
      attemptCount = state.attemptCount,
      error = state.error,
      onSubmit = { answers -> viewModel.submitSeedConfirmation(answers) },
    )

    is OnboardingUiState.ImportWallet -> ImportWalletContent(
      error = state.error,
      onImport = { mnemonic -> viewModel.importMnemonic(mnemonic) },
      onBack = { viewModel.goBack() },
    )

    is OnboardingUiState.SetPassword -> SetPasswordContent(
      error = state.error,
      onSetPassword = { password, confirm -> viewModel.setPassword(password, confirm) },
    )

    is OnboardingUiState.Complete -> {
      onNavigateToWallet()
    }
  }
}

// --- Welcome Screen ---

@Composable
private fun WelcomeContent(
  onCreateWallet: () -> Unit,
  onImportWallet: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = "MCW Wallet",
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "Secure multi-currency wallet",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(48.dp))
    Button(
      onClick = onCreateWallet,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Create Wallet")
    }
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedButton(
      onClick = onImportWallet,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Import Wallet")
    }
  }
}

// --- Mnemonic Display Screen (FLAG_SECURE applied) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MnemonicDisplayContent(
  words: List<String>,
  wasReset: Boolean,
  onConfirmed: () -> Unit,
  onBack: () -> Unit,
) {
  // FLAG_SECURE: prevent screenshots and screen recording on mnemonic display
  FlagSecureEffect()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
  ) {
    TopAppBar(
      title = { Text("Your Seed Phrase") },
      navigationIcon = {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      },
    )

    if (wasReset) {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
      ) {
        Text(
          text = "Confirmation failed 3 times. Please review your seed phrase carefully and try again.",
          modifier = Modifier.padding(16.dp),
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }

    Card(
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp),
    ) {
      Text(
        text = "Write down these 12 words in order and store them safely. This is the ONLY way to recover your wallet.",
        modifier = Modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }

    LazyVerticalGrid(
      columns = GridCells.Fixed(3),
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      itemsIndexed(words) { index, word ->
        Card(
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "${index + 1}. $word",
            modifier = Modifier
              .padding(12.dp)
              .fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
      onClick = onConfirmed,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("I wrote it down")
    }
  }
}

// --- Seed Confirmation Screen (FLAG_SECURE applied) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeedConfirmationContent(
  challengeIndices: List<Int>,
  attemptCount: Int,
  error: String?,
  onSubmit: (Map<Int, String>) -> Unit,
) {
  // FLAG_SECURE: prevent screenshots on seed confirmation screen
  FlagSecureEffect()

  val answers = remember { mutableStateMapOf<Int, String>() }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(rememberScrollState()),
  ) {
    TopAppBar(
      title = { Text("Verify Seed Phrase") },
    )

    Text(
      text = "Enter the following words from your seed phrase to confirm you wrote them down.",
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(bottom = 16.dp),
    )

    if (error != null) {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
      ) {
        Text(
          text = error,
          modifier = Modifier.padding(16.dp),
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }

    Text(
      text = "Attempt ${attemptCount + 1} of 3",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 16.dp),
    )

    challengeIndices.forEach { index ->
      OutlinedTextField(
        value = answers[index] ?: "",
        onValueChange = { answers[index] = it },
        label = { Text("Word #${index + 1}") },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Button(
      onClick = { onSubmit(answers.toMap()) },
      modifier = Modifier.fillMaxWidth(),
      enabled = challengeIndices.all { answers[it]?.isNotBlank() == true },
    ) {
      Text("Verify")
    }
  }
}

// --- Import Wallet Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportWalletContent(
  error: String?,
  onImport: (String) -> Unit,
  onBack: () -> Unit,
) {
  var mnemonic by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
  ) {
    TopAppBar(
      title = { Text("Import Wallet") },
      navigationIcon = {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      },
    )

    Text(
      text = "Enter your 12-word seed phrase to import an existing wallet.",
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(bottom = 16.dp),
    )

    if (error != null) {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
      ) {
        Text(
          text = error,
          modifier = Modifier.padding(16.dp),
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }

    OutlinedTextField(
      value = mnemonic,
      onValueChange = { mnemonic = it },
      label = { Text("Seed phrase") },
      modifier = Modifier
        .fillMaxWidth()
        .height(120.dp),
      maxLines = 4,
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
      onClick = { onImport(mnemonic) },
      modifier = Modifier.fillMaxWidth(),
      enabled = mnemonic.isNotBlank(),
    ) {
      Text("Import")
    }
  }
}

// --- Set Password Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetPasswordContent(
  error: String?,
  onSetPassword: (String, String) -> Unit,
) {
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(rememberScrollState()),
  ) {
    TopAppBar(
      title = { Text("Set App Password") },
    )

    Text(
      text = "Create a password to protect your wallet. Minimum 8 characters.",
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(bottom = 16.dp),
    )

    if (error != null) {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
      ) {
        Text(
          text = error,
          modifier = Modifier.padding(16.dp),
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }

    OutlinedTextField(
      value = password,
      onValueChange = { password = it },
      label = { Text("Password") },
      singleLine = true,
      visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      trailingIcon = {
        IconButton(onClick = { passwordVisible = !passwordVisible }) {
          Icon(
            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = if (passwordVisible) "Hide password" else "Show password",
          )
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp),
    )

    OutlinedTextField(
      value = confirmPassword,
      onValueChange = { confirmPassword = it },
      label = { Text("Confirm password") },
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp),
    )

    Button(
      onClick = { onSetPassword(password, confirmPassword) },
      modifier = Modifier.fillMaxWidth(),
      enabled = password.isNotEmpty() && confirmPassword.isNotEmpty(),
    ) {
      Text("Create Wallet")
    }
  }
}

// --- FLAG_SECURE Helper ---

/**
 * Composable effect that sets FLAG_SECURE on the window while this composable
 * is active, preventing screenshots and screen recording.
 *
 * FLAG_SECURE is required on mnemonic display and seed confirmation screens
 * (tech-spec Security model, Acceptance Criteria).
 */
@Composable
private fun FlagSecureEffect() {
  val context = LocalContext.current
  DisposableEffect(Unit) {
    val window = (context as? android.app.Activity)?.window
    window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    onDispose {
      window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
  }
}
