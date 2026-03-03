/**
 * Unit tests for WalletConnect integration (WalletConnectProviderV2 + Web3Connect)
 *
 * Strategy: mock @web3-react/walletconnect-v2, @web3-react/core, and external
 * config to test provider logic without real WalletConnect relay servers.
 *
 * References:
 * - https://github.com/DePayFi/web3-mock (web3-mock patterns)
 * - https://www.npmjs.com/package/eth-testing (eth-testing patterns)
 * - https://www.callstack.com/blog/testing-expo-web3-apps-with-wagmi-and-anvil
 */

// ---- Shared state for mocks ----

const mockActivate = jest.fn().mockResolvedValue(undefined)
const mockDeactivate = jest.fn()
const mockProviderDisconnect = jest.fn().mockResolvedValue(undefined)

const mockWCProvider: Record<string, any> = {
  accounts: ['0xTestAccount0000000000000000000000000001'],
  chainId: 1,
  connected: true,
  disconnect: mockProviderDisconnect,
  enable: jest.fn().mockResolvedValue(['0xTestAccount0000000000000000000000000001']),
  send: jest.fn(),
  request: jest.fn(),
}

// ---- Mocks must be declared before imports (Jest hoists these) ----

jest.mock('@web3-react/walletconnect-v2', () => {
  // Build the mock class inside the factory to avoid hoisting issues
  class _MockWalletConnectV2 {
    provider = mockWCProvider
    activate = mockActivate
    deactivate = mockDeactivate
    constructor(config: any) {
      ;(this as any)._config = config
    }
  }
  return { WalletConnect: _MockWalletConnectV2 }
})

jest.mock('@web3-react/core', () => ({
  initializeConnector: jest.fn((factory: Function) => {
    const actions = {
      startActivation: jest.fn().mockReturnValue(jest.fn()),
      update: jest.fn(),
      resetState: jest.fn(),
    }
    const connector = factory(actions)
    const hooks = {
      useAccount: jest.fn(),
      useChainId: jest.fn(),
      useProvider: jest.fn(),
    }
    return [connector, hooks]
  }),
}))

jest.mock('helpers/externalConfig', () => ({
  __esModule: true,
  default: {
    api: {
      WalletConnectProjectId: 'test-project-id-12345',
    },
  },
}))

jest.mock('app-config', () => ({
  __esModule: true,
  default: {
    evmNetworkVersions: [1, 56, 137],
  },
}))

jest.mock('react-device-detect', () => ({
  isMobile: false,
}))

jest.mock('web3', () => {
  return jest.fn().mockImplementation(() => ({
    eth: {
      getAccounts: jest.fn().mockResolvedValue(['0xTestAccount0000000000000000000000000001']),
    },
    isMetamask: false,
  }))
})

// ---- Imports (after mocks) ----
import WalletConnectProviderV2 from 'web3connect/providers/WalletConnectProviderV2'
import SUPPORTED_PROVIDERS from 'web3connect/providers/supported'

