package com.mcw.wallet.ui.stub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stub screens for navigation destinations that will be implemented in later tasks.
 * These provide placeholder UI so the navigation graph is complete.
 *
 * - SendScreen: Task 9
 * - HistoryScreen: Task 12
 * - SettingsScreen: Task 13
 * - DAppBrowserScreen: Task 10
 * - DAppsTabScreen: Task 10
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendStubScreen(onNavigateBack: () -> Unit = {}) {
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Send") },
      navigationIcon = {
        IconButton(onClick = onNavigateBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      },
    )
    StubContent("Send Transaction", "Will be implemented in Task 9")
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryStubScreen(onNavigateBack: () -> Unit = {}) {
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Transaction History") },
      navigationIcon = {
        IconButton(onClick = onNavigateBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      },
    )
    StubContent("Transaction History", "Will be implemented in Task 12")
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsStubScreen(onNavigateBack: () -> Unit = {}) {
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Settings") },
      navigationIcon = {
        IconButton(onClick = onNavigateBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      },
    )
    StubContent("Settings", "Will be implemented in Task 13")
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DAppBrowserStubScreen(
  url: String = "",
  onNavigateBack: () -> Unit = {},
) {
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("dApp Browser") },
      navigationIcon = {
        IconButton(onClick = onNavigateBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      },
    )
    StubContent("dApp Browser", "URL: $url\nWill be implemented in Task 10")
  }
}

@Suppress("UNUSED_PARAMETER") // Parameter kept for navigation graph; will be used in Task 10
@Composable
fun DAppsTabScreen(onNavigateToBrowser: (String) -> Unit = {}) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      Icons.Filled.Apps,
      contentDescription = "dApps",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 16.dp),
    )
    Text(
      text = "dApps Browser",
      style = MaterialTheme.typography.headlineSmall,
    )
    Text(
      text = "Browse decentralized applications.\nWill be implemented in Task 10.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}

/**
 * Placeholder for the onboarding flow in the navigation graph.
 * Will be replaced with real OnboardingScreen once ViewModels are wired via DI.
 * The OnboardingScreen composable and OnboardingViewModel are fully implemented
 * and tested; this placeholder exists only because the NavHost cannot yet
 * provide the ViewModel dependencies without assisted injection setup.
 */
@Composable
fun OnboardingPlaceholder(onNavigateToWallet: () -> Unit = {}) {
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
    Text(
      text = "Secure multi-currency wallet",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp, bottom = 48.dp),
    )
    Button(
      onClick = onNavigateToWallet,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Create Wallet")
    }
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedButton(
      onClick = onNavigateToWallet,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Import Wallet")
    }
  }
}

@Composable
private fun StubContent(title: String, description: String) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.headlineSmall,
    )
    Text(
      text = description,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}
