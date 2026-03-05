package com.mcw.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mcw.core.storage.SecureStorage
import com.mcw.wallet.ui.navigation.WalletApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity entry point for the wallet app.
 *
 * Uses Jetpack Compose with Hilt dependency injection.
 * Checks SecureStorage for existing wallet to determine
 * start destination (onboarding vs main wallet).
 *
 * Tech-spec Architecture overview:
 * "Single-activity Compose app with bottom navigation (Wallet / dApps tabs)"
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject
  lateinit var secureStorage: SecureStorage

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val hasWallet = secureStorage.hasWallet()
    setContent {
      WalletApp(hasWallet = hasWallet)
    }
  }
}
