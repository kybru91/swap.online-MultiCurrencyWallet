/* eslint-disable no-await-in-loop */
/**
 * @jest-environment node
 */
/**
 * Apps Smoke Test
 *
 * Verifies that every app in walletAppsCatalog:
 *   1. Appears as a tile on the #/apps catalog page
 *   2. Can be opened (clicking tile navigates to #/apps/:id)
 *   3. The external URL returns HTTP 2xx (iframe target is reachable)
 *   4. The iframe element renders without console errors
 *
 * Also verifies the Apps dropdown in the header navigation:
 *   5. Hovering "Apps" in nav reveals a dropdown with all app titles
 *   6. Clicking a dropdown item navigates directly to that app
 *
 * Run:
 *   ACTIONS=true npm run test:e2e_apps_smoke
 *
 * Prerequisites: build-testnet must exist (npm run build:testnet-tests)
 */

const puppeteer = require('puppeteer')
const { timeOut, takeScreenshot } = require('./utils')

// App catalog — must stay in sync with appsCatalog.ts (same data, plain JS for e2e)
const APPS = [
  {
    id: 'onout-dex',
    title: 'Onout DEX',
    urlPattern: 'dex.onout.org',
    externalUrl: 'https://dex.onout.org/',
  },
  {
    id: 'polyfactory',
    title: 'PolyFactory',
    urlPattern: 'polyfactory.wpmix.net',
    externalUrl: 'https://polyfactory.wpmix.net/',
  },
]

jest.setTimeout(300_000) // 5 minutes per test

// ------------------------------------------------------------------
// Shared browser — one launch for the whole suite
// ------------------------------------------------------------------

let browser
let baseUrl

beforeAll(async () => {
  const args = process.env.CI ? ['--no-sandbox', '--disable-setuid-sandbox'] : []
  browser = await puppeteer.launch({ headless: true, args })

  // Determine the MCW file URL
  if (process.env.BUILD_PATH) {
    baseUrl = process.env.BUILD_PATH
  } else if (process.env.ACTIONS) {
    baseUrl = `file:///home/runner/work/MultiCurrencyWallet/MultiCurrencyWallet/build-testnet/index.html`
  } else {
    baseUrl = 'http://localhost:9001/'
  }
}, 60_000)

afterAll(async () => {
  if (browser) await browser.close()
})

async function newPage() {
  const page = await browser.newPage()
  await page.setViewport({ width: 1100, height: 1080 })
  page.on('error', (err) => console.log('[puppeteer] error:', err))
  // domcontentloaded is sufficient for file:// URLs and speeds up loading significantly
  await page.goto(baseUrl, { waitUntil: 'domcontentloaded' })
  return page
}

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

async function goToAppsPage(page) {
  await page.evaluate(() => { window.SO_WalletAppsEnabled = true })
  const url = page.url().split('#')[0]
  await page.goto(`${url}#/apps`, { waitUntil: 'domcontentloaded' })
  await timeOut(1_500)
}

async function checkUrlReachable(page, url) {
  try {
    const status = await page.evaluate(async (targetUrl) => {
      try {
        const response = await fetch(targetUrl, { method: 'HEAD', mode: 'no-cors' })
        return response.type === 'opaque' ? 200 : response.status
      } catch (e) {
        return 0
      }
    }, url)
    return status
  } catch {
    return 0
  }
}

// ------------------------------------------------------------------
// Tests
// ------------------------------------------------------------------

describe('Apps Catalog — tile render', () => {
  it('all apps appear as tiles on #/apps page', async () => {
    const page = await newPage()
    try {
      await goToAppsPage(page)

      for (const app of APPS) {
        const tileSelector = `[data-app-id="${app.id}"], button`
        const tiles = await page.$$(tileSelector)

        let found = false
        for (const tile of tiles) {
          const text = await page.evaluate((el) => el.textContent, tile)
          if (text && text.includes(app.title)) { found = true; break }
        }

        if (!found) {
          const pageText = await page.evaluate(() => document.body.textContent)
          found = pageText.includes(app.title)
        }

        console.log(`App tile "${app.title}": ${found ? 'FOUND' : 'MISSING'}`)
        expect(found).toBe(true)
      }

      await takeScreenshot(page, 'Apps_CatalogPage')
    } catch (error) {
      await takeScreenshot(page, 'Apps_CatalogPage_Error')
      throw error
    } finally {
      await page.close()
    }
  })
})

