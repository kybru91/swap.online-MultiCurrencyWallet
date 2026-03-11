import { DAPPS_CATALOG, getDAppById } from '../../shared/dappsCatalog'

describe('Shared DApps Catalog', () => {
  it('contains at least 6 entries', () => {
    expect(DAPPS_CATALOG.length).toBeGreaterThanOrEqual(6)
  })

  it('every entry has required fields', () => {
    for (const entry of DAPPS_CATALOG) {
      expect(entry.id).toBeTruthy()
      expect(entry.title).toBeTruthy()
      expect(entry.description).toBeTruthy()
      expect(entry.routeUrl).toBeTruthy()
      expect(entry.supportedChains.length).toBeGreaterThan(0)
      expect(['none', 'eip1193']).toContain(entry.walletBridge)
    }
  })

  it('all IDs are unique', () => {
    const ids = DAPPS_CATALOG.map((e) => e.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('all routeUrls contain walletBridge=swaponline', () => {
    for (const entry of DAPPS_CATALOG) {
      expect(entry.routeUrl).toContain('walletBridge=swaponline')
    }
  })

  it('all routeUrls are valid HTTPS URLs', () => {
    for (const entry of DAPPS_CATALOG) {
      const url = new URL(entry.routeUrl)
      expect(url.protocol).toBe('https:')
    }
  })

  it('getDAppById returns correct entry', () => {
    const lenda = getDAppById('lenda')
    expect(lenda).toBeDefined()
    expect(lenda!.title).toBe('Lenda')
    expect(lenda!.routeUrl).toContain('lenda.wpmix.net')
  })

  it('getDAppById returns undefined for unknown id', () => {
    expect(getDAppById('unknown-app')).toBeUndefined()
  })

  it('lenda entry has correct URL (not 404)', () => {
    const lenda = getDAppById('lenda')
    expect(lenda).toBeDefined()
    expect(lenda!.routeUrl).toBe('https://lenda.wpmix.net/?walletBridge=swaponline')
  })

  it('onout-dex uses appsource.github.io (not dex.onout.org)', () => {
    const dex = getDAppById('onout-dex')
    expect(dex).toBeDefined()
    expect(dex!.routeUrl).toContain('appsource.github.io/dex')
  })

  it('polyfactory uses wpmix.net (not appsource.github.io)', () => {
    const pf = getDAppById('polyfactory')
    expect(pf).toBeDefined()
    expect(pf!.routeUrl).toContain('polyfactory.wpmix.net')
  })
})