describe('WalletConnectProviderV2', () => {
  let provider: WalletConnectProviderV2

  const mockWeb3Connect = {
    _web3RPC: { 1: 'https://mainnet.infura.io/v3/test' },
    _web3ChainId: 1,
  }

  const defaultOptions = {
    rpc: { 1: 'https://mainnet.infura.io/v3/test' },
    chainId: 1,
    bridge: 'https://bridge.walletconnect.org',
    qrcode: true,
    pollingInterval: 12000,
  }

  beforeEach(() => {
    jest.clearAllMocks()
    mockActivate.mockResolvedValue(undefined)
    mockProviderDisconnect.mockResolvedValue(undefined)
    mockWCProvider.connected = true
    mockWCProvider.accounts = ['0xTestAccount0000000000000000000000000001']
    mockWCProvider.chainId = 1

    provider = new WalletConnectProviderV2(mockWeb3Connect, defaultOptions)
  })

  describe('constructor', () => {
    it('stores web3Connect reference', () => {
      expect(provider._web3Connect).toBe(mockWeb3Connect)
    })

    it('exposes instance on window.testWC for debugging', () => {
      expect((window as any).testWC).toBe(provider)
    })

    it('initializes with _inited = false', () => {
      expect((provider as any)._inited).toBe(false)
    })
  })

  describe('initProvider', () => {
    it('activates the connector with given chainId', async () => {
      await provider.initProvider()

      expect(mockActivate).toHaveBeenCalledWith(1)
    })

    it('sets _inited to true on success', async () => {
      await provider.initProvider()

      expect((provider as any)._inited).toBe(true)
    })

    it('does not throw on activation failure, logs to console', async () => {
      mockActivate.mockRejectedValueOnce(new Error('Connection refused'))
      const consoleSpy = jest.spyOn(console, 'log').mockImplementation()

      await provider.initProvider()

      expect(consoleSpy).toHaveBeenCalledWith('>>> fail init - reset')
      expect((provider as any)._inited).toBe(false)

      consoleSpy.mockRestore()
    })
  })

  describe('getAccount', () => {
    it('returns first account from provider', () => {
      expect(provider.getAccount()).toBe('0xTestAccount0000000000000000000000000001')
    })

    it('returns "Not connected" when provider is null', () => {
      ;(provider as any)._walletConnectV2 = { provider: null }

      expect(provider.getAccount()).toBe('Not connected')
    })

    it('returns "Not connected" when _walletConnectV2 is falsy', () => {
      ;(provider as any)._walletConnectV2 = null

      expect(provider.getAccount()).toBe('Not connected')
    })
  })

  describe('getChainId', () => {
    it('returns chainId from provider', () => {
      expect(provider.getChainId()).toBe(1)
    })

    it('returns 0 when provider is null', () => {
      ;(provider as any)._walletConnectV2 = { provider: null }

      expect(provider.getChainId()).toBe(0)
    })

    it('returns 0 when _walletConnectV2 is falsy', () => {
      ;(provider as any)._walletConnectV2 = null

      expect(provider.getChainId()).toBe(0)
    })
  })

  describe('getProvider', () => {
    it('returns the underlying WalletConnect provider', () => {
      expect(provider.getProvider()).toBe(mockWCProvider)
    })
  })

  describe('isConnected', () => {
    it('returns true when provider is connected', async () => {
      mockWCProvider.connected = true

      const result = await provider.isConnected()
      expect(result).toBeTruthy()
    })

    it('returns falsy when provider.connected is false', async () => {
      mockWCProvider.connected = false

      const result = await provider.isConnected()
      expect(result).toBeFalsy()
    })

    it('returns falsy when provider is null', async () => {
      ;(provider as any)._walletConnectV2 = { provider: null }

      const result = await provider.isConnected()
      expect(result).toBeFalsy()
    })
  })

  describe('isLocked', () => {
    it('always returns false', () => {
      expect(provider.isLocked()).toBe(false)
    })
  })

  describe('Connect', () => {
    it('returns false when not initialized', async () => {
      const result = await provider.Connect()

      expect(result).toBe(false)
      expect(mockActivate).not.toHaveBeenCalled()
    })

    it('activates and returns true when connected', async () => {
      await provider.initProvider()
      mockActivate.mockClear()

      const result = await provider.Connect()

      expect(mockActivate).toHaveBeenCalledWith(1)
      expect(result).toBe(true)
    })

    it('returns false when activation throws (user cancels)', async () => {
      await provider.initProvider()
      mockActivate.mockClear()
      mockActivate.mockRejectedValueOnce(new Error('User rejected'))

      const consoleSpy = jest.spyOn(console, 'log').mockImplementation()
      const consoleErrSpy = jest.spyOn(console, 'error').mockImplementation()

      const result = await provider.Connect()

      expect(result).toBe(false)
      expect(consoleSpy).toHaveBeenCalledWith('>>> WC - Fail connect')

      consoleSpy.mockRestore()
      consoleErrSpy.mockRestore()
    })

    it('returns false when not connected after activation', async () => {
      await provider.initProvider()
      mockActivate.mockClear()
      mockWCProvider.connected = false

      const result = await provider.Connect()

      expect(result).toBe(false)
    })
  })

  describe('Disconnect', () => {
    it('calls provider.disconnect()', async () => {
      await provider.Disconnect()

      expect(mockProviderDisconnect).toHaveBeenCalled()
    })

    it('does not throw when provider is null', async () => {
      ;(provider as any)._walletConnectV2 = { provider: null }

      await expect(provider.Disconnect()).resolves.not.toThrow()
    })

    it('does not throw when _walletConnectV2 is null', async () => {
      ;(provider as any)._walletConnectV2 = null

      await expect(provider.Disconnect()).resolves.not.toThrow()
    })
  })

  describe('on (event listener stub)', () => {
    it('does not throw when called', () => {
      expect(() => provider.on('connect', jest.fn())).not.toThrow()
    })
  })
})