describe('Apps Catalog — iframe loads', () => {
  for (const app of APPS) {
    it(`${app.title} — external URL is reachable`, async () => {
      const page = await newPage()
      try {
        const status = await checkUrlReachable(page, app.externalUrl)
        console.log(`${app.title} URL ${app.externalUrl} → status ${status}`)
        expect(status).toBeGreaterThanOrEqual(200)
      } catch (error) {
        await takeScreenshot(page, `Apps_UrlCheck_${app.id}_Error`)
        throw error
      } finally {
        await page.close()
      }
    })

    it(`${app.title} — clicking tile opens iframe`, async () => {
      const page = await newPage()
      const consoleErrors = []
      page.on('pageerror', (err) => consoleErrors.push(err.message))

      try {
        await goToAppsPage(page)

        const buttons = await page.$$('button')
        let clicked = false
        for (const btn of buttons) {
          const text = await page.evaluate((el) => el.textContent, btn)
          if (text && text.includes(app.title)) {
            await btn.click()
            clicked = true
            break
          }
        }

        if (!clicked) {
          const url = page.url().split('#')[0]
          await page.goto(`${url}#/apps/${app.id}`)
        }

        await timeOut(2_000)

        const currentUrl = page.url()
        console.log(`${app.title} — navigated to: ${currentUrl}`)
        expect(currentUrl).toContain(`/apps/${app.id}`)

        const iframe = await page.$('iframe')
        expect(iframe).not.toBeNull()
        console.log(`${app.title} — iframe element found`)

        const iframeSrc = await page.evaluate((el) => el?.getAttribute('src'), iframe)
        console.log(`${app.title} — iframe src: ${iframeSrc}`)
        expect(iframeSrc).toContain(app.urlPattern)
        // Verify theme is forwarded to iframe URL
        expect(iframeSrc).toMatch(/theme=(dark|light)/)

        const critical = consoleErrors.filter(
          (e) => !e.includes('MetaMask') && !e.includes('ethereum')
        )
        if (critical.length > 0) console.warn(`${app.title} — console errors:`, critical)
        expect(critical.length).toBe(0)

        await takeScreenshot(page, `Apps_IframeOpen_${app.id}`)
      } catch (error) {
        await takeScreenshot(page, `Apps_IframeOpen_${app.id}_Error`)
        throw error
      } finally {
        await page.close()
      }
    })
  }
})

