const puppeteer = require('puppeteer')
const baseUrl = 'http://localhost:9001/'

;(async () => {
  const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] })
  const page = await browser.newPage()
  await page.setViewport({ width: 1100, height: 1080 })

  await page.goto(baseUrl + '#/apps/onout-dex', { waitUntil: 'networkidle0' })
  await new Promise(r => setTimeout(r, 2000))

  const info = await page.evaluate(() => {
    var iframe = document.querySelector('iframe')
    var main = document.querySelector('main')
    var appsSection = document.querySelector('[class*="appsPageFull"]')
    var widthContainer = document.querySelector('#swapComponentWrapper')

    return {
      viewportWidth: window.innerWidth,
      iframe: iframe ? {
        width: Math.round(iframe.getBoundingClientRect().width),
        style: iframe.getAttribute('style'),
        className: iframe.className,
      } : null,
      main: main ? {
        width: Math.round(main.getBoundingClientRect().width),
      } : null,
      appsSection: appsSection ? {
        width: Math.round(appsSection.getBoundingClientRect().width),
        style: window.getComputedStyle(appsSection).width,
      } : null,
      widthContainer: widthContainer ? {
        width: Math.round(widthContainer.getBoundingClientRect().width),
        paddingLeft: window.getComputedStyle(widthContainer).paddingLeft,
        paddingRight: window.getComputedStyle(widthContainer).paddingRight,
      } : null,
    }
  })
  console.log(JSON.stringify(info, null, 2))

  await browser.close()
})()
