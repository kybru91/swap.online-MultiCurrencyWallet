const puppeteer = require('puppeteer')
const baseUrl = 'http://localhost:9001/'

;(async () => {
  const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] })
  const page = await browser.newPage()
  await page.setViewport({ width: 1100, height: 1080 })

  const consoleMsgs = []
  page.on('console', msg => {
    if (msg.type() === 'error') consoleMsgs.push('[err] ' + msg.text())
  })
  page.on('pageerror', err => consoleMsgs.push('[pageerror] ' + err.message.slice(0, 100)))

  await page.goto(baseUrl + '#/apps', { waitUntil: 'networkidle0' })
  await new Promise(r => setTimeout(r, 3000))

  const info = await page.evaluate(() => {
    return {
      url: window.location.href,
      bodyText: document.body.innerText.slice(0, 300),
      hasOnoutDEX: document.body.innerText.includes('Onout DEX'),
      hasAppsHeading: document.body.innerText.includes('Wallet Apps'),
    }
  })
  console.log('=== devserver #/apps ===')
  console.log(JSON.stringify(info, null, 2))
  if (consoleMsgs.length) console.log('Errors:', consoleMsgs.slice(0,5))

  // Navigate to app view
  await page.goto(baseUrl + '#/apps/onout-dex', { waitUntil: 'networkidle0' })
  await new Promise(r => setTimeout(r, 2000))

  const info2 = await page.evaluate(() => {
    var iframe = document.querySelector('iframe')
    return {
      url: window.location.href,
      iframe: !!iframe,
      iframeSrc: iframe ? iframe.src : null,
      iframeWidth: iframe ? Math.round(iframe.getBoundingClientRect().width) : -1,
      securityNotice: !!document.querySelector('[class*="securityNotice"]'),
      backButton: Array.from(document.querySelectorAll('button')).map(b => b.textContent && b.textContent.trim()).find(t => t && t.includes('All apps')),
    }
  })
  console.log('=== devserver #/apps/onout-dex ===')
  console.log(JSON.stringify(info2, null, 2))

  await browser.close()
})()
