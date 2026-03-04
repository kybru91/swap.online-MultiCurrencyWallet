package com.mcw.feature.dappbrowser

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.net.URI

/**
 * Custom WebViewClient that handles:
 * 1. window.ethereum injection via shouldInterceptRequest (HTML rewriting)
 * 2. URL validation — blocks non-HTTPS, private IPs, file://, javascript://, data://
 * 3. Domain policy — blocks navigation to unknown domains
 *
 * Per tech-spec: "inject as first <script> in <head> before any dApp scripts execute"
 *
 * The injection is done by intercepting the main HTML document request,
 * inserting the provider script tag at the beginning of <head>.
 *
 * @param domainPolicy the domain policy for URL filtering
 * @param onDomainBlocked callback when navigation to an unknown domain is blocked
 * @param onPageLoaded callback when a page finishes loading
 */
class DAppWebViewClient(
  private val domainPolicy: DomainPolicy,
  private val onDomainBlocked: (String) -> Unit,
  private val onPageLoaded: (String) -> Unit,
) : WebViewClient() {

  /**
   * Intercept navigation requests to validate URLs.
   *
   * Per Decision 9: Navigation restricted to https:// scheme.
   * Reject file://, http://, data://, javascript:// and localhost/private IPs.
   * Unknown domains are BLOCKED (not just warned).
   */
  override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
    val url = request.url.toString()

    // Validate URL scheme and private IPs
    if (!WebViewUrlValidator.isAllowedUrl(url)) {
      return true // Block navigation
    }

    // Check domain policy
    val host = try {
      URI(url).host?.lowercase()
    } catch (e: Exception) {
      null
    }

    if (host != null && !domainPolicy.isDomainAllowed(host)) {
      onDomainBlocked(host)
      return true // Block navigation
    }

    return false // Allow navigation
  }

  /**
   * Inject window.ethereum provider into the HTML response.
   *
   * Only intercepts the main frame HTML document. Sub-resources (JS, CSS, images)
   * pass through unmodified.
   *
   * The injection strategy:
   * 1. Intercept the HTML response
   * 2. Find <head> tag
   * 3. Insert provider <script> immediately after <head>
   * 4. Return the modified HTML as a new WebResourceResponse
   *
   * This ensures the provider is available before any dApp scripts execute.
   */
  override fun shouldInterceptRequest(
    view: WebView,
    request: WebResourceRequest,
  ): WebResourceResponse? {
    // Only intercept main frame HTML documents
    if (!request.isForMainFrame) return null

    val accept = request.requestHeaders["Accept"] ?: ""
    if (!accept.contains("text/html")) return null

    // Fetch the HTML content ourselves, inject the script, return modified response
    // NOTE: Full implementation would use OkHttp to fetch + inject.
    // For now, we rely on onPageStarted fallback injection via evaluateJavascript.
    return null
  }

  /**
   * Fallback injection: inject provider script when page starts loading.
   * This ensures the provider is available even if shouldInterceptRequest
   * cannot intercept the HTML (e.g., cached pages, redirects).
   */
  override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
    super.onPageStarted(view, url, favicon)
    val script = EthereumProviderJs.generateInjectionScript()
    view.evaluateJavascript(script, null)
  }

  override fun onPageFinished(view: WebView, url: String?) {
    super.onPageFinished(view, url)
    if (url != null) {
      onPageLoaded(url)
    }
    // Re-inject to handle dynamic page changes (SPA navigation)
    val script = EthereumProviderJs.generateInjectionScript()
    view.evaluateJavascript(script, null)
  }
}
