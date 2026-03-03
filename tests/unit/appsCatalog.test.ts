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
    expect(isAllowedWalletAppUrl('https://evil.example.com/')).toBe(false)
  })

  it('polyfactory app exists with eip1193 bridge', () => {
    const app = getWalletAppById('polyfactory')
    expect(app).toBeDefined()
    expect(app!.walletBridge).toBe('eip1193')
    expect(app!.routeUrl).toContain('polyfactory.wpmix.net')
    expect(app!.routeUrl).toContain('walletBridge=swaponline')
  })
})