describe('WalletConnect multi-chain support', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    mockActivate.mockResolvedValue(undefined)
    mockWCProvider.connected = true
  })

  it('supports Ethereum mainnet (chainId 1)', () => {
    const provider = new WalletConnectProviderV2(
      { _web3RPC: { 1: 'https://mainnet.infura.io' }, _web3ChainId: 1 },
      { chainId: 1 }
    )
    expect(provider).toBeDefined()
    expect(provider.getChainId()).toBe(1)
  })

  it('supports BSC (chainId 56)', () => {
    const provider = new WalletConnectProviderV2(
      { _web3RPC: { 56: 'https://bsc-dataseed.binance.org' }, _web3ChainId: 56 },
      { chainId: 56 }
    )
    expect(provider).toBeDefined()
  })

  it('supports Polygon (chainId 137)', () => {
    const provider = new WalletConnectProviderV2(
      { _web3RPC: { 137: 'https://polygon-rpc.com' }, _web3ChainId: 137 },
      { chainId: 137 }
    )
    expect(provider).toBeDefined()
  })
})

describe('SUPPORTED_PROVIDERS', () => {
  it('has INJECTED provider', () => {
    expect(SUPPORTED_PROVIDERS.INJECTED).toBe('INJECTED')
  })

  it('has WALLETCONNECT provider', () => {
    expect(SUPPORTED_PROVIDERS.WALLETCONNECT).toBe('WALLETCONNECT')
  })
})

describe('WalletConnect full lifecycle', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    mockActivate.mockResolvedValue(undefined)
    mockProviderDisconnect.mockResolvedValue(undefined)
    mockWCProvider.connected = true
    mockWCProvider.accounts = ['0xTestAccount0000000000000000000000000001']
    mockWCProvider.chainId = 1
  })

  it('init → connect → get data → disconnect', async () => {
    const provider = new WalletConnectProviderV2(
      { _web3RPC: { 1: 'https://mainnet.infura.io' }, _web3ChainId: 1 },
      { chainId: 1 }
    )

    // 1. Init
    await provider.initProvider()
    expect((provider as any)._inited).toBe(true)

    // 2. Connect
    mockActivate.mockClear()
    const connected = await provider.Connect()
    expect(connected).toBe(true)

    // 3. Get data
    expect(provider.getAccount()).toBe('0xTestAccount0000000000000000000000000001')
    expect(provider.getChainId()).toBe(1)
    expect(provider.getProvider()).toBe(mockWCProvider)
    expect(await provider.isConnected()).toBeTruthy()
    expect(provider.isLocked()).toBe(false)

    // 4. Disconnect
    await provider.Disconnect()
    expect(mockProviderDisconnect).toHaveBeenCalled()
  })

  it('handles reconnection after disconnect', async () => {
    const provider = new WalletConnectProviderV2(
      { _web3RPC: { 1: 'https://mainnet.infura.io' }, _web3ChainId: 1 },
      { chainId: 1 }
    )

    await provider.initProvider()

    // Connect
    mockActivate.mockClear()
    let connected = await provider.Connect()
    expect(connected).toBe(true)

    // Disconnect
    await provider.Disconnect()

    // Reconnect
    mockActivate.mockClear()
    mockWCProvider.connected = true
    connected = await provider.Connect()
    expect(connected).toBe(true)
    expect(mockActivate).toHaveBeenCalledWith(1)
  })

  it('handles chain switching scenario', async () => {
    const provider = new WalletConnectProviderV2(
      { _web3RPC: { 1: 'https://mainnet.infura.io' }, _web3ChainId: 1 },
      { chainId: 1 }
    )

    await provider.initProvider()
    await provider.Connect()

    // Simulate chain change (user switches to BSC in wallet)
    mockWCProvider.chainId = 56
    expect(provider.getChainId()).toBe(56)

    // Switch back
    mockWCProvider.chainId = 1
    expect(provider.getChainId()).toBe(1)
  })

  it('handles account change scenario', async () => {
    const provider = new WalletConnectProviderV2(
      { _web3RPC: { 1: 'https://mainnet.infura.io' }, _web3ChainId: 1 },
      { chainId: 1 }
    )

    await provider.initProvider()
    await provider.Connect()

    expect(provider.getAccount()).toBe('0xTestAccount0000000000000000000000000001')

    // Simulate account change
    mockWCProvider.accounts = ['0xNewAccount00000000000000000000000000002']
    expect(provider.getAccount()).toBe('0xNewAccount00000000000000000000000000002')
  })
})

