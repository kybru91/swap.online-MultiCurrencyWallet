// @ts-nocheck
import {
  getWalletNetwork,
  getAvailableNetworks,
  filterWalletsByNetwork,
} from 'helpers/networkFilter'

// Mock wallet data matching real Redux structure
const mockWallets = [
  { currency: 'BTC', fullName: 'Bitcoin', balance: 0.5, isToken: false },
  { currency: 'ETH', fullName: 'Ethereum', balance: 1.2, isToken: false },
  { currency: 'BNB', fullName: 'BNB', balance: 0.3, isToken: false },
  { currency: 'MATIC', fullName: 'Polygon', balance: 10, isToken: false },
  { currency: 'ARBETH', fullName: 'Arbitrum', balance: 0.1, isToken: false },
  { currency: 'XDAI', fullName: 'Gnosis', balance: 5, isToken: false },
  { currency: 'AVAX', fullName: 'Avalanche', balance: 2, isToken: false },
  // ERC20 token on Ethereum
  {
    currency: '{ETH}USDT',
    fullName: 'Tether',
    balance: 100,
    isToken: true,
    tokenKey: '{ETH}USDT',
    standard: 'erc20',
  },
  // BEP20 token on BSC
  {
    currency: '{BNB}USDT',
    fullName: 'Tether',
    balance: 50,
    isToken: true,
    tokenKey: '{BNB}USDT',
    standard: 'bep20',
  },
  // ERC20 token on Polygon
  {
    currency: '{MATIC}USDC',
    fullName: 'USD Coin',
    balance: 200,
    isToken: true,
    tokenKey: '{MATIC}USDC',
    standard: 'erc20matic',
  },
  // BTC multisig
  { currency: 'BTC (Multisig)', fullName: 'Bitcoin (Multisig)', balance: 0.1, isToken: false },
  {
    currency: 'BTC (SMS-protected)',
    fullName: 'Bitcoin (SMS-protected)',
    balance: 0,
    isToken: false,
  },
]

describe('getWalletNetwork', () => {
  it('returns BTC for bitcoin wallet', () => {
    expect(getWalletNetwork({ currency: 'BTC', isToken: false })).toBe('BTC')
  })

  it('returns BTC for BTC multisig variants', () => {
    expect(getWalletNetwork({ currency: 'BTC (Multisig)', isToken: false })).toBe('BTC')
    expect(getWalletNetwork({ currency: 'BTC (SMS-protected)', isToken: false })).toBe('BTC')
    expect(getWalletNetwork({ currency: 'BTC (PIN-protected)', isToken: false })).toBe('BTC')
  })

  it('returns ETH for ethereum native coin', () => {
    expect(getWalletNetwork({ currency: 'ETH', isToken: false })).toBe('ETH')
  })

  it('returns BNB for BSC native coin', () => {
    expect(getWalletNetwork({ currency: 'BNB', isToken: false })).toBe('BNB')
  })

  it('returns ETH for ERC20 token', () => {
    expect(getWalletNetwork({ currency: '{ETH}USDT', isToken: true, tokenKey: '{ETH}USDT' })).toBe(
      'ETH'
    )
  })

  it('returns BNB for BEP20 token', () => {
    expect(getWalletNetwork({ currency: '{BNB}USDT', isToken: true, tokenKey: '{BNB}USDT' })).toBe(
      'BNB'
    )
  })

  it('returns MATIC for Polygon token', () => {
    expect(
      getWalletNetwork({ currency: '{MATIC}USDC', isToken: true, tokenKey: '{MATIC}USDC' })
    ).toBe('MATIC')
  })

  it('returns GHOST for ghost coin', () => {
    expect(getWalletNetwork({ currency: 'GHOST', isToken: false })).toBe('GHOST')
  })

  it('returns NEXT for next coin', () => {
    expect(getWalletNetwork({ currency: 'NEXT', isToken: false })).toBe('NEXT')
  })

  it('returns ETH for metamask wallet entry', () => {
    expect(getWalletNetwork({ currency: 'ETH', isToken: false, isMetamask: true })).toBe('ETH')
  })
})

describe('getAvailableNetworks', () => {
  it('returns unique networks from wallet list', () => {
    const networks = getAvailableNetworks(mockWallets)
    expect(networks).toContain('BTC')
    expect(networks).toContain('ETH')
    expect(networks).toContain('BNB')
    expect(networks).toContain('MATIC')
  })

  it('does not duplicate networks (ETH native + ETH token = one ETH entry)', () => {
    const networks = getAvailableNetworks(mockWallets)
    const ethCount = networks.filter((n) => n === 'ETH').length
    expect(ethCount).toBe(1)
  })

  it('returns empty array for empty wallet list', () => {
    expect(getAvailableNetworks([])).toEqual([])
  })

  it('includes BTC only once even with multisig variants', () => {
    const networks = getAvailableNetworks(mockWallets)
    const btcCount = networks.filter((n) => n === 'BTC').length
    expect(btcCount).toBe(1)
  })
})

describe('filterWalletsByNetwork', () => {
  it('returns all wallets when network is null (All Networks)', () => {
    const result = filterWalletsByNetwork(mockWallets, null)
    expect(result).toHaveLength(mockWallets.length)
  })

  it('filters BTC wallets (includes multisig variants)', () => {
    const result = filterWalletsByNetwork(mockWallets, 'BTC')
    expect(result.length).toBe(3) // BTC + BTC (Multisig) + BTC (SMS-protected)
    result.forEach((w) => {
      expect(getWalletNetwork(w)).toBe('BTC')
    })
  })

  it('filters ETH wallets (native + ERC20 tokens)', () => {
    const result = filterWalletsByNetwork(mockWallets, 'ETH')
    expect(result.length).toBe(2) // ETH native + {ETH}USDT
    expect(result.map((w) => w.currency)).toContain('ETH')
    expect(result.map((w) => w.currency)).toContain('{ETH}USDT')
  })

  it('filters BNB wallets (native + BEP20 tokens)', () => {
    const result = filterWalletsByNetwork(mockWallets, 'BNB')
    expect(result.length).toBe(2) // BNB native + {BNB}USDT
  })

  it('filters MATIC wallets (native + Polygon tokens)', () => {
    const result = filterWalletsByNetwork(mockWallets, 'MATIC')
    expect(result.length).toBe(2) // MATIC native + {MATIC}USDC
  })

  it('returns empty array for network with no wallets', () => {
    const result = filterWalletsByNetwork(mockWallets, 'FTM')
    expect(result).toEqual([])
  })
})
