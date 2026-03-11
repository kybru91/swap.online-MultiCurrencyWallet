import imgDex from './images/onout-dex.png'
import imgPolyfactory from './images/polyfactory.png'
import imgFarmFactory from './images/farm-factory.png'
import imgLaunchpad from './images/ido-launchpad.png'
import imgLottery from './images/crypto-lottery.png'
import imgLenda from './images/lenda.png'

import { DAPPS_CATALOG, DAppEntry } from '../../../../../shared/dappsCatalog'

export type { DAppEntry }

export type WalletApp = DAppEntry & {
  cardImage?: string
  isInternal?: boolean
}

const APP_IMAGES: Record<string, string> = {
  'onout-dex': imgDex,
  polyfactory: imgPolyfactory,
  'farm-factory': imgFarmFactory,
  'ido-launchpad': imgLaunchpad,
  'crypto-lottery': imgLottery,
  lenda: imgLenda,
}

const EXTERNAL_ALLOWED_HOSTS = new Set(
  DAPPS_CATALOG.filter((a) => a.walletBridge !== 'none').map((a) => new URL(a.routeUrl).hostname)
)

export const walletAppsCatalog: WalletApp[] = DAPPS_CATALOG.map((entry) => ({
  ...entry,
  cardImage: APP_IMAGES[entry.id],
}))

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
