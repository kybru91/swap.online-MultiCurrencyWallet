package com.mcw.feature.dappbrowser

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * dApp Browser screen with WebView and injected window.ethereum provider.
 *
 * Features:
 * - URL bar with navigation controls
 * - WebView with security hardening per Decision 9
 * - Injected EIP-1193 provider
 * - Transaction confirmation dialogs
 * - Domain blocking interstitial
 *
 * @param ethAddress the connected wallet's ETH address
 * @param initialUrl optional URL to load on launch
 */
@Composable
fun DAppBrowserScreen(
  ethAddress: String,
  initialUrl: String = "",
) {
  val viewModel = remember { DAppBrowserViewModel(ethAddress) }
  val uiState by viewModel.uiState.collectAsState()
  val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
  var urlInput by remember { mutableStateOf(initialUrl) }
  var webViewRef by remember { mutableStateOf<WebView?>(null) }

  Column(modifier = Modifier.fillMaxSize()) {
    // URL bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = urlInput,
        onValueChange = { urlInput = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text("Enter dApp URL") },
        singleLine = true,
      )
      TextButton(onClick = {
        val url = if (urlInput.startsWith("https://")) urlInput else "https://$urlInput"
        webViewRef?.loadUrl(url)
      }) {
        Text("Go")
      }
    }

    // Loading indicator
    if (uiState.isLoading) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    // Domain blocked warning
    if (uiState.domainBlocked) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFFFFEBEE))
          .padding(12.dp),
      ) {
        Text(
          text = "External navigation blocked: ${uiState.blockedDomain}",
          color = Color(0xFFB71C1C),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }

    // WebView
    val context = LocalContext.current
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = {
        createSecureWebView(context, viewModel).also { webView ->
          webViewRef = webView
          if (initialUrl.isNotEmpty()) {
            webView.loadUrl(initialUrl)
          }
        }
      },
    )
  }

  // Transaction confirmation dialog
  pendingConfirmation?.let { pending ->
    when (pending.displayInfo) {
      is TransactionDisplayInfo -> TransactionConfirmationDialog(
        info = pending.displayInfo,
        onApprove = { viewModel.approveConfirmation() },
        onReject = { viewModel.rejectConfirmation() },
      )
      is SigningDisplayInfo -> SigningConfirmationDialog(
        info = pending.displayInfo,
        method = pending.method,
        onApprove = { viewModel.approveConfirmation() },
        onReject = { viewModel.rejectConfirmation() },
      )
    }
  }
}

/**
 * Create a WebView with security hardening per Decision 9.
 */
@Suppress("DEPRECATION") // allowFileAccessFromFileURLs/allowUniversalAccessFromFileURLs: intentional security hardening per Decision 9
@SuppressLint("SetJavaScriptEnabled")
private fun createSecureWebView(
  context: android.content.Context,
  viewModel: DAppBrowserViewModel,
): WebView {
  return WebView(context).apply {
    // Security settings per Decision 9
    settings.apply {
      javaScriptEnabled = true // Required for dApp interaction
      allowFileAccess = false
      allowContentAccess = false
      mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      setGeolocationEnabled(false)
      allowFileAccessFromFileURLs = false
      allowUniversalAccessFromFileURLs = false
      javaScriptCanOpenWindowsAutomatically = false

      // Standard WebView settings
      domStorageEnabled = true // Required for most dApps
      databaseEnabled = false
      setSupportMultipleWindows(false)
      userAgentString = settings.userAgentString + " MCWWallet/1.0"
    }

    // Debug only in debug builds
    WebView.setWebContentsDebuggingEnabled(false) // Set to BuildConfig.DEBUG in production

    // Set up the WebView client for URL validation and injection
    webViewClient = DAppWebViewClient(
      domainPolicy = viewModel.domainPolicy,
      onDomainBlocked = { _ ->
        // Update UI state with blocked domain info
      },
      onPageLoaded = { _ ->
        // Update UI state with current URL
      },
    )

    // Add the JS bridge with self-reference for response dispatch
    // Using array wrapper to allow lambda capture of mutable reference
    val bridgeHolder = arrayOfNulls<EthereumBridge>(1)
    val bridge = EthereumBridge(
      webView = this,
      originValidator = viewModel.originValidator,
      rateLimiter = viewModel.rateLimiter,
      approvedOrigin = url ?: "",
      onRequest = { callbackId, method, params ->
        val b = bridgeHolder[0] ?: return@EthereumBridge
        viewModel.handleRequest(callbackId, method, params, bridge = b)
      },
    )
    bridgeHolder[0] = bridge
    addJavascriptInterface(bridge, EthereumBridge.BRIDGE_NAME)
  }
}

/**
 * Transaction confirmation dialog with decoded function signature display.
 */
@Composable
private fun TransactionConfirmationDialog(
  info: TransactionDisplayInfo,
  onApprove: () -> Unit,
  onReject: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onReject,
    title = { Text("Confirm Transaction") },
    text = {
      Column {
        Text("To: ${info.to}")
        Text("Value: ${info.value}")

        info.decodedFunction?.let { func ->
          Text(
            text = "Action: ${func.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
          )
        }

        if (info.isUnlimitedApproval) {
          Text(
            text = "WARNING: Unlimited token approval",
            color = Color(0xFFB71C1C),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
          )
        }

        if (info.gasWarning) {
          Text(
            text = "WARNING: High gas limit",
            color = Color(0xFFE65100),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onApprove) { Text("Approve") }
    },
    dismissButton = {
      TextButton(onClick = onReject) { Text("Reject") }
    },
  )
}

/**
 * Message signing confirmation dialog.
 */
@Composable
private fun SigningConfirmationDialog(
  info: SigningDisplayInfo,
  method: String,
  onApprove: () -> Unit,
  onReject: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onReject,
    title = {
      Text(
        when (method) {
          "personal_sign" -> "Sign Message"
          "eth_signTypedData_v4" -> "Sign Typed Data"
          else -> "Sign Request"
        }
      )
    },
    text = {
      Column {
        if (!info.isHumanReadable) {
          Text(
            text = "WARNING: Message is not human-readable",
            color = Color(0xFFE65100),
            modifier = Modifier.padding(bottom = 8.dp),
          )
        }
        Text(
          text = info.message.take(500), // Truncate long messages for display
          style = MaterialTheme.typography.bodySmall,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onApprove) { Text("Sign") }
    },
    dismissButton = {
      TextButton(onClick = onReject) { Text("Reject") }
    },
  )
}
