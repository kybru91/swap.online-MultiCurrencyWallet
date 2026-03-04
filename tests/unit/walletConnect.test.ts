/**
 * Unit tests for wallet connection helper (metamask.ts)
 *
 * Strategy: mock @wagmi/core and lib/appkit to test the helper logic
 * without real wallet connections or relay servers.
 *
 * Mocking pattern: jest.mock factories must be self-contained (hoisted above
 * variable declarations). Access mock functions via jest.requireMock().
 */

// ---- Mocks must be declared before imports (Jest hoists these) ----

jest.mock('@wagmi/core', () => ({
  getAccount: jest.fn(),
  watchAccount: jest.fn(() => jest.fn()),
  getChainId: jest.fn(),
  disconnect: jest.fn().mockResolvedValue(undefined),
  switchChain: jest.fn().mockResolvedValue(undefined),
}))

jest.mock('lib/appkit', () => ({
  modal: {
    open: jest.fn().mockResolvedValue(undefined),
    subscribeState: jest.fn(),
  },
  wagmiConfig: {},
  wagmiAdapter: {},
}))

jest.mock('helpers/externalConfig', () => ({
  __esModule: true,
  default: {
    api: { WalletConnectProjectId: 'test-project-id-12345' },
    evmNetworks: {
      ETH: { networkVersion: 1, chainName: 'Ethereum', currency: 'ETH', rpcUrls: ['https://mainnet.infura.io/v3/test'] },
      BNB: { networkVersion: 56, chainName: 'Binance Smart Chain', currency: 'BNB', rpcUrls: ['https://bsc-dataseed.binance.org'] },
      MATIC: { networkVersion: 137, chainName: 'Polygon', currency: 'MATIC', rpcUrls: ['https://polygon-rpc.com'] },
    },
    evmNetworkVersions: [1, 56, 137],
    opts: { curEnabled: null },
  },
}))

jest.mock('redux/actions', () => ({
  __esModule: true,
  default: {
    user: {
      sign: jest.fn().mockResolvedValue(undefined),
      getBalances: jest.fn().mockResolvedValue(undefined),
    },
    modals: {
      open: jest.fn(),
      close: jest.fn(),
    },
  },
}))

jest.mock('helpers', () => ({
  cacheStorageGet: jest.fn().mockReturnValue(false),
  cacheStorageSet: jest.fn(),
  constants: {},
}))

jest.mock('swap.app', () => ({
  __esModule: true,
  default: { services: {} },
}))

jest.mock('helpers/web3', () => ({
  setMetamask: jest.fn(),
  setProvider: jest.fn(),
  setDefaultProvider: jest.fn(),
  getWeb3: jest.fn(),
}))

jest.mock('web3', () => jest.fn().mockImplementation(() => ({
  eth: { getAccounts: jest.fn().mockResolvedValue(['0xTestAccount0000000000000000000000000001']) },
})))

jest.mock('common/coins/getCoinInfo', () => ({
  __esModule: true,
  default: jest.fn((currency: string) => ({ blockchain: currency.toUpperCase() })),
}))

jest.mock('swap.app/constants/COINS', () => ({
  COIN_DATA: {
    BTC: { model: 'UTXO' },
    ETH: { model: 'AB' },
  },
  COIN_MODEL: { UTXO: 'UTXO', AB: 'AB' },
}))

// ---- Imports (after mocks) ----

import metamask from 'helpers/metamask'

// ---- Helpers ----

const connectedAccount = {
  address: '0xTestAccount0000000000000000000000000001' as `0x${string}`,
  isConnected: true,
  chainId: 1,
  connector: { name: 'MetaMask', id: 'metaMask', type: 'injected' },
  status: 'connected' as const,
}

const disconnectedAccount = {
  address: undefined,
  isConnected: false,
  chainId: undefined,
  connector: undefined,
  status: 'disconnected' as const,
}

function getWagmiMocks() {
  const wagmiCore = jest.requireMock('@wagmi/core')
  return {
    getAccount: wagmiCore.getAccount as jest.Mock,
    watchAccount: wagmiCore.watchAccount as jest.Mock,
    getChainId: wagmiCore.getChainId as jest.Mock,
    disconnect: wagmiCore.disconnect as jest.Mock,
    switchChain: wagmiCore.switchChain as jest.Mock,
  }
}

function getAppKitMocks() {
  const lib = jest.requireMock('lib/appkit')
  return {
    modalOpen: lib.modal.open as jest.Mock,
    modalSubscribeState: lib.modal.subscribeState as jest.Mock,
  }
}

// ---- Tests ----

describe('metamask.ts — isConnected', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    const m = getWagmiMocks()
    m.getAccount.mockReturnValue({ ...connectedAccount })
    m.getChainId.mockReturnValue(1)
    m.watchAccount.mockReturnValue(jest.fn())
  })

  it('returns true when wagmi account is connected', () => {
    getWagmiMocks().getAccount.mockReturnValueOnce({ ...connectedAccount, isConnected: true })
    expect(metamask.isConnected()).toBe(true)
  })

  it('returns false when wagmi account is disconnected', () => {
    getWagmiMocks().getAccount.mockReturnValueOnce({ ...disconnectedAccount })
    expect(metamask.isConnected()).toBe(false)
  })
})

