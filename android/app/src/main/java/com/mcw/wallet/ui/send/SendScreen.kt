package com.mcw.wallet.ui.send

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Send Transaction screen composable.
 *
 * Displays address input, amount input, fee tier selector, and handles
 * the full send flow: validation -> fee estimation -> confirmation -> broadcast -> result.
 *
 * Uses state hoisting pattern: receives UI state and callbacks from the parent/ViewModel.
 *
 * @param uiState the current send UI state
 * @param currency the currency being sent (BTC, ETH, BNB, MATIC)
 * @param balance the sender's current balance
 * @param onAddressChanged callback when address input changes
 * @param onAmountChanged callback when amount input changes
 * @param onPrepareSend callback to initiate fee estimation
 * @param onSelectFeeTier callback when user selects a fee tier
 * @param onConfirmSend callback to confirm and broadcast (after biometric)
 * @param onResetState callback to reset to idle state
 * @param onNavigateBack callback for back navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
  uiState: SendUiState = SendUiState(),
  currency: String = "BTC",
  balance: String = "0.00",
  onAddressChanged: (String) -> Unit = {},
  onAmountChanged: (String) -> Unit = {},
  onPrepareSend: () -> Unit = {},
  onSelectFeeTier: (Int) -> Unit = {},
  onConfirmSend: () -> Unit = {},
  onResetState: () -> Unit = {},
  onNavigateBack: () -> Unit = {},
) {
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Send $currency") },
      navigationIcon = {
        IconButton(onClick = onNavigateBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      },
    )

    Column(
      modifier = Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    ) {
      when (uiState.state) {
        SendState.Success -> SuccessContent(
          txHash = uiState.txHash ?: "",
          currency = currency,
          onDone = onNavigateBack,
        )
        else -> {
          // Balance display
          Text(
            text = "Available: $balance $currency",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(16.dp))

          // Address input
          AddressInput(
            address = uiState.recipientAddress,
            onAddressChanged = onAddressChanged,
            validation = uiState.addressValidation,
            currency = currency,
            enabled = uiState.state == SendState.Idle || uiState.state == SendState.Error,
          )
          Spacer(modifier = Modifier.height(16.dp))

          // Amount input
          AmountInput(
            amount = uiState.amount,
            onAmountChanged = onAmountChanged,
            validation = uiState.amountValidation,
            currency = currency,
            enabled = uiState.state == SendState.Idle || uiState.state == SendState.Error,
          )
          Spacer(modifier = Modifier.height(24.dp))

          // Fee selector (shown when fee options available)
          if (uiState.feeOptions != null && uiState.state in listOf(
              SendState.Confirming, SendState.Submitting
            )
          ) {
            FeeTierSelector(
              feeOptions = uiState.feeOptions,
              selectedIndex = uiState.selectedFeeTierIndex,
              onSelectTier = onSelectFeeTier,
              enabled = uiState.state == SendState.Confirming,
            )
            Spacer(modifier = Modifier.height(24.dp))
          }

          // Error display
          if (uiState.error != null) {
            ErrorBanner(message = uiState.error)
            Spacer(modifier = Modifier.height(16.dp))
          }

          // Loading indicator
          if (uiState.state == SendState.Building) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(modifier = Modifier.padding(16.dp))
              Text(
                text = "Estimating fees...",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
            Spacer(modifier = Modifier.height(16.dp))
          }

          // Submitting indicator
          if (uiState.state == SendState.Submitting) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(modifier = Modifier.padding(16.dp))
              Text(
                text = "Broadcasting transaction...",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
            Spacer(modifier = Modifier.height(16.dp))
          }

          // Action buttons
          when (uiState.state) {
            SendState.Idle, SendState.Error -> {
              Button(
                onClick = {
                  if (uiState.state == SendState.Error) onResetState()
                  onPrepareSend()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.recipientAddress.isNotBlank() && uiState.amount.isNotBlank(),
              ) {
                Text(if (uiState.state == SendState.Error) "Retry" else "Continue")
              }
            }
            SendState.Confirming -> {
              // Confirm button triggers biometric + broadcast
              Button(
                onClick = onConfirmSend,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("Confirm & Send")
              }
              Spacer(modifier = Modifier.height(8.dp))
              OutlinedButton(
                onClick = onResetState,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("Cancel")
              }
            }
            SendState.Submitting -> {
              // Button disabled during submission (duplicate prevention)
              Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
              ) {
                Text("Sending...")
              }
            }
            else -> { /* Building, Success handled elsewhere */ }
          }
        }
      }
    }
  }

  // Confirmation dialog (shown in Confirming state when user taps Confirm & Send)
  // The actual biometric prompt is triggered by the caller
}

