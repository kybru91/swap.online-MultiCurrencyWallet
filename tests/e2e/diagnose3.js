const puppeteer = require('puppeteer')
const baseUrl = 'file:///root/MultiCurrencyWallet/build-testnet/index.html'

;(async () => {
  const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] })
  const page = await browser.newPage()
  await page.setViewport({ width: 1100, height: 1080 })

  // Capture console errors
  const consoleMsgs = []
  page.on('console', msg => {
    if (msg.type() === 'error' || msg.type() === 'warning') {
      consoleMsgs.push('[' + msg.type() + '] ' + msg.text())
    }
  })
  page.on('pageerror', err => consoleMsgs.push('[pageerror] ' + err.message))

  // Navigate with hash set from the start (not as a hash change)
  await page.goto(baseUrl + '#/apps', { waitUntil: 'networkidle0' })
  await new Promise(r => setTimeout(r, 3000))

  const info = await page.evaluate(() => {
    return {
      url: window.location.href,
      hash: window.location.hash,
      pathname: window.location.pathname,
      bodyText: document.body.innerText.slice(0, 400),
      hasOnoutDEX: document.body.innerText.includes('Onout DEX'),
      hasAppsHeading: document.body.innerText.includes('Wallet Apps'),
    }
  })
  console.log('=== Direct navigation to #/apps (networkidle0) ===')
  console.log(JSON.stringify(info, null, 2))
  console.log('Console messages:', consoleMsgs.slice(0, 10))

  await browser.close()
})()
