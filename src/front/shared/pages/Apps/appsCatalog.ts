import imgDex from './images/onout-dex.png'
import imgPolyfactory from './images/polyfactory.png'
import imgFarmFactory from './images/farm-factory.png'
import imgLaunchpad from './images/ido-launchpad.png'
import imgLottery from './images/crypto-lottery.png'
import imgLenda from './images/lenda.png'

export type WalletApp = {
  id: string
  title: string
  menuTitle?: string
  description: string
  iconSymbol?: string
  cardImage?: string
  routeUrl: string
  supportedChains: string[]
  walletBridge?: 'none' | 'eip1193'
  isInternal?: boolean
}

const EXTERNAL_ALLOWED_HOSTS = new Set([
  'appsource.github.io',
  'dex.onout.org',
  'polyfactory.wpmix.net',
  'farm.wpmix.net',
  'launchpad.onout.org',
  'lottery.onout.org',
  'lenda.wpmix.net',
])

export const walletAppsCatalog: WalletApp[] = [
  {
    id: 'swapio-exchange',
    title: 'Swap.Online Exchange',
    menuTitle: 'Exchange App',
    description: 'Current Swap.Online exchange opened in in-wallet Apps container.',
    iconSymbol: 'SO',
    routeUrl: '/exchange/quick',
    supportedChains: ['Bitcoin', 'Ethereum', 'BSC', 'Polygon'],
    walletBridge: 'none',
    isInternal: true,
  },
  {
    id: 'onout-dex',
    title: 'Onout DEX',
    menuTitle: 'Onout DEX',
    description: 'Onout DEX opened inside wallet container for seamless swap flow.',
    iconSymbol: 'OD',
    cardImage: imgDex,
    routeUrl: 'https://dex.onout.org/?walletBridge=swaponline',
    supportedChains: ['Ethereum', 'BSC', 'Polygon'],
    walletBridge: 'eip1193',
  },
  {
    id: 'polyfactory',
    title: 'PolyFactory',
    menuTitle: 'PolyFactory',
    description: 'Prediction markets on BSC Testnet. Trade YES/NO outcomes with CLOB orderbook.',
    iconSymbol: 'PF',
    cardImage: imgPolyfactory,
    routeUrl: 'https://polyfactory.wpmix.net/?walletBridge=swaponline',
    supportedChains: ['BSC'],
    walletBridge: 'eip1193',
  },
  {
    id: 'farm-factory',
    title: 'FarmFactory',
    menuTitle: 'FarmFactory',
    description:
      'Yield farming and liquidity pools. Stake LP tokens, earn rewards on BSC and Ethereum.',
    iconSymbol: 'FF',
    cardImage: imgFarmFactory,
    routeUrl: 'https://appsource.github.io/farm/?walletBridge=swaponline',
    supportedChains: ['BSC', 'Ethereum'],
    walletBridge: 'eip1193',
  },
  {
    id: 'ido-launchpad',
    title: 'IDO Launchpad',
    menuTitle: 'Launchpad',
    description: 'Token launchpad for IDO and IEO sales. Participate in new DeFi project launches.',
    iconSymbol: 'IL',
    cardImage: imgLaunchpad,
    routeUrl: 'https://launchpad.onout.org/?walletBridge=swaponline',
    supportedChains: ['Ethereum', 'BSC', 'Polygon'],
    walletBridge: 'eip1193',
  },
  {
    id: 'crypto-lottery',
    title: 'Crypto Lottery',
    menuTitle: 'Lottery',
    description: 'On-chain lottery with provably fair draws. Buy tickets, win crypto prizes.',
    iconSymbol: 'CL',
    cardImage: imgLottery,
    routeUrl: 'https://lottery.onout.org/?walletBridge=swaponline',
    supportedChains: ['Ethereum', 'BSC'],
    walletBridge: 'eip1193',
  },
  {
    id: 'lenda',
    title: 'Lenda',
    menuTitle: 'Lenda',
    description:
      'Aave-like lending and borrowing on BSC. Deposit USDT to earn APY, borrow against BNB collateral.',
    iconSymbol: 'LD',
    cardImage: imgLenda,
    routeUrl: 'https://lenda.wpmix.net/?walletBridge=swaponline',
    supportedChains: ['BSC'],
    walletBridge: 'eip1193',
  },
]

export const defaultWalletAppId = 'onout-dex'

export const getWalletAppById = (appId?: string): WalletApp | undefined => {
  if (!appId) {
    return undefined
  }

  return walletAppsCatalog.find((app) => app.id === appId)
}

export const resolveWalletAppUrl = (
  app: WalletApp,
  currentLocation: Location = window.location
): string => {
  if (!app.isInternal) {
    return app.routeUrl
  }

  const routePath = app.routeUrl.startsWith('/') ? app.routeUrl : `/${app.routeUrl}`

  return `${currentLocation.origin}${currentLocation.pathname}#${routePath}`
}

export const isAllowedWalletAppUrl = (
  appUrl: string,
  currentLocation: Location = window.location
): boolean => {
  if (!appUrl) {
    return false
  }

  try {
    const parsedUrl = new URL(appUrl)

    if (parsedUrl.hostname === currentLocation.hostname) {
      return parsedUrl.protocol === currentLocation.protocol
    }

    return parsedUrl.protocol === 'https:' && EXTERNAL_ALLOWED_HOSTS.has(parsedUrl.hostname)
  } catch (error) {
    return false
  }
}
