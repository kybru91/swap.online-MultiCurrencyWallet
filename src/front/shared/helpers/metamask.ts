/**
 * metamask.ts — wallet connection helper (Reown AppKit + Wagmi v2)
 *
 * Replaces the old @web3-react based implementation.
 * Maintains the same public API surface so call-sites need minimal changes.
 *
 * Internal state comes from @wagmi/core (getAccount, watchAccount, etc.)
 * UI modal is provided by Reown AppKit (modal.open()).
 */

import { getAccount, watchAccount, getChainId, disconnect, switchChain } from '@wagmi/core'
import actions from 'redux/actions'
import { cacheStorageGet, cacheStorageSet, constants } from 'helpers'
import config from './externalConfig'
import SwapApp from 'swap.app'
import { setMetamask, setProvider, setDefaultProvider, getWeb3 as getDefaultWeb3 } from 'helpers/web3'
import { modal, wagmiConfig } from 'lib/appkit'
import getCoinInfo from 'common/coins/getCoinInfo'
import { COIN_DATA, COIN_MODEL } from 'swap.app/constants/COINS'

// ---------------------------------------------------------------------------
// Core wagmi-based state accessors
// ---------------------------------------------------------------------------

const isEnabled = () => true

const isConnected = () => getAccount(wagmiConfig).isConnected

const getAddress = (): string => getAccount(wagmiConfig).address ?? ''

const getChainIdNum = (): number => getChainId(wagmiConfig) ?? 1

const getWeb3 = () => {
  // web3 instance comes from the legacy web3 provider layer,
  // updated on account/chain change via _onWeb3Changed
  return null
}

// ---------------------------------------------------------------------------
// Network helpers
// ---------------------------------------------------------------------------

const isAvailableNetwork = (): boolean => {
  const networkVersion = getChainIdNum()
  const existsNetwork = Object.keys(config.evmNetworks).filter(
    (key) => config.evmNetworks[key].networkVersion === networkVersion,
  )
  if (existsNetwork.length) {
    if (config.opts.curEnabled && !config.opts.curEnabled[existsNetwork[0].toLowerCase()]) {
      return false
    }
  }
  return config.evmNetworkVersions.includes(networkVersion)
}

const isAvailableNetworkByCurrency = (currency: string): boolean => {
  const { blockchain } = getCoinInfo(currency)
  const ticker = currency.toUpperCase()
  const isUTXOModel = COIN_DATA[ticker]?.model === COIN_MODEL.UTXO
  if (isUTXOModel) return false

  const currencyNetworkVersion = blockchain
    ? config.evmNetworks[blockchain]?.networkVersion
    : config.evmNetworks[ticker]?.networkVersion

  return currencyNetworkVersion === getChainIdNum()
}

const switchNetwork = async (nativeCurrency: string): Promise<boolean> => {
  const networkInfo = config.evmNetworks[nativeCurrency]
  if (!networkInfo) return false
  try {
    await switchChain(wagmiConfig, { chainId: networkInfo.networkVersion })
    return true
  } catch (err) {
    console.error('switchNetwork error:', err)
    return false
  }
}

const addCurrencyNetwork = async (currency: string): Promise<boolean> => {
  // For wagmi/AppKit: chain switching is handled by the modal/connector
  return switchNetwork(currency)
}

// ---------------------------------------------------------------------------
// Internal: sync wagmi state → web3 provider + Redux + SwapApp
// ---------------------------------------------------------------------------

const _syncWalletState = async () => {
  try {
    await actions.user.sign()
    await actions.user.getBalances()
  } catch (e) {
    console.warn('_syncWalletState error', e)
  }
}

// Watch for wagmi account changes and sync to the rest of the app
let _unsubscribeAccount: (() => void) | null = null

const _startWatching = () => {
  if (_unsubscribeAccount) return

  _unsubscribeAccount = watchAccount(wagmiConfig, {
    onChange(account) {
      if (account.isConnected) {
        _syncWalletState()
      } else {
        setDefaultProvider()
      }
    },
  })
}

// Start watching immediately
_startWatching()

// ---------------------------------------------------------------------------
// Connection modal
// ---------------------------------------------------------------------------

const connect = (options: Record<string, any> = {}): Promise<boolean> =>
  new Promise((resolve) => {
    modal.open()

    const unsub = watchAccount(wagmiConfig, {
      onChange(account) {
        if (account.isConnected) {
          unsub()
          if (typeof options.onResolve === 'function') options.onResolve(true)
          resolve(true)
        }
      },
    })

    // If modal is closed without connecting, resolve false via subscription to modal state
    modal.subscribeState((state) => {
      if (!state.open) {
        const acc = getAccount(wagmiConfig)
        if (!acc.isConnected) {
          unsub()
          if (typeof options.onResolve === 'function') options.onResolve(false)
          resolve(false)
        }
      }
    })
  })

// ---------------------------------------------------------------------------
// Public API: handleConnectMetamask / handleDisconnectWallet
// ---------------------------------------------------------------------------

