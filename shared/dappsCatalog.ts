/**
 * Shared DApp catalog — единый источник данных для веб и мобильного приложения.
 * Не содержит платформенных импортов (без изображений, без DOM, без React Native).
 */

export type DAppEntry = {
  id: string
  title: string
  menuTitle?: string
  description: string
  iconSymbol: string
  routeUrl: string
  supportedChains: string[]
  walletBridge: 'none' | 'eip1193'
}

export const DAPPS_CATALOG: DAppEntry[] = [
  {
    id: 'onout-dex',
    title: 'Onout DEX',
    menuTitle: 'Onout DEX',
    description: 'Onout DEX opened inside wallet container for seamless swap flow.',
    iconSymbol: 'OD',
    routeUrl: 'https://appsource.github.io/dex/?walletBridge=swaponline',
    supportedChains: ['Ethereum', 'BSC', 'Polygon'],
    walletBridge: 'eip1193',
  },
  {
    id: 'polyfactory',
    title: 'PolyFactory',
    menuTitle: 'PolyFactory',
    description: 'Prediction markets on BSC Testnet. Trade YES/NO outcomes with CLOB orderbook.',
    iconSymbol: 'PF',
    routeUrl: 'https://appsource.github.io/polyfactory/?walletBridge=swaponline',
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
    routeUrl: 'https://appsource.github.io/launchpad/?walletBridge=swaponline',
    supportedChains: ['Ethereum', 'BSC', 'Polygon'],
    walletBridge: 'eip1193',
  },
  {
    id: 'crypto-lottery',
    title: 'Crypto Lottery',
    menuTitle: 'Lottery',
    description: 'On-chain lottery with provably fair draws. Buy tickets, win crypto prizes.',
    iconSymbol: 'CL',
    routeUrl: 'https://appsource.github.io/lottery/?walletBridge=swaponline',
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
    routeUrl: 'https://appsource.github.io/lenda/?walletBridge=swaponline',
    supportedChains: ['BSC'],
    walletBridge: 'eip1193',
  },
]

export const getDAppById = (id: string): DAppEntry | undefined =>
  DAPPS_CATALOG.find((app) => app.id === id)