describe('metamask.ts — getAddress', () => {
  beforeEach(() => jest.clearAllMocks())

  it('returns address when connected', () => {
    getWagmiMocks().getAccount.mockReturnValueOnce({ ...connectedAccount })
    expect(metamask.getAddress()).toBe('0xTestAccount0000000000000000000000000001')
  })

  it('returns empty string when not connected', () => {
    getWagmiMocks().getAccount.mockReturnValueOnce({ ...disconnectedAccount })
    expect(metamask.getAddress()).toBe('')
  })
})

describe('metamask.ts — getChainId', () => {
  beforeEach(() => jest.clearAllMocks())

  it('returns current chain ID', () => {
    getWagmiMocks().getChainId.mockReturnValueOnce(1)
    expect(metamask.getChainId()).toBe(1)
  })

  it('returns 56 for BSC chain', () => {
    getWagmiMocks().getChainId.mockReturnValueOnce(56)
    expect(metamask.getChainId()).toBe(56)
  })
})

describe('metamask.ts — isAvailableNetwork', () => {
  beforeEach(() => jest.clearAllMocks())

  it('returns true for Ethereum mainnet (chainId 1)', () => {
    getWagmiMocks().getChainId.mockReturnValue(1)
    expect(metamask.isAvailableNetwork()).toBe(true)
  })

  it('returns true for BSC (chainId 56)', () => {
    getWagmiMocks().getChainId.mockReturnValue(56)
    expect(metamask.isAvailableNetwork()).toBe(true)
  })

  it('returns true for Polygon (chainId 137)', () => {
    getWagmiMocks().getChainId.mockReturnValue(137)
    expect(metamask.isAvailableNetwork()).toBe(true)
  })

  it('returns false for unknown chain (chainId 999)', () => {
    getWagmiMocks().getChainId.mockReturnValue(999)
    expect(metamask.isAvailableNetwork()).toBe(false)
  })
})

describe('metamask.ts — switchNetwork', () => {
  beforeEach(() => jest.clearAllMocks())

  it('calls switchChain with correct chainId for ETH', async () => {
    const result = await metamask.switchNetwork('ETH')
    expect(getWagmiMocks().switchChain).toHaveBeenCalledWith({}, { chainId: 1 })
    expect(result).toBe(true)
  })

  it('calls switchChain with correct chainId for BNB', async () => {
    const result = await metamask.switchNetwork('BNB')
    expect(getWagmiMocks().switchChain).toHaveBeenCalledWith({}, { chainId: 56 })
    expect(result).toBe(true)
  })

  it('returns false for unknown currency', async () => {
    const result = await metamask.switchNetwork('UNKNOWN_CHAIN')
    expect(getWagmiMocks().switchChain).not.toHaveBeenCalled()
    expect(result).toBe(false)
  })

  it('returns false and logs error when switchChain throws', async () => {
    getWagmiMocks().switchChain.mockRejectedValueOnce(new Error('User rejected network switch'))
    const consoleSpy = jest.spyOn(console, 'error').mockImplementation()

    const result = await metamask.switchNetwork('ETH')

    expect(result).toBe(false)
    expect(consoleSpy).toHaveBeenCalledWith('switchNetwork error:', expect.any(Error))
    consoleSpy.mockRestore()
  })
})

describe('metamask.ts — connect', () => {
  beforeEach(() => jest.clearAllMocks())

  it('opens AppKit modal', async () => {
    // watchAccount immediately triggers connection
    getWagmiMocks().watchAccount.mockImplementationOnce((_: any, { onChange }: any) => {
      setTimeout(() => onChange({ ...connectedAccount }), 0)
      return jest.fn()
    })
    getAppKitMocks().modalSubscribeState.mockImplementation(() => {})

    const result = await metamask.connect()

    expect(getAppKitMocks().modalOpen).toHaveBeenCalled()
    expect(result).toBe(true)
  })

  it('resolves false when modal closes without connection', async () => {
    getWagmiMocks().watchAccount.mockImplementationOnce(() => jest.fn())
    getWagmiMocks().getAccount.mockReturnValue({ ...disconnectedAccount })

    // Simulate modal closing with no connection
    getAppKitMocks().modalSubscribeState.mockImplementationOnce((cb: Function) => {
      setTimeout(() => cb({ open: false }), 0)
    })

    const result = await metamask.connect()
    expect(result).toBe(false)
  })
})

describe('metamask.ts — disconnect', () => {
  beforeEach(() => jest.clearAllMocks())

  it('calls wagmi disconnect when connected', async () => {
    getWagmiMocks().getAccount.mockReturnValueOnce({ ...connectedAccount, isConnected: true })

    await metamask.disconnect()

    expect(getWagmiMocks().disconnect).toHaveBeenCalled()
  })

  it('does not call disconnect when already disconnected', async () => {
    getWagmiMocks().getAccount.mockReturnValueOnce({ ...disconnectedAccount })

    await metamask.disconnect()

    expect(getWagmiMocks().disconnect).not.toHaveBeenCalled()
  })
})