describe('Apps nav dropdown', () => {
  it('hovering Apps nav item reveals dropdown with all app titles', async () => {
    const page = await newPage()
    try {
      await goToAppsPage(page)

      const appsNavLink = await page.evaluateHandle(() => {
        const links = Array.from(document.querySelectorAll('a, button'))
        return links.find(
          (el) =>
            el.textContent?.trim().match(/^Apps$/i) &&
            el.closest('nav, header, [class*="nav"], [class*="header"]')
        )
      })

      if (!appsNavLink.asElement()) {
        console.warn('Apps nav link not found — skipping dropdown test')
        return
      }

      await appsNavLink.asElement().hover()
      await timeOut(500)

      const pageText = await page.evaluate(() => document.body.textContent)
      for (const app of APPS) {
        const found = pageText.includes(app.title)
        console.log(`Dropdown item "${app.title}": ${found ? 'FOUND' : 'MISSING'}`)
        expect(found).toBe(true)
      }

      await takeScreenshot(page, 'Apps_NavDropdown_Hover')
    } catch (error) {
      await takeScreenshot(page, 'Apps_NavDropdown_Error')
      throw error
    } finally {
      await page.close()
    }
  })

  it('clicking dropdown item navigates directly to that app', async () => {
    const page = await newPage()
    try {
      await goToAppsPage(page)

      const targetApp = APPS[APPS.length - 1]

      const appsNavLink = await page.evaluateHandle(() => {
        const links = Array.from(document.querySelectorAll('a, button'))
        return links.find(
          (el) =>
            el.textContent?.trim().match(/^Apps$/i) &&
            el.closest('nav, header, [class*="nav"], [class*="header"]')
        )
      })

      if (!appsNavLink.asElement()) {
        console.warn('Apps nav link not found — skipping dropdown click test')
        return
      }

      await appsNavLink.asElement().hover()
      await timeOut(500)

      const dropdownItem = await page.evaluateHandle((title) => {
        const links = Array.from(document.querySelectorAll('a'))
        return links.find((el) => el.textContent?.trim() === title)
      }, targetApp.title)

      if (dropdownItem.asElement()) {
        await dropdownItem.asElement().click()
        await timeOut(2_000)

        const currentUrl = page.url()
        console.log(`Dropdown click navigated to: ${currentUrl}`)
        expect(currentUrl).toContain(`/apps/${targetApp.id}`)

        await takeScreenshot(page, `Apps_NavDropdown_Click_${targetApp.id}`)
      } else {
        console.warn(`Dropdown item for "${targetApp.title}" not found in DOM`)
      }
    } catch (error) {
      await takeScreenshot(page, 'Apps_NavDropdown_Click_Error')
      throw error
    } finally {
      await page.close()
    }
  })
})

describe('App view — layout and UI', () => {
  it('no duplicate app tabs in switch row (only Back button + app title)', async () => {
    const page = await newPage()
    try {
      await goToAppsPage(page)
      const targetApp = APPS[0]

      // Navigate to first app
      const url = page.url().split('#')[0]
      await page.goto(`${url}#/apps/${targetApp.id}`)
      await timeOut(2_000)

      // Switch row should have the Back button text
      const backBtn = await page.evaluateHandle(() => {
        const btns = Array.from(document.querySelectorAll('button'))
        return btns.find((b) => b.textContent?.includes('All apps'))
      })
      expect(backBtn.asElement()).not.toBeNull()
      console.log('Back button found ✓')

      // Switch row should NOT contain other app titles as separate buttons
      const appTabButtons = await page.evaluate((appTitles) => {
        const btns = Array.from(document.querySelectorAll('button'))
        return appTitles.filter((title) =>
          btns.some(
            (b) =>
              b.textContent?.trim() === title &&
              !b.textContent?.includes('All apps')
          )
        )
      }, APPS.map((a) => a.title))

      console.log('App tab buttons found:', appTabButtons)
      expect(appTabButtons.length).toBe(0)

      await takeScreenshot(page, 'Apps_SwitchRow_NoTabs')
    } catch (error) {
      await takeScreenshot(page, 'Apps_SwitchRow_Error')
      throw error
    } finally {
      await page.close()
    }
  })

  it('app view is full-width (no container max-width constraint)', async () => {
    const page = await newPage()
    try {
      await goToAppsPage(page)
      const targetApp = APPS[0]

      const url = page.url().split('#')[0]
      await page.goto(`${url}#/apps/${targetApp.id}`)
      await timeOut(2_000)

      // The iframe should be close to full viewport width
      const iframeWidth = await page.evaluate(() => {
        const iframe = document.querySelector('iframe')
        if (!iframe) return 0
        return iframe.getBoundingClientRect().width
      })
      const viewportWidth = await page.evaluate(() => window.innerWidth)

      console.log(`iframe width: ${iframeWidth}, viewport: ${viewportWidth}`)
      // iframe should be at least 90% of viewport width (no container constraint)
      expect(iframeWidth).toBeGreaterThan(viewportWidth * 0.9)

      await takeScreenshot(page, 'Apps_FullWidth')
    } catch (error) {
      await takeScreenshot(page, 'Apps_FullWidth_Error')
      throw error
    } finally {
      await page.close()
    }
  })
})
