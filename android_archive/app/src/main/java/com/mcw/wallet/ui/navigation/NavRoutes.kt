package com.mcw.wallet.ui.navigation

/**
 * Navigation route constants for the wallet app.
 *
 * Single-activity architecture: all screens are Compose destinations
 * within a single NavHost. Bottom navigation switches between
 * Wallet and dApps tabs at the top level.
 */
object NavRoutes {
  // Onboarding flow
  const val ONBOARDING = "onboarding"
  // TODO: These routes will be used when onboarding is split into separate
  // NavHost destinations (currently onboarding is a single composable with internal state)
  const val CREATE_WALLET = "create_wallet"
  const val SEED_CONFIRMATION = "seed_confirmation"
  const val IMPORT_WALLET = "import_wallet"
  const val SET_PASSWORD = "set_password"

  // Main screens (bottom navigation tabs)
  const val WALLET = "wallet"
  const val DAPPS = "dapps"

  // Secondary screens
  const val SEND = "send"
  const val HISTORY = "history"
  const val SETTINGS = "settings"
  const val DAPP_BROWSER = "dapp_browser"
}
