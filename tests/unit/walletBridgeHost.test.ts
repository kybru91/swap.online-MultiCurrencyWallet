/**
 * Unit tests for MCW walletBridge.ts (HOST-SIDE bridge)
 *
 * Tests the createWalletAppsBridge function that runs on the wallet host page
 * and handles EIP-1193 requests from DApp iframes via postMessage.
 *
 * Key focus: internal provider must handle chain-related methods
 * (eth_chainId, wallet_switchEthereumChain, net_version) without MetaMask.
 */

// Mock helpers/metamask before importing the module
jest.mock('helpers', () => ({
  metamask: {
    isConnected: jest.fn().mockReturnValue(false),
    getAddress: jest.fn().mockReturnValue(null),
    getWeb3: jest.fn().mockReturnValue(null),
    web3connect: null,
  },
}))

import { createWalletAppsBridge } from 'pages/Apps/walletBridge'

const BRIDGE_SOURCE_HOST = 'swap.wallet.apps.bridge.host'
const BRIDGE_SOURCE_CLIENT = 'swap.wallet.apps.bridge.client'

type PostedMessage = { data: unknown; targetOrigin: string }

/**
 * Creates a mock iframe element with a mock contentWindow
 */
function createMockIframe(): {
  iframe: HTMLIFrameElement
  postedMessages: PostedMessage[]
} {
  const postedMessages: PostedMessage[] = []

  const mockContentWindow = {
    postMessage: jest.fn((data: unknown, targetOrigin: string) => {
      postedMessages.push({ data, targetOrigin })
    }),
  }

  const iframe = {
    contentWindow: mockContentWindow,
  } as unknown as HTMLIFrameElement

  return { iframe, postedMessages }
}

/**
 * Simulates a postMessage from the bridge client (iframe) to the host window.
 */
function sendClientMessage(
  type: string,
  payload: Record<string, unknown>,
  iframeWindow: MessageEventSource,
  origin: string
) {
  const event = new MessageEvent('message', {
    data: {
      source: BRIDGE_SOURCE_CLIENT,
      type,
      payload,
    },
    source: iframeWindow,
    origin,
  })
  window.dispatchEvent(event)
}

/**
 * Wait for a response message to be posted back to the iframe
 */
function waitForResponse(
  postedMessages: PostedMessage[],
  requestId: string,
  timeout = 2000
): Promise<unknown> {
  return new Promise((resolve, reject) => {
    const startLen = postedMessages.length
    const check = () => {
      for (let i = startLen; i < postedMessages.length; i++) {
        const msg = postedMessages[i].data as Record<string, unknown>
        if (
          msg?.source === BRIDGE_SOURCE_HOST &&
          msg?.type === 'WALLET_APPS_BRIDGE_RESPONSE' &&
          (msg?.payload as Record<string, unknown>)?.requestId === requestId
        ) {
          resolve(msg.payload)
          return
        }
      }
    }

    const interval = setInterval(() => {
      check()
    }, 50)

    setTimeout(() => {
      clearInterval(interval)
      check()
      for (let i = 0; i < postedMessages.length; i++) {
        const msg = postedMessages[i].data as Record<string, unknown>
        if (
          msg?.source === BRIDGE_SOURCE_HOST &&
          msg?.type === 'WALLET_APPS_BRIDGE_RESPONSE' &&
          (msg?.payload as Record<string, unknown>)?.requestId === requestId
        ) {
          resolve(msg.payload)
          return
        }
      }
      reject(new Error(`Timeout waiting for response to ${requestId}`))
    }, timeout)
  })
}

function waitForEvent(
  postedMessages: PostedMessage[],
  eventName: string,
  startIndex: number,
  timeout = 2000
): Promise<unknown> {
  return new Promise((resolve, reject) => {
    const check = (): boolean => {
      for (let i = startIndex; i < postedMessages.length; i++) {
        const msg = postedMessages[i].data as Record<string, unknown>
        if (
          msg?.source === BRIDGE_SOURCE_HOST &&
          msg?.type === 'WALLET_APPS_BRIDGE_EVENT' &&
          (msg?.payload as Record<string, unknown>)?.eventName === eventName
        ) {
          resolve(msg.payload)
          return true
        }
      }
      return false
    }

    if (check()) return

    const interval = setInterval(() => {
      if (check()) clearInterval(interval)
    }, 50)

    setTimeout(() => {
      clearInterval(interval)
      reject(new Error(`Timeout waiting for event ${eventName}`))
    }, timeout)
  })
}