/**
 * Address input field with validation feedback.
 */
@Composable
private fun AddressInput(
  address: String,
  onAddressChanged: (String) -> Unit,
  validation: AddressValidation?,
  currency: String,
  enabled: Boolean,
) {
  OutlinedTextField(
    value = address,
    onValueChange = onAddressChanged,
    label = { Text("Recipient Address") },
    placeholder = {
      Text(
        if (currency == "BTC") "BTC address (1... or m...)"
        else "0x address"
      )
    },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    enabled = enabled,
    isError = validation is AddressValidation.Invalid,
    supportingText = {
      when (validation) {
        is AddressValidation.Invalid -> Text(
          text = validation.message,
          color = MaterialTheme.colorScheme.error,
        )
        is AddressValidation.ChecksumWarning -> Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Filled.Warning,
            contentDescription = "Warning",
            tint = MaterialTheme.colorScheme.tertiary,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = validation.message,
            color = MaterialTheme.colorScheme.tertiary,
          )
        }
        else -> {}
      }
    },
  )
}

/**
 * Amount input field with validation feedback.
 */
@Composable
private fun AmountInput(
  amount: String,
  onAmountChanged: (String) -> Unit,
  validation: AmountValidation?,
  currency: String,
  enabled: Boolean,
) {
  OutlinedTextField(
    value = amount,
    onValueChange = onAmountChanged,
    label = { Text("Amount ($currency)") },
    placeholder = { Text("0.00") },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    enabled = enabled,
    isError = validation is AmountValidation.Invalid,
    supportingText = {
      if (validation is AmountValidation.Invalid) {
        Text(
          text = validation.message,
          color = MaterialTheme.colorScheme.error,
        )
      }
    },
  )
}

/**
 * Fee tier selector showing Fast/Normal/Slow options with estimated time and fee.
 */
@Composable
private fun FeeTierSelector(
  feeOptions: List<FeeTierOption>,
  selectedIndex: Int,
  onSelectTier: (Int) -> Unit,
  enabled: Boolean,
) {
  Text(
    text = "Select Fee",
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
  )
  Spacer(modifier = Modifier.height(8.dp))

  feeOptions.forEachIndexed { index, option ->
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .selectable(
          selected = index == selectedIndex,
          onClick = { onSelectTier(index) },
          role = Role.RadioButton,
          enabled = enabled,
        ),
      border = if (index == selectedIndex) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
      } else {
        null
      },
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(
          selected = index == selectedIndex,
          onClick = null,
          enabled = enabled,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = option.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = option.estimatedTime,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = option.feeAmount,
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = FontFamily.Monospace,
        )
      }
    }
  }
}

/**
 * Error banner displaying error messages.
 */
@Composable
private fun ErrorBanner(message: String) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer,
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Filled.Warning,
        contentDescription = "Error",
        tint = MaterialTheme.colorScheme.onErrorContainer,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = message,
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

/**
 * Success content showing the transaction hash.
 */
@Composable
private fun SuccessContent(
  txHash: String,
  currency: String,
  onDone: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      Icons.Filled.CheckCircle,
      contentDescription = "Success",
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(bottom = 16.dp),
    )
    Text(
      text = "Transaction Sent!",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = "Your $currency transaction has been broadcast to the network.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(24.dp))

    // Transaction hash
    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "Transaction Hash",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = txHash,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
        )
      }
    }

    Spacer(modifier = Modifier.height(32.dp))
    Button(
      onClick = onDone,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Done")
    }
  }
}

/**
 * Confirmation dialog showing transaction details before broadcast.
 *
 * Displays recipient, amount, fee, and total. User confirms to trigger
 * biometric prompt and then broadcast.
 *
 * @param recipientAddress the recipient address
 * @param amount the send amount
 * @param currency the currency symbol
 * @param fee the selected fee amount string
 * @param onConfirm callback when user confirms
 * @param onDismiss callback when user dismisses
 */
@Composable
fun ConfirmationDialog(
  recipientAddress: String,
  amount: String,
  currency: String,
  fee: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Confirm Transaction") },
    text = {
      Column {
        DetailRow("To:", recipientAddress)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        DetailRow("Amount:", "$amount $currency")
        DetailRow("Fee:", fee)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        val amountNum = amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        val feeNum = fee.split(" ").firstOrNull()?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        val total = amountNum.add(feeNum)
        DetailRow("Total:", "${total.toPlainString()} $currency")
      }
    },
    confirmButton = {
      Button(onClick = onConfirm) {
        Text("Confirm")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun DetailRow(label: String, value: String) {
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
      fontFamily = FontFamily.Monospace,
      modifier = Modifier.weight(1f, fill = false),
    )
  }
}
