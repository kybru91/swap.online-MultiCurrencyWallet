package com.mcw.wallet.ui.settings

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import com.mcw.wallet.BuildConfig
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings screen composable.
 *
 * Provides:
 * 1. Network selector (ETH Mainnet, BSC, Polygon) — requires app restart
 * 2. Custom RPC URL configuration — validates HTTPS-only, blocks private IPs
 * 3. Backup wallet — shows mnemonic with FLAG_SECURE, confirms user wrote it down
 *
 * @param state the current UI state from SettingsViewModel
 * @param onNavigateBack callback to navigate back
 * @param onRpcUrlChanged callback when user types in RPC URL field
 * @param onSaveRpcUrl callback to save validated RPC URL
 * @param onNetworkSelected callback when user selects a network
 * @param onConfirmNetworkSwitch callback to confirm network switch (app restart)
 * @param onDismissRestartDialog callback to cancel network switch
 * @param onShowBackup callback to show mnemonic for backup
 * @param onConfirmBackup callback when user confirms they wrote down mnemonic
 * @param onHideBackup callback to hide mnemonic display
 * @param onDismissError callback to dismiss error message
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  state: SettingsUiState = SettingsUiState(),
  onNavigateBack: () -> Unit = {},
  onRpcUrlChanged: (String) -> Unit = {},
  onSaveRpcUrl: () -> Unit = {},
  onNetworkSelected: (SupportedNetwork) -> Unit = {},
  onConfirmNetworkSwitch: () -> Unit = {},
  onDismissRestartDialog: () -> Unit = {},
  onShowBackup: () -> Unit = {},
  onConfirmBackup: () -> Unit = {},
  onHideBackup: () -> Unit = {},
  onDismissError: () -> Unit = {},
) {
  val context = LocalContext.current

  // Set FLAG_SECURE when mnemonic is shown to prevent screenshots
  if (state.showMnemonic) {
    DisposableEffect(Unit) {
      val window = (context as? Activity)?.window
      window?.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE,
      )
      onDispose {
        window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
    ) {
      // --- Network Selector Section ---
      SectionHeader("Network")
      Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          SupportedNetwork.entries.forEach { network ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onNetworkSelected(network) }
                .padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(
                selected = network == state.selectedNetwork,
                onClick = { onNetworkSelected(network) },
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = network.displayName,
                  style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                  text = "Chain ID: ${network.chainId} (${network.currencySymbol})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // --- Custom RPC URL Section ---
      SectionHeader("Custom RPC URL")
      Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "Override the default RPC endpoint for ${state.selectedNetwork.displayName}. " +
              "Only HTTPS URLs are accepted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = state.customRpcUrl,
            onValueChange = onRpcUrlChanged,
            label = { Text("RPC URL") },
            placeholder = { Text("https://mainnet.infura.io/v3/...") },
            isError = state.rpcValidationError != null,
            supportingText = {
              when {
                state.rpcValidationError != null -> Text(
                  text = state.rpcValidationError,
                  color = MaterialTheme.colorScheme.error,
                )
                state.rpcSaved -> Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Saved", color = MaterialTheme.colorScheme.primary)
                }
              }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Button(
            onClick = onSaveRpcUrl,
            enabled = state.customRpcUrl.isNotBlank() && state.rpcValidationError == null,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Save RPC URL")
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // --- Backup Wallet Section ---
      SectionHeader("Backup Wallet")
      Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          if (!state.showMnemonic) {
            Text(
              text = "View your seed phrase to back up your wallet. " +
                "Keep your seed phrase safe and never share it.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Anyone with your seed phrase can access your funds",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
              )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
              onClick = onShowBackup,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Show Seed Phrase")
            }
          } else {
            // Mnemonic is visible — FLAG_SECURE is active
            Text(
              text = "Write down these words in order and store them safely:",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Display mnemonic words in a grid
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              state.mnemonicWords.forEachIndexed { index, word ->
                Box(
                  modifier = Modifier
                    .border(
                      1.dp,
                      MaterialTheme.colorScheme.outline,
                      RoundedCornerShape(8.dp),
                    )
                    .background(
                      MaterialTheme.colorScheme.surfaceVariant,
                      RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                  Text(
                    text = "${index + 1}. $word",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                  )
                }
              }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (!state.backupConfirmed) {
              Button(
                onClick = onConfirmBackup,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("I wrote it down")
              }
            } else {
              Text(
                text = "Backup confirmed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
              onClick = onHideBackup,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Hide Seed Phrase")
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // --- App Info Section ---
      SectionHeader("About")
      Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          InfoRow("App Name", BuildConfig.APP_NAME)
          InfoRow("Version", BuildConfig.VERSION_NAME)
          InfoRow("Active Network", state.selectedNetwork.displayName)
          InfoRow("Chain ID", state.selectedNetwork.chainId.toString())
        }
      }

      Spacer(modifier = Modifier.height(32.dp))
    }
  }

  // --- Restart Confirmation Dialog ---
  if (state.showRestartDialog && state.pendingNetwork != null) {
    AlertDialog(
      onDismissRequest = onDismissRestartDialog,
      title = { Text("Switch Network") },
      text = {
        Text(
          "Switching to ${state.pendingNetwork.displayName} requires an app restart. " +
            "Any unsaved data will be lost."
        )
      },
      confirmButton = {
        Button(onClick = onConfirmNetworkSwitch) {
          Text("Restart")
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissRestartDialog) {
          Text("Cancel")
        }
      },
    )
  }

  // --- Error Dialog ---
  if (state.errorMessage != null) {
    AlertDialog(
      onDismissRequest = onDismissError,
      title = { Text("Error") },
      text = { Text(state.errorMessage) },
      confirmButton = {
        TextButton(onClick = onDismissError) {
          Text("OK")
        }
      },
    )
  }
}

@Composable
private fun SectionHeader(title: String) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(bottom = 8.dp),
  )
}

@Composable
private fun InfoRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}
