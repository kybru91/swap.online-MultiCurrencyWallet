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
    var iframeParent = iframe ? iframe.parentElement : null
    var iframeGrandParent = iframeParent ? iframeParent.parentElement : null

    function getRect(el) {
      if (!el) return null
      var r = el.getBoundingClientRect()
      var cs = window.getComputedStyle(el)
      return {
        tag: el.tagName,
        className: el.className.slice(0, 50),
        width: Math.round(r.width),
        display: cs.display,
        flexDirection: cs.flexDirection,
        paddingLeft: cs.paddingLeft,
        paddingRight: cs.paddingRight,
        marginLeft: cs.marginLeft,
        marginRight: cs.marginRight,
        maxWidth: cs.maxWidth,
      }
    }

    return {
      viewportWidth: window.innerWidth,
      iframe: getRect(iframe),
      iframeParent: getRect(iframeParent),
      iframeGrandParent: getRect(iframeGrandParent),
      allSections: Array.from(document.querySelectorAll('section')).map(getRect).slice(0, 5),
    }
  })
  console.log(JSON.stringify(info, null, 2))

  await browser.close()
})()
