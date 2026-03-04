import {
  defaultWalletAppId,
  getWalletAppById,
  isAllowedWalletAppUrl,
  resolveWalletAppUrl,
} from 'pages/Apps/appsCatalog'

describe('Wallet Apps Catalog', () => {
  it('uses onout-dex as default app for first approximation', () => {
    expect(defaultWalletAppId).toBe('onout-dex')
  })

  it('resolves internal app route into host hash url', () => {
    const exchangeApp = getWalletAppById('swapio-exchange')

    expect(exchangeApp).toBeDefined()

    const resolvedUrl = resolveWalletAppUrl(exchangeApp!)

    expect(resolvedUrl).toBe(`${window.location.origin}${window.location.pathname}#/exchange/quick`)
    expect(isAllowedWalletAppUrl(resolvedUrl)).toBe(true)
  })

  it('allows only configured external hosts in allowlist', () => {
    expect(isAllowedWalletAppUrl('https://dex.onout.org/')).toBe(true)
    expect(isAllowedWalletAppUrl('https://polyfactory.wpmix.net/')).toBe(true)
    expect(isAllowedWalletAppUrl('https://farm.wpmix.net/')).toBe(true)
    expect(isAllowedWalletAppUrl('https://launchpad.onout.org/')).toBe(true)
    expect(isAllowedWalletAppUrl('https://lottery.onout.org/')).toBe(true)
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
    expect(app!.routeUrl).toContain('farm.wpmix.net')
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
})
