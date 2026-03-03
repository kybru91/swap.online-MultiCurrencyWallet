const puppeteer = require('puppeteer')
const baseUrl = 'file:///root/MultiCurrencyWallet/build-testnet/index.html'

;(async () => {
  const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] })
  const page = await browser.newPage()
  await page.setViewport({ width: 1100, height: 1080 })
  await page.goto(baseUrl, { waitUntil: 'domcontentloaded' })

  // Set flag and go to /apps
  await page.evaluate(() => { window.SO_WalletAppsEnabled = true })
  await page.goto(baseUrl + '#/apps', { waitUntil: 'domcontentloaded' })
  await new Promise(r => setTimeout(r, 2000))

  const info = await page.evaluate(() => {
    return {
      url: window.location.href,
      bodyText: document.body.innerText.slice(0, 500),
      buttons: Array.from(document.querySelectorAll('button')).map(b => b.textContent ? b.textContent.trim() : '').filter(Boolean).slice(0, 20),
      appsGridClass: document.querySelector('[class*="appsCatalogGrid"]') ? 'found' : 'missing',
      appsPageFullClass: document.querySelector('[class*="appsPageFull"]') ? 'found' : 'missing',
    }
  })
  console.log('=== #/apps page ===')
  console.log(JSON.stringify(info, null, 2))

  // Now go to specific app
  const url0 = page.url().split('#')[0]
  await page.goto(url0 + '#/apps/onout-dex', { waitUntil: 'domcontentloaded' })
  await new Promise(r => setTimeout(r, 2000))

  const info2 = await page.evaluate(() => {
    var iframe = document.querySelector('iframe')
    return {
      url: window.location.href,
      iframeExists: !!iframe,
      iframeSrc: iframe ? iframe.src : null,
      iframeWidth: iframe ? iframe.getBoundingClientRect().width : -1,
      securityNoticeClass: document.querySelector('[class*="securityNotice"]') ? 'found' : 'missing',
      appsPageFull: document.querySelector('[class*="appsPageFull"]') ? 'found' : 'missing',
      backButton: (function() {
        var btns = Array.from(document.querySelectorAll('button'))
        var btn = btns.find(function(b) { return b.textContent && b.textContent.includes('All apps') })
        return btn ? btn.textContent.trim() : null
      })(),
      bodyTextStart: document.body.innerText.slice(0, 300),
    }
  })
  console.log('=== #/apps/onout-dex page ===')
  console.log(JSON.stringify(info2, null, 2))

  await browser.close()
})()