type MetamaskConnectParams = {
  dontRedirect?: boolean
  callback?: (connected: boolean) => void
  onResolve?: (connected: boolean) => void
}

const handleConnectMetamask = (params: MetamaskConnectParams = {}) => {
  const { callback } = params

  connect(params).then(async (connected) => {
    if (connected) {
      await actions.user.sign()
      await actions.user.getBalances()
      if (typeof callback === 'function') callback(true)
    } else {
      if (typeof callback === 'function') callback(false)
    }
  })
}

const handleDisconnectWallet = async (callback?: () => void) => {
  if (isConnected()) {
    await disconnect(wagmiConfig)
    setDefaultProvider()
    await actions.user.sign()
    await actions.user.getBalances()
    if (typeof callback === 'function') callback()
  }
}

// ---------------------------------------------------------------------------
// Legacy compat stubs (kept so existing call-sites compile)
// Includes minimal EventEmitter so call-sites that use .on/.off still work.
// ---------------------------------------------------------------------------

// Simple event emitter backed by wagmi watchAccount
type Listener = (...args: any[]) => void
const _listeners: Record<string, Listener[]> = {}

const _emit = (event: string, ...args: any[]) => {
  ;(_listeners[event] || []).forEach((fn) => fn(...args))
}

// Fire synthetic events when wagmi account changes
watchAccount(wagmiConfig, {
  onChange(account, prevAccount) {
    if (account.isConnected && !prevAccount?.isConnected) {
      _emit('connected', account)
    }
    if (!account.isConnected && prevAccount?.isConnected) {
      _emit('disconnect')
    }
    if (account.address !== prevAccount?.address) {
      _emit('accountChange', account.address)
      _emit('updated')
    }
    if (account.chainId !== prevAccount?.chainId) {
      _emit('chainChanged', account.chainId)
      _emit('updated')
    }
  },
})

const web3connect = {
  getInjectedType: (): 'NONE' | 'METAMASK' | 'UNKNOWN' => {
    const acc = getAccount(wagmiConfig)
    if (!acc.isConnected) return 'NONE'
    return 'METAMASK'
  },
  getInjectedTitle: () => 'Browser Wallet',
  getProviderTitle: () => {
    const acc = getAccount(wagmiConfig)
    return acc.connector?.name ?? 'Browser Wallet'
  },
  isInjectedEnabled: () => {
    return typeof window !== 'undefined' && !!window.ethereum
  },
  getProviderType: () => {
    const acc = getAccount(wagmiConfig)
    return acc.connector?.name ?? 'Unknown'
  },
  isConnected: () => getAccount(wagmiConfig).isConnected,
  getChainId: () => `0x${getChainIdNum().toString(16)}`,
  isCorrectNetwork: () => isAvailableNetwork(),
  // Event emitter
  on: (event: string, fn: Listener) => {
    _listeners[event] = _listeners[event] || []
    _listeners[event].push(fn)
  },
  off: (event: string, fn: Listener) => {
    _listeners[event] = (_listeners[event] || []).filter((l) => l !== fn)
  },
  removeListener: (event: string, fn: Listener) => {
    _listeners[event] = (_listeners[event] || []).filter((l) => l !== fn)
  },
  // onInit: calls callback immediately (wagmi session is already restored via reconnect())
  onInit: (callback: () => void) => {
    // wagmi session may not be hydrated synchronously; wait one tick
    setTimeout(callback, 0)
  },
}

const setWeb3connect = (_networkId: number) => {
  // No-op: network is managed by wagmi/AppKit
}

const getWeb3connect = () => web3connect

const web3connectInit = async () => {
  // No-op: wagmi handles session restoration via reconnect() in appkit.ts
}

const addWallet = () => {
  // No-op: state comes from wagmi hooks now
}

const getBalance = async () => {
  const { user: { metamaskData } } = await import('helpers/getReduxState').then(m => m.default())
    .catch(() => ({ user: { metamaskData: null } }))

  if (metamaskData) {
    const { address, currency } = metamaskData as any
    const balanceInCache = cacheStorageGet('currencyBalances', `${currency}_${address}`)

    if (balanceInCache !== false) {
      return balanceInCache
    }
  }
}

const isCorrectNetwork = () => isAvailableNetwork()

const metamaskApi = {
  connect,
  isEnabled,
  isConnected,
  getAddress,
  getChainId: getChainIdNum,
  web3connect,
  setWeb3connect,
  getWeb3connect,
  web3connectInit,
  addWallet,
  getBalance,
  getWeb3,
  isCorrectNetwork,
  isAvailableNetwork,
  isAvailableNetworkByCurrency,
  handleDisconnectWallet,
  handleConnectMetamask,
  switchNetwork,
  addCurrencyNetwork,
  disconnect: handleDisconnectWallet,
}

if (typeof window !== 'undefined') {
  ;(window as any).metamaskApi = metamaskApi
}

export default metamaskApi