describe('WalletConnect error scenarios', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    mockActivate.mockResolvedValue(undefined)
    mockProviderDisconnect.mockResolvedValue(undefined)
    mockWCProvider.connected = true
    mockWCProvider.accounts = ['0xTestAccount0000000000000000000000000001']
    mockWCProvider.chainId = 1
  })

  it('handles relay server timeout during init', async () => {
    mockActivate.mockRejectedValueOnce(new Error('WebSocket connection timeout'))
    const consoleSpy = jest.spyOn(console, 'log').mockImplementation()

    const provider = new WalletConnectProviderV2(
      { _web3RPC: { 1: 'https://mainnet.infura.io' }, _web3ChainId: 1 },
      { chainId: 1 }
    )

    await provider.initProvider()

    // Should not be inited
    expect((provider as any)._inited).toBe(false)
    // Connect should return false since not inited
    const result = await provider.Connect()
    expect(result).toBe(false)

    consoleSpy.mockRestore()
  })

  it('handles provider becoming unavailable mid-session', async () => {
    const provider = new WalletConnectProviderV2(
      { _web3RPC: { 1: 'https://mainnet.infura.io' }, _web3ChainId: 1 },
      { chainId: 1 }
    )

    await provider.initProvider()
    await provider.Connect()

    // Simulate provider becoming null (session expired)
    ;(provider as any)._walletConnectV2.provider = null

    expect(provider.getAccount()).toBe('Not connected')
    expect(provider.getChainId()).toBe(0)
    expect(await provider.isConnected()).toBeFalsy()
  })

  it('handles disconnect when already disconnected', async () => {
    const provider = new WalletConnectProviderV2(
      { _web3RPC: { 1: 'https://mainnet.infura.io' }, _web3ChainId: 1 },
      { chainId: 1 }
    )

    ;(provider as any)._walletConnectV2 = null

    await expect(provider.Disconnect()).resolves.not.toThrow()
  })

  it('handles UserRejectedRequestError (code 4001) during Connect', async () => {
    const provider = new WalletConnectProviderV2(
      { _web3RPC: { 1: 'https://mainnet.infura.io' }, _web3ChainId: 1 },
      { chainId: 1 }
    )

    await provider.initProvider()
    mockActivate.mockClear()

    const userRejectError = new Error('User rejected the request')
    ;(userRejectError as any).code = 4001
    mockActivate.mockRejectedValueOnce(userRejectError)

    const consoleSpy = jest.spyOn(console, 'log').mockImplementation()
    const consoleErrSpy = jest.spyOn(console, 'error').mockImplementation()

    const result = await provider.Connect()
    expect(result).toBe(false)

    consoleSpy.mockRestore()
    consoleErrSpy.mockRestore()
  })

  it('handles network error during activation', async () => {
    const provider = new WalletConnectProviderV2(
      { _web3RPC: { 1: 'https://mainnet.infura.io' }, _web3ChainId: 1 },
      { chainId: 1 }
    )

    await provider.initProvider()
    mockActivate.mockClear()
    mockActivate.mockRejectedValueOnce(new Error('Network error'))

    const consoleSpy = jest.spyOn(console, 'log').mockImplementation()
    const consoleErrSpy = jest.spyOn(console, 'error').mockImplementation()

    const result = await provider.Connect()
    expect(result).toBe(false)

    consoleSpy.mockRestore()
    consoleErrSpy.mockRestore()
  })
})
