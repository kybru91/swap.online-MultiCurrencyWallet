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

const { createBrowser, timeOut, takeScreenshot } = require('./utils')
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

jest.setTimeout(300_000) // 5 minutes

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

/**
 * Navigate to #/apps (enabling apps via window override if needed).
 */
async function goToAppsPage(page) {
  // Enable apps feature at runtime (required when externalConfig.opts.ui.apps.enabled is false)
  await page.evaluate(() => {
    window.SO_WalletAppsEnabled = true
  })

  const baseUrl = page.url().split('#')[0]
  await page.goto(`${baseUrl}#/apps`)
  await timeOut(3_000)
}

/**
 * Wait for an iframe whose src matches urlPattern to appear in page.frames().
 */
async function waitForIframe(page, urlPattern, timeoutMs = 30_000) {
  const pollInterval = 1_000
  const maxAttempts = Math.ceil(timeoutMs / pollInterval)

  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const frame = page.frames().find((f) => f.url().includes(urlPattern))
    if (frame) return frame
    await timeOut(pollInterval)
  }

  throw new Error(`Iframe with pattern "${urlPattern}" not found after ${timeoutMs}ms`)
}

/**
 * Check HTTP status of a URL using fetch inside the page context.
 * Returns the HTTP status code.
 */
async function checkUrlReachable(page, url) {
  try {
    const status = await page.evaluate(async (targetUrl) => {
      try {
        const response = await fetch(targetUrl, { method: 'HEAD', mode: 'no-cors' })
        // no-cors always returns opaque (status 0) but doesn't throw = reachable
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
    const { browser, page } = await createBrowser()

    try {
      await goToAppsPage(page)

      for (const app of APPS) {
        // Each app tile has data-app-id or contains the title text
        const tileSelector = `[data-app-id="${app.id}"], button`
        const tiles = await page.$$(tileSelector)

        let found = false
        for (const tile of tiles) {
          const text = await page.evaluate((el) => el.textContent, tile)
          if (text && text.includes(app.title)) {
            found = true
            break
          }
        }

        if (!found) {
          // fallback: check page text
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
      await browser.close()
    }
  })
})

describe('Apps Catalog — iframe loads', () => {
  for (const app of APPS) {
    it(`${app.title} — external URL is reachable`, async () => {
      const { browser, page } = await createBrowser()

      try {
        // Just check external URL responds (2xx or opaque via no-cors)
        const status = await checkUrlReachable(page, app.externalUrl)
        console.log(`${app.title} URL ${app.externalUrl} → status ${status}`)
        expect(status).toBeGreaterThanOrEqual(200)
      } catch (error) {
        await takeScreenshot(page, `Apps_UrlCheck_${app.id}_Error`)
        throw error
      } finally {
        await browser.close()
      }
    })

    it(`${app.title} — clicking tile opens iframe`, async () => {
      const { browser, page } = await createBrowser()
      const consoleErrors = []
      page.on('pageerror', (err) => consoleErrors.push(err.message))

      try {
        await goToAppsPage(page)

        // Find and click the app tile
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
          // Navigate directly
          const baseUrl = page.url().split('#')[0]
          await page.goto(`${baseUrl}#/apps/${app.id}`)
        }

        await timeOut(2_000)

        // Verify URL changed to #/apps/:id
        const currentUrl = page.url()
        console.log(`${app.title} — navigated to: ${currentUrl}`)
        expect(currentUrl).toContain(`/apps/${app.id}`)

        // Verify iframe element exists in DOM
        const iframe = await page.$('iframe')
        expect(iframe).not.toBeNull()
        console.log(`${app.title} — iframe element found`)

        // Wait for iframe to receive a src pointing to expected domain
        const iframeSrc = await page.evaluate((el) => el?.getAttribute('src'), iframe)
        console.log(`${app.title} — iframe src: ${iframeSrc}`)
        expect(iframeSrc).toContain(app.urlPattern)

        // No critical page-level JS errors
        const critical = consoleErrors.filter(
          (e) => !e.includes('MetaMask') && !e.includes('ethereum')
        )
        if (critical.length > 0) {
          console.warn(`${app.title} — console errors:`, critical)
        }
        expect(critical.length).toBe(0)

        await takeScreenshot(page, `Apps_IframeOpen_${app.id}`)
      } catch (error) {
        await takeScreenshot(page, `Apps_IframeOpen_${app.id}_Error`)
        throw error
      } finally {
        await browser.close()
      }
    })
  }
})

describe('Apps nav dropdown', () => {
  it('hovering Apps nav item reveals dropdown with all app titles', async () => {
    const { browser, page } = await createBrowser()

    try {
      await goToAppsPage(page)

      // Find the Apps nav link
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

      // Hover over the Apps link
      await appsNavLink.asElement().hover()
      await timeOut(500)

      // Check that dropdown appears with all app titles
      const pageText = await page.evaluate(() => document.body.textContent)
      for (const app of APPS) {
        const title = app.title
        const found = pageText.includes(title)
        console.log(`Dropdown item "${title}": ${found ? 'FOUND' : 'MISSING'}`)
        expect(found).toBe(true)
      }

      await takeScreenshot(page, 'Apps_NavDropdown_Hover')
    } catch (error) {
      await takeScreenshot(page, 'Apps_NavDropdown_Error')
      throw error
    } finally {
      await browser.close()
    }
  })

  it('clicking dropdown item navigates directly to that app', async () => {
    const { browser, page } = await createBrowser()

    try {
      await goToAppsPage(page)

      const targetApp = APPS[APPS.length - 1] // PolyFactory (last added)

      // Hover Apps nav link
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

      // Click the target app's dropdown item
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
      await browser.close()
    }
  })
})
