package com.mcw.feature.dappbrowser

/**
 * Generates the JavaScript code for the injected window.ethereum EIP-1193 provider.
 *
 * Per tech-spec:
 * - Injected via shouldInterceptRequest() as first <script> in <head>
 * - Uses Object.freeze(window.ethereum) to prevent dApp tampering
 * - Single request() method that bridges to native @JavascriptInterface
 * - Emits EIP-1193 events: accountsChanged, chainChanged, connect, disconnect
 * - Dispatches EIP-6963 announceProvider event for modern dApp detection
 * - Sets isMetaMask: true for broad dApp compatibility
 */
object EthereumProviderJs {

  /**
   * Generate the complete JavaScript injection script.
   *
   * This script:
   * 1. Creates a window.ethereum object with EIP-1193 request() method
   * 2. Implements event emitter (on/removeListener/emit)
   * 3. Bridges request() calls to the native MCWBridge.request() JavascriptInterface
   * 4. Uses a promise-based callback system with unique IDs
   * 5. Freezes the provider to prevent dApp tampering
   * 6. Announces via EIP-6963 for modern dApp support
   *
   * @return the complete JavaScript code to inject into the WebView
   */
  fun generateInjectionScript(): String {
    return """
(function() {
  'use strict';

  // Prevent double injection
  if (window.ethereum && window.ethereum._isMCW) return;

  // Callback registry for async native bridge responses
  var _callbacks = {};
  var _callbackId = 0;

  // Event listeners registry
  var _listeners = {};

  // Provider state
  var _chainId = null;
  var _accounts = [];
  var _connected = false;

  // Event emitter implementation
  function _on(event, handler) {
    if (!_listeners[event]) _listeners[event] = [];
    _listeners[event].push(handler);
  }

  function _removeListener(event, handler) {
    if (!_listeners[event]) return;
    _listeners[event] = _listeners[event].filter(function(h) { return h !== handler; });
  }

  function _emit(event, data) {
    if (!_listeners[event]) return;
    _listeners[event].forEach(function(handler) {
      try { handler(data); } catch(e) { console.error('MCW provider event error:', e); }
    });
  }

  // Native bridge callback (called from Kotlin via evaluateJavascript)
  window._mcwCallback = function(id, error, result) {
    var cb = _callbacks[id];
    if (!cb) return;
    delete _callbacks[id];
    if (error) {
      cb.reject(error);
    } else {
      cb.resolve(result);
    }
  };

  // Native bridge event (called from Kotlin for state changes)
  window._mcwEvent = function(event, data) {
    if (event === 'chainChanged') {
      _chainId = data;
      _emit('chainChanged', data);
    } else if (event === 'accountsChanged') {
      _accounts = data || [];
      _emit('accountsChanged', _accounts);
    } else if (event === 'connect') {
      _connected = true;
      _emit('connect', { chainId: data });
    } else if (event === 'disconnect') {
      _connected = false;
      _emit('disconnect', data);
    }
  };

  // EIP-1193 request method
  function _request(args) {
    return new Promise(function(resolve, reject) {
      if (!args || !args.method) {
        reject({ code: -32600, message: 'Invalid request: missing method' });
        return;
      }

      var id = ++_callbackId;
      _callbacks[id] = { resolve: resolve, reject: reject };

      try {
        // Bridge to native via @JavascriptInterface
        MCWBridge.request(id, args.method, JSON.stringify(args.params || []));
      } catch(e) {
        delete _callbacks[id];
        reject({ code: -32603, message: 'Internal error: bridge unavailable' });
      }
    });
  }

  // Build the provider object
  var provider = {
    // EIP-1193 standard
    request: _request,

    // Legacy compatibility (MetaMask)
    send: function(methodOrPayload, paramsOrCallback) {
      if (typeof methodOrPayload === 'string') {
        return _request({ method: methodOrPayload, params: paramsOrCallback || [] });
      }
      return _request(methodOrPayload);
    },
    sendAsync: function(payload, callback) {
      _request({ method: payload.method, params: payload.params })
        .then(function(result) {
          callback(null, { id: payload.id, jsonrpc: '2.0', result: result });
        })
        .catch(function(error) {
          callback(error, null);
        });
    },

    // Event emitter
    on: _on,
    removeListener: _removeListener,
    addListener: _on,

    // Identity
    isMetaMask: true,
    _isMCW: true,

    // State
    get chainId() { return _chainId; },
    get selectedAddress() { return _accounts[0] || null; },
    get networkVersion() {
      if (!_chainId) return null;
      return String(parseInt(_chainId, 16));
    },
    isConnected: function() { return _connected; },
  };

  // Assign to window.ethereum and freeze to prevent dApp tampering
  window.ethereum = provider;
  Object.freeze(window.ethereum);

  // EIP-6963: Announce provider for modern dApp detection
  var info = {
    uuid: 'mcw-wallet-' + Date.now(),
    name: 'MCW Wallet',
    icon: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><circle cx="16" cy="16" r="16" fill="%234A90D9"/><text x="16" y="22" text-anchor="middle" fill="white" font-size="18" font-family="sans-serif">M</text></svg>',
    rdns: 'com.mcw.wallet',
  };

  window.dispatchEvent(new CustomEvent('eip6963:announceProvider', {
    detail: Object.freeze({ info: Object.freeze(info), provider: provider })
  }));

  // Re-announce on request
  window.addEventListener('eip6963:requestProvider', function() {
    window.dispatchEvent(new CustomEvent('eip6963:announceProvider', {
      detail: Object.freeze({ info: Object.freeze(info), provider: provider })
    }));
  });

})();
""".trimIndent()
  }

  /**
   * Generate a <script> tag wrapping the injection script, suitable for
   * insertion at the beginning of <head> in intercepted HTML.
   */
  fun generateScriptTag(): String {
    return "<script>${generateInjectionScript()}</script>"
  }
}
