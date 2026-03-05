import React, {useState, useRef} from 'react';
import {
  View,
  Text,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  Platform,
} from 'react-native';
import {WebView} from 'react-native-webview';
import type {WebViewMessageEvent} from 'react-native-webview';
import {useWalletStore} from '@state/walletStore';
import {useAuthStore} from '@state/authStore';
import {getEthPrivateKey} from '@storage/SecureStorage';

/**
 * EIP-1193 JavaScript bridge injected into DApp WebView.
 * Exposes window.ethereum to web3 dApps.
 * Handles: eth_requestAccounts, eth_accounts, eth_chainId, eth_sendTransaction (prompt).
 */
const EIP1193_BRIDGE = `
(function() {
  if (window.ethereum) return;

  var callbacks = {};
  var callId = 0;

  function sendToNative(method, params) {
    return new Promise(function(resolve, reject) {
      var id = ++callId;
      callbacks[id] = { resolve: resolve, reject: reject };
      window.ReactNativeWebView.postMessage(JSON.stringify({
        type: 'eip1193',
        id: id,
        method: method,
        params: params || []
      }));
    });
  }

  window.ethereum = {
    isMetaMask: false,
    isMCWallet: true,
    chainId: null,
    selectedAddress: null,

    request: function(args) {
      return sendToNative(args.method, args.params);
    },

    on: function(event, handler) {
      // Basic event subscription — more events added as needed
      document.addEventListener('mcw:' + event, function(e) {
        handler(e.detail);
      });
    },

    removeListener: function(event, handler) {
      document.removeEventListener('mcw:' + event, handler);
    }
  };

  // Handle responses from native layer
  window._mcwBridgeResponse = function(id, result, error) {
    var cb = callbacks[id];
    if (!cb) return;
    delete callbacks[id];
    if (error) {
      cb.reject(new Error(error));
    } else {
      cb.resolve(result);
    }
  };

  true;
})();
`;

export default function DAppBrowserScreen() {
  const {ethAddress, activeChainId} = useWalletStore();
  const onUserInteraction = useAuthStore(s => s.onUserInteraction);
  const webViewRef = useRef<WebView>(null);

  const [url, setUrl] = useState('https://uniswap.org/');
  const [inputUrl, setInputUrl] = useState(url);
  const [loading, setLoading] = useState(false);
  const [connected, setConnected] = useState(false);

  const handleNavigate = () => {
    onUserInteraction();
    let target = inputUrl.trim();
    if (!target.startsWith('http')) target = 'https://' + target;
    setUrl(target);
  };

  const handleMessage = async (event: WebViewMessageEvent) => {
    onUserInteraction();
    try {
      const msg = JSON.parse(event.nativeEvent.data);
      if (msg.type !== 'eip1193') return;

      const {id, method, params} = msg;

      let result: unknown = null;
      let error: string | null = null;

      switch (method) {
        case 'eth_requestAccounts':
        case 'eth_accounts':
          if (!connected) {
            await new Promise<void>((resolve, reject) => {
              Alert.alert(
                'Connect Wallet',
                `Allow this DApp to see your wallet address?\n\n${ethAddress?.slice(0, 20)}...`,
                [
                  {text: 'Reject', style: 'cancel', onPress: () => reject(new Error('User rejected'))},
                  {text: 'Connect', onPress: () => { setConnected(true); resolve(); }},
                ],
              );
            }).catch(() => {
              error = 'User rejected the request';
            });
          }
          result = error ? null : [ethAddress];
          break;

        case 'eth_chainId':
          result = `0x${activeChainId.toString(16)}`;
          break;

        case 'net_version':
          result = String(activeChainId);
          break;

        case 'eth_sendTransaction':
          Alert.alert(
            'Transaction Request',
            'This DApp wants to send a transaction. Full signing support coming soon.',
            [{text: 'OK'}],
          );
          error = 'Transaction signing not yet supported in this version';
          break;

        default:
          error = `Method not supported: ${method}`;
      }

      // Send response back to WebView
      const js = `window._mcwBridgeResponse(${id}, ${JSON.stringify(result)}, ${JSON.stringify(error)});`;
      webViewRef.current?.injectJavaScript(js);
    } catch {
      // Ignore non-JSON messages
    }
  };

  return (
    <View style={styles.container}>
      {/* URL bar */}
      <View style={styles.urlBar}>
        {connected && (
          <View style={styles.connectedBadge}>
            <Text style={styles.connectedText}>●</Text>
          </View>
        )}
        <TextInput
          style={styles.urlInput}
          value={inputUrl}
          onChangeText={setInputUrl}
          onSubmitEditing={handleNavigate}
          placeholder="Enter DApp URL..."
          placeholderTextColor="#6B7280"
          autoCapitalize="none"
          autoCorrect={false}
          returnKeyType="go"
        />
        <TouchableOpacity style={styles.goBtn} onPress={handleNavigate}>
          <Text style={styles.goBtnText}>Go</Text>
        </TouchableOpacity>
      </View>

      {/* WebView */}
      <WebView
        ref={webViewRef}
        source={{uri: url}}
        style={styles.webview}
        injectedJavaScriptBeforeContentLoaded={EIP1193_BRIDGE}
        onMessage={handleMessage}
        onLoadStart={() => setLoading(true)}
        onLoadEnd={() => setLoading(false)}
        javaScriptEnabled
        domStorageEnabled
        allowsInlineMediaPlayback
        mediaPlaybackRequiresUserAction={false}
      />

      {loading && (
        <ActivityIndicator
          style={styles.loadingOverlay}
          color="#3B82F6"
          size="large"
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#111827'},
  urlBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1F2937',
    paddingHorizontal: 12,
    paddingVertical: 8,
    gap: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#374151',
  },
  connectedBadge: {marginRight: 4},
  connectedText: {color: '#10B981', fontSize: 12},
  urlInput: {
    flex: 1,
    backgroundColor: '#374151',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    color: '#F9FAFB',
    fontSize: 13,
  },
  goBtn: {backgroundColor: '#3B82F6', paddingHorizontal: 14, paddingVertical: 8, borderRadius: 8},
  goBtnText: {color: '#fff', fontWeight: '600', fontSize: 13},
  webview: {flex: 1},
  loadingOverlay: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    marginLeft: -20,
    marginTop: -20,
  },
});
