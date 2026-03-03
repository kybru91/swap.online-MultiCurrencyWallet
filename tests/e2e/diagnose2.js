const puppeteer = require('puppeteer')
const baseUrl = 'file:///root/MultiCurrencyWallet/build-testnet/index.html'

;(async () => {
  const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] })
  const page = await browser.newPage()
  await page.setViewport({ width: 1100, height: 1080 })

  // Inject debug to trace React Router
  await page.goto(baseUrl, { waitUntil: 'networkidle0' })
  await new Promise(r => setTimeout(r, 3000))

  const info = await page.evaluate(() => {
    return {
      url: window.location.href,
      hash: window.location.hash,
      bodyText: document.body.innerText.slice(0, 200),
      hasAppsNav: document.body.innerText.includes('Apps'),
      hasWalletApps: typeof window.SO_WalletAppsEnabled,
    }
  })
  console.log('=== Initial load (no hash) ===')
  console.log(JSON.stringify(info, null, 2))

  // Now directly navigate with the hash set
  await page.goto(baseUrl + '#/apps', { waitUntil: 'networkidle0' })
  await new Promise(r => setTimeout(r, 3000))

  const info2 = await page.evaluate(() => {
    return {
      url: window.location.href,
      hash: window.location.hash,
      bodyText: document.body.innerText.slice(0, 300),
      hasOnoutDEX: document.body.innerText.includes('Onout DEX'),
      hasPolyFactory: document.body.innerText.includes('PolyFactory'),
      hasAppsHeading: document.body.innerText.includes('Wallet Apps'),
    }
  })
  console.log('=== After navigating to #/apps ===')
  console.log(JSON.stringify(info2, null, 2))

  await browser.close()
})()