describe('metamask.ts — isEnabled', () => {
  it('always returns true', () => {
    expect(metamask.isEnabled()).toBe(true)
  })
})

describe('metamask.ts — web3connect compat stubs', () => {
  beforeEach(() => jest.clearAllMocks())

  it('getInjectedType returns METAMASK when connected', () => {
    getWagmiMocks().getAccount.mockReturnValueOnce({ ...connectedAccount, isConnected: true })
    expect(metamask.web3connect.getInjectedType()).toBe('METAMASK')
  })

  it('getInjectedType returns NONE when not connected', () => {
    getWagmiMocks().getAccount.mockReturnValueOnce({ ...disconnectedAccount })
    expect(metamask.web3connect.getInjectedType()).toBe('NONE')
  })

  it('getInjectedTitle returns "Browser Wallet"', () => {
    expect(metamask.web3connect.getInjectedTitle()).toBe('Browser Wallet')
  })

  it('isConnected mirrors wagmi state — true', () => {
    getWagmiMocks().getAccount.mockReturnValueOnce({ ...connectedAccount, isConnected: true })
    expect(metamask.web3connect.isConnected()).toBe(true)
  })

  it('isConnected mirrors wagmi state — false', () => {
    getWagmiMocks().getAccount.mockReturnValueOnce({ ...disconnectedAccount })
    expect(metamask.web3connect.isConnected()).toBe(false)
  })

  it('getProviderType returns connector name', () => {
    getWagmiMocks().getAccount.mockReturnValueOnce({
      ...connectedAccount,
      connector: { name: 'MetaMask', id: 'metaMask', type: 'injected' },
    })
    expect(metamask.web3connect.getProviderType()).toBe('MetaMask')
  })

  it('getProviderType returns "Unknown" when no connector', () => {
    getWagmiMocks().getAccount.mockReturnValueOnce({ ...disconnectedAccount, connector: undefined })
    expect(metamask.web3connect.getProviderType()).toBe('Unknown')
  })

  it('getChainId returns hex chain id', () => {
    getWagmiMocks().getChainId.mockReturnValueOnce(1)
    expect(metamask.web3connect.getChainId()).toBe('0x1')
  })

  it('isCorrectNetwork delegates to isAvailableNetwork — true', () => {
    getWagmiMocks().getChainId.mockReturnValue(1)
    expect(metamask.web3connect.isCorrectNetwork()).toBe(true)
  })

  it('isCorrectNetwork delegates to isAvailableNetwork — false', () => {
    getWagmiMocks().getChainId.mockReturnValue(9999)
    expect(metamask.web3connect.isCorrectNetwork()).toBe(false)
  })
})

describe('metamask.ts — multi-chain support', () => {
  beforeEach(() => jest.clearAllMocks())

  it('supports Ethereum mainnet (chainId 1)', () => {
    getWagmiMocks().getChainId.mockReturnValue(1)
    expect(metamask.isAvailableNetwork()).toBe(true)
  })

  it('supports BSC (chainId 56)', () => {
    getWagmiMocks().getChainId.mockReturnValue(56)
    expect(metamask.isAvailableNetwork()).toBe(true)
  })

  it('supports Polygon (chainId 137)', () => {
    getWagmiMocks().getChainId.mockReturnValue(137)
    expect(metamask.isAvailableNetwork()).toBe(true)
  })

  it('rejects unsupported networks (chainId 42161 not in test config)', () => {
    getWagmiMocks().getChainId.mockReturnValue(42161)
    expect(metamask.isAvailableNetwork()).toBe(false)
  })
})

describe('metamask.ts — full connect/disconnect lifecycle', () => {
  beforeEach(() => jest.clearAllMocks())

  it('connect → get data → disconnect', async () => {
    const { getAccount, watchAccount, getChainId, disconnect } = getWagmiMocks()
    const { modalOpen, modalSubscribeState } = getAppKitMocks()

    // Setup: immediate connection on watchAccount
    watchAccount.mockImplementationOnce((_: any, { onChange }: any) => {
      setTimeout(() => onChange({ ...connectedAccount }), 0)
      return jest.fn()
    })
    modalSubscribeState.mockImplementation(() => {})
    getAccount.mockReturnValue({ ...connectedAccount })
    getChainId.mockReturnValue(1)

    // 1. Connect
    const connected = await metamask.connect()
    expect(connected).toBe(true)
    expect(modalOpen).toHaveBeenCalled()

    // 2. Get data
    expect(metamask.isConnected()).toBe(true)
    expect(metamask.getAddress()).toBe('0xTestAccount0000000000000000000000000001')
    expect(metamask.getChainId()).toBe(1)
    expect(metamask.isAvailableNetwork()).toBe(true)

    // 3. Disconnect
    await metamask.disconnect()
    expect(disconnect).toHaveBeenCalled()
  })
})
