// TODO: Migrate from compose-material pullRefresh to Material3 PullToRefreshBox
// when BOM is upgraded from 2024.02.00 to 2024.09.00+
@file:OptIn(ExperimentalMaterialApi::class)

package com.mcw.wallet.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcw.core.network.TransactionRecord
import com.mcw.core.network.TxDirection
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transaction History screen with pull-to-refresh.
 *
 * Displays a LazyColumn of TransactionRecord items with:
 * - Direction icon (arrow up for OUT, arrow down for IN, swap for SELF)
 * - Truncated tx hash
 * - Amount with currency
 * - Relative timestamp
 * - Confirmations count
 *
 * Tap on a transaction to expand detail view showing:
 * - Full tx hash
 * - Block number
 * - Fee
 * - Counterparty address
 *
 * Tech-spec requirements:
 * - Pull-to-refresh for history reload
 * - Display direction icon, amount, timestamp, confirmations
 * - Tap tx -> show details (full hash, block, fee, counterparty address)
 *
 * @param uiState the current history UI state
 * @param onRefresh callback when user pulls to refresh
 * @param onTxClick callback when user taps a transaction
 * @param onNavigateBack callback for the back button
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HistoryScreen(
  uiState: HistoryUiState = HistoryUiState(),
  onRefresh: () -> Unit = {},
  onTxClick: (String) -> Unit = {},
  onNavigateBack: () -> Unit = {},
) {
  val pullRefreshState = rememberPullRefreshState(
    refreshing = uiState.isLoading,
    onRefresh = onRefresh,
  )

  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Transaction History") },
      navigationIcon = {
        IconButton(onClick = onNavigateBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      },
    )

    // Error banner
    if (uiState.error != null) {
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
            Icons.Filled.Error,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(24.dp),
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = uiState.error,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }

    // Transaction list with pull-to-refresh
    Box(
      modifier = Modifier
        .weight(1f)
        .pullRefresh(pullRefreshState),
    ) {
      if (uiState.transactions.isEmpty() && !uiState.isLoading) {
        // Empty state
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "No transactions yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          items(
            items = uiState.transactions,
            key = { "${it.hash}_${it.currency}" },
          ) { tx ->
            TransactionItem(
              tx = tx,
              isExpanded = uiState.expandedTxHash == tx.hash,
              onClick = { onTxClick(tx.hash) },
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
 * A single transaction list item.
 *
 * Shows: direction icon, truncated hash, amount, timestamp, confirmations.
 * Expandable: shows full hash, block number, fee, counterparty on tap.
 */
@Composable
private fun TransactionItem(
  tx: TransactionRecord,
  isExpanded: Boolean,
  onClick: () -> Unit,
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    colors = if (tx.isError) {
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
      )
    } else {
      CardDefaults.cardColors()
    },
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      // Main row: icon + hash + amount
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Direction icon
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          DirectionIcon(
            direction = tx.direction,
            isError = tx.isError,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            // Truncated hash
            Text(
              text = truncateHash(tx.hash),
              style = MaterialTheme.typography.bodyMedium,
              fontFamily = FontFamily.Monospace,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            // Timestamp
            Text(
              text = formatTimestamp(tx.timestamp),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Amount + currency
        Column(horizontalAlignment = Alignment.End) {
          val sign = when (tx.direction) {
            TxDirection.IN -> "+"
            TxDirection.OUT -> "-"
            TxDirection.SELF -> ""
          }
          val amountColor = when {
            tx.isError -> MaterialTheme.colorScheme.error
            tx.direction == TxDirection.IN -> Color(0xFF4CAF50) // Green
            tx.direction == TxDirection.OUT -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
          }
          Text(
            text = "$sign${formatAmount(tx.amount)} ${tx.currency}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = amountColor,
          )
          // Confirmations
          Text(
            text = formatConfirmations(tx.confirmations),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Expanded detail view
      AnimatedVisibility(visible = isExpanded) {
        Column(modifier = Modifier.padding(top = 8.dp)) {
          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

          DetailRow("Hash", tx.hash)
          DetailRow("Block", if (tx.blockNumber > 0) tx.blockNumber.toString() else "Pending")
          DetailRow("Fee", "${formatAmount(tx.fee)} ${tx.currency}")
          DetailRow("Counterparty", tx.counterpartyAddress)

          if (tx.isError) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Transaction failed",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

/**
 * Direction icon composable.
 * - IN: green arrow down
 * - OUT: red arrow up
 * - SELF: neutral swap icon
 */
@Composable
private fun DirectionIcon(direction: TxDirection, isError: Boolean) {
  val (icon, tint, description) = when {
    isError -> Triple(
      Icons.Filled.Error,
      MaterialTheme.colorScheme.error,
      "Failed",
    )
    direction == TxDirection.IN -> Triple(
      Icons.Filled.ArrowDownward,
      Color(0xFF4CAF50),
      "Received",
    )
    direction == TxDirection.OUT -> Triple(
      Icons.Filled.ArrowUpward,
      MaterialTheme.colorScheme.error,
      "Sent",
    )
    else -> Triple(
      Icons.Filled.SwapVert,
      MaterialTheme.colorScheme.onSurfaceVariant,
      "Self",
    )
  }

  Icon(
    imageVector = icon,
    contentDescription = description,
    tint = tint,
    modifier = Modifier.size(28.dp),
  )
}

/**
 * Detail row in the expanded tx view.
 */
@Composable
private fun DetailRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(80.dp),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      fontFamily = FontFamily.Monospace,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
  }
}

// ===== Formatting helpers =====

/**
 * Truncates a tx hash for list display.
 * "0xabcdef...123456" or "abcdef...123456"
 */
internal fun truncateHash(hash: String): String {
  if (hash.length <= 16) return hash
  return "${hash.take(8)}...${hash.takeLast(6)}"
}

/**
 * Formats a unix epoch seconds timestamp to a human-readable string.
 */
internal fun formatTimestamp(epochSeconds: Long): String {
  if (epochSeconds <= 0) return "Pending"
  val date = Date(epochSeconds * 1000)
  val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
  return sdf.format(date)
}

/**
 * Formats a BigDecimal amount, stripping trailing zeros but keeping at least 2 decimals.
 */
internal fun formatAmount(amount: BigDecimal): String {
  val stripped = amount.stripTrailingZeros()
  return if (stripped.scale() < 2) {
    stripped.setScale(2).toPlainString()
  } else {
    stripped.toPlainString()
  }
}

/**
 * Formats confirmations count for display.
 */
internal fun formatConfirmations(confirmations: Int): String {
  return when {
    confirmations <= 0 -> "Unconfirmed"
    confirmations == 1 -> "1 confirmation"
    confirmations > 1000 -> "1000+ confirmations"
    else -> "$confirmations confirmations"
  }
}
