import {
  defaultWalletAppId,
  getWalletAppById,
  isAllowedWalletAppUrl,
  walletAppsCatalog,
} from 'pages/Apps/appsCatalog'
import { DAPPS_CATALOG } from '../../shared/dappsCatalog'

describe('Wallet Apps Catalog', () => {
  it('uses onout-dex as default app for first approximation', () => {
    expect(defaultWalletAppId).toBe('onout-dex')
  })

  it('allows only configured external hosts in allowlist', () => {
    expect(isAllowedWalletAppUrl('https://appsource.github.io/dex/')).toBe(true)
    expect(isAllowedWalletAppUrl('https://polyfactory.wpmix.net/')).toBe(true)
    expect(isAllowedWalletAppUrl('https://appsource.github.io/farm/')).toBe(true)
    expect(isAllowedWalletAppUrl('https://launchpad.onout.org/')).toBe(true)
    expect(isAllowedWalletAppUrl('https://lottery.onout.org/')).toBe(true)
    expect(isAllowedWalletAppUrl('https://lenda.wpmix.net/')).toBe(true)
    expect(isAllowedWalletAppUrl('https://evil.example.com/')).toBe(false)
  })

  it('polyfactory app exists with eip1193 bridge', () => {
    const app = getWalletAppById('polyfactory')
    expect(app).toBeDefined()
    expect(app!.walletBridge).toBe('eip1193')
    expect(app!.routeUrl).toContain('polyfactory.wpmix.net')
    expect(app!.routeUrl).toContain('walletBridge=swaponline')
  })

  it('farm-factory app exists with eip1193 bridge', () => {
    const app = getWalletAppById('farm-factory')
    expect(app).toBeDefined()
    expect(app!.walletBridge).toBe('eip1193')
    expect(app!.routeUrl).toContain('appsource.github.io/farm')
    expect(app!.routeUrl).toContain('walletBridge=swaponline')
  })

  it('ido-launchpad app exists with eip1193 bridge', () => {
    const app = getWalletAppById('ido-launchpad')
    expect(app).toBeDefined()
    expect(app!.walletBridge).toBe('eip1193')
    expect(app!.routeUrl).toContain('launchpad.onout.org')
    expect(app!.routeUrl).toContain('walletBridge=swaponline')
  })

  it('crypto-lottery app exists with eip1193 bridge', () => {
    const app = getWalletAppById('crypto-lottery')
    expect(app).toBeDefined()
    expect(app!.walletBridge).toBe('eip1193')
    expect(app!.routeUrl).toContain('lottery.onout.org')
    expect(app!.routeUrl).toContain('walletBridge=swaponline')
  })

  it('lenda app exists with eip1193 bridge and correct URL', () => {
    const app = getWalletAppById('lenda')
    expect(app).toBeDefined()
    expect(app!.walletBridge).toBe('eip1193')
    expect(app!.routeUrl).toContain('lenda.wpmix.net')
    expect(app!.routeUrl).toContain('walletBridge=swaponline')
  })

  it('all DAPPS_CATALOG entries have walletBridge=swaponline in routeUrl', () => {
    for (const entry of DAPPS_CATALOG) {
      expect(entry.routeUrl).toContain('walletBridge=swaponline')
    }
  })

  it('walletAppsCatalog includes all entries from DAPPS_CATALOG', () => {
    expect(walletAppsCatalog.length).toBe(DAPPS_CATALOG.length)
    for (const entry of DAPPS_CATALOG) {
      const found = walletAppsCatalog.find((a) => a.id === entry.id)
      expect(found).toBeDefined()
    }
  })

  it('all routeUrls use HTTPS', () => {
    for (const entry of DAPPS_CATALOG) {
      expect(entry.routeUrl).toMatch(/^https:\/\//)
    }
  })

  it('getWalletAppById returns undefined for non-existent id', () => {
    expect(getWalletAppById('non-existent-app')).toBeUndefined()
    expect(getWalletAppById(undefined)).toBeUndefined()
  })
})
