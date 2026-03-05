package com.mcw.wallet.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mcw.core.storage.SecureStorage
import com.mcw.wallet.MCWalletApplication
import com.mcw.wallet.ui.history.HistoryScreen
import com.mcw.wallet.ui.stub.DAppBrowserStubScreen
import com.mcw.wallet.ui.stub.DAppsTabScreen
import com.mcw.wallet.ui.send.SendScreen
import com.mcw.wallet.ui.settings.SettingsScreen
import com.mcw.wallet.ui.settings.SettingsViewModel
import com.mcw.wallet.ui.wallet.WalletScreen

/**
 * Bottom navigation tab definition.
 *
 * @param route the navigation route string
 * @param label the display label
 * @param icon the Material icon
 */
data class BottomNavTab(
  val route: String,
  val label: String,
  val icon: ImageVector,
)

/** Bottom navigation tabs: Wallet and dApps */
val bottomNavTabs = listOf(
  BottomNavTab(NavRoutes.WALLET, "Wallet", Icons.Filled.AccountBalanceWallet),
  BottomNavTab(NavRoutes.DAPPS, "dApps", Icons.Filled.Apps),
)

/**
 * Root composable for the wallet app.
 *
 * Implements single-activity Compose architecture (tech-spec Architecture overview):
 * - NavHost with onboarding and main flows
 * - Bottom navigation bar (Wallet / dApps tabs)
 * - Onboarding screens: welcome, create, confirm, import, password
 * - Main screens: wallet, dApps, send, history, settings
 *
 * ViewModels are not Hilt-injected in this task; they will be wired up
 * with proper DI (assisted injection or factory) in the integration phase.
 * For now, composable screens use state hoisting or placeholder content.
 *
 * @param hasWallet whether a wallet already exists in secure storage
 */
@Composable
fun WalletApp(hasWallet: Boolean = false) {
  val navController = rememberNavController()
  val startDestination = if (hasWallet) NavRoutes.WALLET else NavRoutes.ONBOARDING

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route

  // Show bottom nav only on main tab screens
  val showBottomBar = currentRoute in listOf(NavRoutes.WALLET, NavRoutes.DAPPS)

  MaterialTheme {
    Scaffold(
      bottomBar = {
        if (showBottomBar) {
          NavigationBar {
            bottomNavTabs.forEach { tab ->
              val selected = navBackStackEntry?.destination?.hierarchy?.any {
                it.route == tab.route
              } == true

              NavigationBarItem(
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                selected = selected,
                onClick = {
                  navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                      saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
              )
            }
          }
        }
      }
    ) { innerPadding ->
      NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.padding(innerPadding),
      ) {
        // Onboarding flow — uses a placeholder until ViewModels are wired via DI
        composable(NavRoutes.ONBOARDING) {
          // OnboardingScreen will be wired with ViewModel via assisted injection
          // For now, the navigation graph is complete for the architecture
          com.mcw.wallet.ui.stub.OnboardingPlaceholder(
            onNavigateToWallet = {
              navController.navigate(NavRoutes.WALLET) {
                popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
              }
            }
          )
        }

        // Main wallet tab
        composable(NavRoutes.WALLET) {
          WalletScreen(
            onNavigateToSend = { navController.navigate(NavRoutes.SEND) },
            onNavigateToHistory = { navController.navigate(NavRoutes.HISTORY) },
            onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
          )
        }

        // dApps tab
        composable(NavRoutes.DAPPS) {
          DAppsTabScreen(
            onNavigateToBrowser = { url ->
              navController.navigate("${NavRoutes.DAPP_BROWSER}/$url")
            }
          )
        }

        // Send screen — real composable, ViewModel wired in integration phase
        composable(NavRoutes.SEND) {
          SendScreen(
            onNavigateBack = { navController.popBackStack() }
          )
        }

        // History screen — real composable, ViewModel wired in integration phase
        composable(NavRoutes.HISTORY) {
          HistoryScreen(
            onNavigateBack = { navController.popBackStack() }
          )
        }

        // Settings screen — wired with SettingsViewModel
        composable(NavRoutes.SETTINGS) {
          val context = LocalContext.current
          val secureStorage = remember { SecureStorage(context) }
          val settingsViewModel = remember {
            SettingsViewModel(
              secureStorage = secureStorage,
              debugLogTree = MCWalletApplication.debugLogTree,
              appContext = context.applicationContext,
            )
          }
          val settingsState by settingsViewModel.uiState.collectAsState()
          SettingsScreen(
            state = settingsState,
            onNavigateBack = { navController.popBackStack() },
            onRpcUrlChanged = settingsViewModel::onRpcUrlChanged,
            onSaveRpcUrl = settingsViewModel::saveCustomRpcUrl,
            onNetworkSelected = settingsViewModel::onNetworkSelected,
            onConfirmNetworkSwitch = settingsViewModel::confirmNetworkSwitch,
            onDismissRestartDialog = settingsViewModel::dismissRestartDialog,
            onShowBackup = settingsViewModel::showBackupMnemonic,
            onConfirmBackup = settingsViewModel::confirmBackup,
            onHideBackup = settingsViewModel::hideBackupMnemonic,
            onDismissError = settingsViewModel::dismissError,
            onSendDebugReport = settingsViewModel::sendDebugReport,
            onDismissDebugUrl = settingsViewModel::dismissDebugUrl,
          )
        }

        // dApp Browser screen (stub)
        composable("${NavRoutes.DAPP_BROWSER}/{url}") { backStackEntry ->
          val url = backStackEntry.arguments?.getString("url") ?: ""
          DAppBrowserStubScreen(
            url = url,
            onNavigateBack = { navController.popBackStack() }
          )
        }
      }
    }
  }
}
