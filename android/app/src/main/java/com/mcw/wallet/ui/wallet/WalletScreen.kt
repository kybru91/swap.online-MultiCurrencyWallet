@file:OptIn(ExperimentalMaterialApi::class)

package com.mcw.wallet.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wallet screen displaying balance list with pull-to-refresh and offline mode banner.
 *
 * Uses state hoisting pattern: receives UI state and callbacks from the parent.
 * The ViewModel is managed by the parent composable (WalletApp NavHost).
 *
 * Tech-spec Balance Display:
 * - BTC (8 decimals), ETH/BNB/MATIC (18 decimals)
 * - Zero balances show as "0.00" not empty
 * - Pull-to-refresh triggers new API call
 * - Offline: retain previous balances, show error banner, disable Send
 *
 * @param uiState the current wallet UI state
 * @param onRefresh callback when user pulls to refresh
 * @param onNavigateToSend callback for Send button
 * @param onNavigateToHistory callback for History button
 * @param onNavigateToSettings callback for Settings button
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun WalletScreen(
  uiState: WalletUiState = WalletUiState(),
  onRefresh: () -> Unit = {},
  onNavigateToSend: () -> Unit = {},
  onNavigateToHistory: () -> Unit = {},
  onNavigateToSettings: () -> Unit = {},
) {
  // Pull-to-refresh state
  val pullRefreshState = rememberPullRefreshState(
    refreshing = uiState.isLoading,
    onRefresh = onRefresh,
  )

  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Wallet") },
      actions = {
        IconButton(onClick = onNavigateToHistory) {
          Icon(Icons.Filled.History, contentDescription = "History")
        }
        IconButton(onClick = onNavigateToSettings) {
          Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
      },
    )

    // Offline error banner
    if (uiState.error != null) {
      OfflineBanner(
        message = uiState.error,
        lastUpdated = uiState.lastUpdated,
      )
    }

    // Balance list with pull-to-refresh
    Box(
      modifier = Modifier
        .weight(1f)
        .pullRefresh(pullRefreshState),
    ) {
      if (uiState.balances.isEmpty() && !uiState.isLoading) {
        // Empty state
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "No balances to display",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(uiState.balances) { balance ->
            BalanceCard(
              balance = balance,
              onSendClick = onNavigateToSend,
              isSendDisabled = uiState.isSendDisabled,
            )
          }
        }
      }

      PullRefreshIndicator(
        refreshing = uiState.isLoading,
        state = pullRefreshState,
        modifier = Modifier.align(Alignment.TopCenter),
      )
    }
  }
}

/**
 * Offline mode banner displayed when balance fetch fails.
 *
 * Shows error message and "Last updated" timestamp if available.
 * Tech-spec: "show 'No internet connection' error banner"
 */
@Composable
private fun OfflineBanner(
  message: String,
  lastUpdated: Long?,
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        Icons.Filled.WifiOff,
        contentDescription = "Offline",
        tint = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.size(24.dp),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(
          text = message,
          color = MaterialTheme.colorScheme.onErrorContainer,
          style = MaterialTheme.typography.bodyMedium,
        )
        if (lastUpdated != null) {
          val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
          Text(
            text = "Last updated: ${dateFormat.format(Date(lastUpdated))}",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }
}

/**
 * Balance card for a single currency showing balance and fiat value.
 *
 * @param balance the currency balance data
 * @param onSendClick callback for the Send button
 * @param isSendDisabled whether Send button is disabled (offline mode)
 */
@Composable
private fun BalanceCard(
  balance: CurrencyBalanceUi,
  onSendClick: () -> Unit,
  isSendDisabled: Boolean,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = balance.currency,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = formatBalance(balance.balance, balance.decimals),
          style = MaterialTheme.typography.bodyLarge,
        )
        if (balance.balanceUsd != null) {
          Text(
            text = "$${formatFiat(balance.balanceUsd)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text(
            text = "N/A",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      IconButton(
        onClick = onSendClick,
        enabled = !isSendDisabled,
      ) {
        Icon(
          Icons.AutoMirrored.Filled.Send,
          contentDescription = "Send ${balance.currency}",
        )
      }
    }
  }
}

/**
 * Formats a balance BigDecimal with appropriate precision.
 * Zero balances display as "0.00" per spec: "Zero balances show as '0.00' not empty"
 */
private fun formatBalance(balance: BigDecimal, decimals: Int): String {
  // Show at most 8 decimals for display, min 2
  val displayDecimals = minOf(decimals, 8).coerceAtLeast(2)
  return balance.setScale(displayDecimals, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
    .let { stripped ->
      // Ensure at least 2 decimal places
      if (stripped.scale() < 2) stripped.setScale(2) else stripped
    }
    .toPlainString()
}

/**
 * Formats a fiat value to 2 decimal places.
 */
private fun formatFiat(value: BigDecimal): String {
  return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
}