type WalletBridge = {
  destroy: () => void
  sendReady: () => void
  isClientConnected: () => boolean
}

describe('WalletAppsBridge Host (createWalletAppsBridge)', () => {
  const TEST_ORIGIN = 'https://appsource.github.io'
  const TEST_APP_URL = 'https://appsource.github.io/predict/?walletBridge=swaponline'
  const TEST_ADDRESS = '0x1234567890abcdef1234567890abcdef12345678'

  let bridge: WalletBridge | null
  let iframe: HTMLIFrameElement
  let postedMessages: PostedMessage[]
  let mockContentWindow: MessageEventSource

  beforeEach(() => {
    // Clear window.ethereum to ensure no external provider
    delete (window as Record<string, unknown>).ethereum

    const mock = createMockIframe()
    iframe = mock.iframe
    postedMessages = mock.postedMessages
    mockContentWindow = iframe.contentWindow as unknown as MessageEventSource

    bridge = createWalletAppsBridge({
      iframe,
      appUrl: TEST_APP_URL,
      internalWallet: {
        address: TEST_ADDRESS,
        currency: 'ETH',
      },
    })
  })

  afterEach(() => {
    if (bridge) {
      bridge.destroy()
    }
  })

  describe('Handshake', () => {
    it('responds to HELLO with READY containing provider metadata', async () => {
      sendClientMessage(
        'WALLET_APPS_BRIDGE_HELLO',
        { version: '1.0.0', ua: 'test' },
        mockContentWindow,
        TEST_ORIGIN
      )

      // Wait a tick for async fetchProviderMeta
      await new Promise((r) => {
        setTimeout(r, 200)
      })

      const readyMsg = postedMessages.find(
        (m) =>
          (m.data as Record<string, unknown>)?.source === BRIDGE_SOURCE_HOST &&
          (m.data as Record<string, unknown>)?.type === 'WALLET_APPS_BRIDGE_READY'
      )

      expect(readyMsg).toBeDefined()
      const payload = (readyMsg!.data as Record<string, unknown>).payload as Record<string, unknown>
      expect(payload.providerAvailable).toBe(true)
      expect(payload.accounts).toContain(TEST_ADDRESS)
    })

    it('sets clientConnected to true after HELLO', () => {
      sendClientMessage(
        'WALLET_APPS_BRIDGE_HELLO',
        { version: '1.0.0', ua: 'test' },
        mockContentWindow,
        TEST_ORIGIN
      )

      expect(bridge!.isClientConnected()).toBe(true)
    })
  })

  describe('Internal Provider — eth_accounts', () => {
    it('returns internal wallet address for eth_accounts', async () => {
      const requestId = 'req-accounts-1'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        { requestId, method: 'eth_accounts', params: [] },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(postedMessages, requestId)) as Record<string, unknown>

      expect(response.error).toBeUndefined()
      expect(response.result).toEqual([TEST_ADDRESS])
    })

    it('returns internal wallet address for eth_requestAccounts', async () => {
      const requestId = 'req-reqaccounts-1'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        { requestId, method: 'eth_requestAccounts', params: [] },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(postedMessages, requestId)) as Record<string, unknown>

      expect(response.error).toBeUndefined()
      expect(response.result).toEqual([TEST_ADDRESS])
    })
  })

  describe('Internal Provider — eth_chainId', () => {
    it('returns default chain ID (0x38 BSC) without external provider', async () => {
      const requestId = 'req-chainId-1'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        { requestId, method: 'eth_chainId', params: [] },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(postedMessages, requestId)) as Record<string, unknown>

      expect(response.error).toBeUndefined()
      expect(response.result).toBe('0x38')
    })
  })

  describe('Internal Provider — net_version', () => {
    it('returns network version string without external provider', async () => {
      const requestId = 'req-netver-1'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        { requestId, method: 'net_version', params: [] },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(postedMessages, requestId)) as Record<string, unknown>

      expect(response.error).toBeUndefined()
      expect(response.result).toBe('56') // BSC network version
    })
  })

  describe('Internal Provider — wallet_switchEthereumChain', () => {
    it('accepts wallet_switchEthereumChain for known chain (BSC 0x38)', async () => {
      const requestId = 'req-switch-bsc'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        {
          requestId,
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: '0x38' }],
        },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(postedMessages, requestId)) as Record<string, unknown>

      expect(response.error).toBeUndefined()
      expect(response.result).toBeNull()
    })

    it('accepts wallet_switchEthereumChain for Polygon (0x89)', async () => {
      const requestId = 'req-switch-polygon'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        {
          requestId,
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: '0x89' }],
        },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(postedMessages, requestId)) as Record<string, unknown>

      expect(response.error).toBeUndefined()
      expect(response.result).toBeNull()
    })

    it('emits chainChanged event after switch', async () => {
      const startIdx = postedMessages.length
      const requestId = 'req-switch-event'

      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        {
          requestId,
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: '0x89' }],
        },
        mockContentWindow,
        TEST_ORIGIN
      )

      const eventPayload = (await waitForEvent(postedMessages, 'chainChanged', startIdx)) as Record<
        string,
        unknown
      >

      expect(eventPayload.data).toBe('0x89')
    })

    it('updates eth_chainId after switching chain', async () => {
      // First switch to Polygon
      const switchId = 'req-switch-for-check'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        {
          requestId: switchId,
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: '0x89' }],
        },
        mockContentWindow,
        TEST_ORIGIN
      )
      await waitForResponse(postedMessages, switchId)

      // Then query eth_chainId — should now be 0x89
      const chainIdReq = 'req-chainId-after-switch'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        { requestId: chainIdReq, method: 'eth_chainId', params: [] },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(postedMessages, chainIdReq)) as Record<
        string,
        unknown
      >
      expect(response.result).toBe('0x89')
    })

    it('updates net_version after switching chain', async () => {
      const switchId = 'req-switch-for-netver'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        {
          requestId: switchId,
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: '0x1' }], // Ethereum mainnet
        },
        mockContentWindow,
        TEST_ORIGIN
      )
      await waitForResponse(postedMessages, switchId)

      const netVerId = 'req-netver-after-switch'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        { requestId: netVerId, method: 'net_version', params: [] },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(postedMessages, netVerId)) as Record<string, unknown>
      expect(response.result).toBe('1') // Ethereum = 1
    })

    it('accepts unknown chain IDs gracefully', async () => {
      const requestId = 'req-switch-unknown'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        {
          requestId,
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: '0xDEAD' }],
        },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(postedMessages, requestId)) as Record<string, unknown>
      expect(response.error).toBeUndefined()
      expect(response.result).toBeNull()
    })
  })

  describe('Internal Provider — wallet_addEthereumChain', () => {
    it('accepts wallet_addEthereumChain silently', async () => {
      const requestId = 'req-addchain-1'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        {
          requestId,
          method: 'wallet_addEthereumChain',
          params: [
            {
              chainId: '0x38',
              chainName: 'BSC',
              rpcUrls: ['https://bsc-dataseed.binance.org/'],
            },
          ],
        },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(postedMessages, requestId)) as Record<string, unknown>
      expect(response.error).toBeUndefined()
      expect(response.result).toBeNull()
    })
  })

  describe('Security — blocked methods', () => {
    it('blocks eth_subscribe', async () => {
      const requestId = 'req-blocked-sub'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        {
          requestId,
          method: 'eth_subscribe',
          params: ['newHeads'],
        },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(postedMessages, requestId)) as Record<string, unknown>
      expect(response.error).toBeDefined()
      expect((response.error as Record<string, unknown>).code).toBe(4200)
    })

    it('ignores messages from wrong origin', () => {
      const requestId = 'req-wrong-origin'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        { requestId, method: 'eth_accounts', params: [] },
        mockContentWindow,
        'https://evil.example.com' // Wrong origin
      )

      // Should not produce any response
      const response = postedMessages.find(
        (m) =>
          (m.data as Record<string, unknown>)?.payload &&
          ((m.data as Record<string, unknown>).payload as Record<string, unknown>)?.requestId ===
            requestId &&
          (m.data as Record<string, unknown>)?.type === 'WALLET_APPS_BRIDGE_RESPONSE'
      )
      expect(response).toBeUndefined()
    })

    it('ignores messages from wrong source window', () => {
      const requestId = 'req-wrong-source'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        { requestId, method: 'eth_accounts', params: [] },
        window, // Wrong source — should be iframe.contentWindow
        TEST_ORIGIN
      )

      const response = postedMessages.find(
        (m) =>
          (m.data as Record<string, unknown>)?.payload &&
          ((m.data as Record<string, unknown>).payload as Record<string, unknown>)?.requestId ===
            requestId &&
          (m.data as Record<string, unknown>)?.type === 'WALLET_APPS_BRIDGE_RESPONSE'
      )
      expect(response).toBeUndefined()
    })
  })

  describe('Unsupported method fallback', () => {
    it('returns error 4200 for methods not handled internally and no external provider', async () => {
      const requestId = 'req-unsupported-1'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        {
          requestId,
          method: 'eth_getBalance',
          params: [TEST_ADDRESS, 'latest'],
        },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(postedMessages, requestId)) as Record<string, unknown>
      expect(response.error).toBeDefined()
      expect((response.error as Record<string, unknown>).code).toBe(4200)
      expect((response.error as Record<string, unknown>).message).toContain('not supported')
    })
  })

  describe('Null internalWallet (ethData not yet loaded)', () => {
    let nullBridge: WalletBridge
    let nullIframe: HTMLIFrameElement
    let nullMessages: PostedMessage[]
    let nullContentWindow: MessageEventSource

    beforeEach(() => {
      const mock = createMockIframe()
      nullIframe = mock.iframe
      nullMessages = mock.postedMessages
      nullContentWindow = nullIframe.contentWindow as unknown as MessageEventSource

      nullBridge = createWalletAppsBridge({
        iframe: nullIframe,
        appUrl: TEST_APP_URL,
        internalWallet: null,
      })
    })

    afterEach(() => {
      nullBridge.destroy()
    })

    it('returns providerAvailable false when no internalWallet and no external provider', async () => {
      sendClientMessage(
        'WALLET_APPS_BRIDGE_HELLO',
        { version: '1.0.0', ua: 'test' },
        nullContentWindow,
        TEST_ORIGIN
      )

      await new Promise((r) => { setTimeout(r, 200) })

      const readyMsg = nullMessages.find(
        (m) =>
          (m.data as Record<string, unknown>)?.source === BRIDGE_SOURCE_HOST &&
          (m.data as Record<string, unknown>)?.type === 'WALLET_APPS_BRIDGE_READY'
      )

      expect(readyMsg).toBeDefined()
      const payload = (readyMsg!.data as Record<string, unknown>).payload as Record<string, unknown>
      expect(payload.providerAvailable).toBe(false)
      expect(payload.accounts).toEqual([])
    })

    it('returns error 4900 for eth_accounts when no wallet available', async () => {
      const requestId = 'req-no-wallet'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        { requestId, method: 'eth_accounts', params: [] },
        nullContentWindow,
        TEST_ORIGIN
      )

      const response = (await waitForResponse(nullMessages, requestId)) as Record<string, unknown>
      expect(response.error).toBeDefined()
      expect((response.error as Record<string, unknown>).code).toBe(4900)
    })
  })

  describe('Cleanup', () => {
    it('destroy removes message listener', () => {
      bridge!.destroy()

      const requestId = 'req-after-destroy'
      sendClientMessage(
        'WALLET_APPS_BRIDGE_REQUEST',
        { requestId, method: 'eth_accounts', params: [] },
        mockContentWindow,
        TEST_ORIGIN
      )

      const response = postedMessages.find(
        (m) =>
          (m.data as Record<string, unknown>)?.payload &&
          ((m.data as Record<string, unknown>).payload as Record<string, unknown>)?.requestId ===
            requestId &&
          (m.data as Record<string, unknown>)?.type === 'WALLET_APPS_BRIDGE_RESPONSE'
      )
      expect(response).toBeUndefined()

      // Mark as destroyed so afterEach doesn't call destroy again
      bridge = null
    })
  })
})
