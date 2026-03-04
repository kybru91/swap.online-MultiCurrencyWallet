package com.mcw.feature.dappbrowser

import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * Native side of the JS-to-Native bridge for the window.ethereum provider.
 *
 * Per tech-spec Decision 9:
 * - Single @JavascriptInterface method exposing EIP-1193 request() only
 * - Origin validation on every bridge call
 * - Rate limit: 10 calls/sec
 *
 * The bridge receives calls from the injected JS provider (MCWBridge.request()),
 * validates them, and dispatches to [DAppBrowserViewModel] for processing.
 *
 * Thread safety: @JavascriptInterface methods are called on a WebView background
 * thread, not the main thread. All mutable state access is thread-safe via
 * atomic operations (rate limiter) or dispatched to the main thread (WebView eval).
 *
 * @param webView the WebView instance (for evaluateJavascript callbacks)
 * @param originValidator validates that the current URL matches the approved origin
 * @param rateLimiter rate limits bridge calls to 10/sec
 * @param onRequest callback to process the RPC request (invoked on bridge thread)
 */
class EthereumBridge(
  private val webView: WebView,
  private val originValidator: OriginValidator,
  private val rateLimiter: RpcRateLimiter,
  private val approvedOrigin: String,
  private val onRequest: (callbackId: Int, method: String, params: String) -> Unit,
) {

  companion object {
    /** Name used in WebView.addJavascriptInterface() */
    const val BRIDGE_NAME = "MCWBridge"

    /** EIP-1193 error codes */
    const val ERROR_USER_REJECTED = 4001
    const val ERROR_UNAUTHORIZED = 4100
    const val ERROR_UNSUPPORTED_METHOD = 4200
    const val ERROR_DISCONNECTED = 4900
    const val ERROR_CHAIN_DISCONNECTED = 4901
    const val ERROR_INVALID_PARAMS = -32602
    const val ERROR_INTERNAL = -32603
    const val ERROR_RATE_LIMITED = -32005
  }

  /**
   * Single @JavascriptInterface entry point for all EIP-1193 requests.
   *
   * Called from JS: MCWBridge.request(callbackId, method, paramsJson)
   *
   * Performs:
   * 1. Origin validation
   * 2. Rate limit check
   * 3. Method validation
   * 4. Dispatches to onRequest callback
   *
   * Responses are sent back to JS via evaluateJavascript() calling window._mcwCallback().
   *
   * @param callbackId the JS callback ID for promise resolution
   * @param method the EIP-1193 method name
   * @param paramsJson the JSON-encoded params array
   */
  @JavascriptInterface
  fun request(callbackId: Int, method: String, paramsJson: String) {
    // 1. Origin validation: check WebView URL matches approved origin
    val currentUrl = webView.url
    if (!originValidator.validateOrigin(approvedOrigin, currentUrl)) {
      sendError(callbackId, ERROR_UNAUTHORIZED, "Origin mismatch: request blocked")
      return
    }

    // 2. Rate limit check
    if (!rateLimiter.tryAcquire()) {
      sendError(callbackId, ERROR_RATE_LIMITED, "Rate limit exceeded: max 10 calls/sec")
      return
    }

    // 3. Dispatch to handler
    onRequest(callbackId, method, paramsJson)
  }

  /**
   * Send a success response back to JS via evaluateJavascript.
   *
   * @param callbackId the JS callback ID
   * @param result the result value (will be JSON-encoded if needed)
   */
  fun sendResult(callbackId: Int, result: String) {
    val js = "window._mcwCallback($callbackId, null, $result)"
    webView.post { webView.evaluateJavascript(js, null) }
  }

  /**
   * Send an error response back to JS via evaluateJavascript.
   *
   * @param callbackId the JS callback ID
   * @param code EIP-1193 error code
   * @param message human-readable error message
   */
  fun sendError(callbackId: Int, code: Int, message: String) {
    // Escape message for JS string literal
    val escaped = message.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
    val js = "window._mcwCallback($callbackId, {code: $code, message: '$escaped'}, null)"
    webView.post { webView.evaluateJavascript(js, null) }
  }

  /**
   * Emit an EIP-1193 event to the JS provider.
   *
   * @param event the event name (e.g., "chainChanged", "accountsChanged")
   * @param data the event data (JSON-encoded)
   */
  fun emitEvent(event: String, data: String) {
    val js = "window._mcwEvent('$event', $data)"
    webView.post { webView.evaluateJavascript(js, null) }
  }
}
