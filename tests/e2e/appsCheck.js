/**
 * appsCheck.js — Проверка всех приложений в MCW Apps через CDP.
 * Проверяет:
 *   1. Главная /apps — карточки видны, нет белого/blank экрана
 *   2. Каждый app-роут — iframe с walletBridge загружается
 *   3. Скриншоты сохраняются в /tmp/mcw-*.png
 *
 * Запуск: node tests/e2e/appsCheck.js
 * Требует: chrome-cdp на порту 9222, dev server на 9001
 */

const http = require('http')
const WebSocket = require('ws')
const fs = require('fs')

const CDP_PORT = 9222
const BASE_URL = 'http://localhost:9001'

const APPS = [
  { id: 'onout-dex', routeUrl: 'https://appsource.github.io/dex/?walletBridge=swaponline' },
  { id: 'polyfactory', routeUrl: 'https://polyfactory.wpmix.net/?walletBridge=swaponline' },
  { id: 'farm-factory', routeUrl: 'https://appsource.github.io/farm/?walletBridge=swaponline' },
  {
    id: 'ido-launchpad',
    routeUrl: 'https://appsource.github.io/launchpad/?walletBridge=swaponline',
  },
  {
    id: 'crypto-lottery',
    routeUrl: 'https://appsource.github.io/lottery/?walletBridge=swaponline',
  },
  { id: 'lenda', routeUrl: 'https://lenda.wpmix.net/?walletBridge=swaponline' },
]

// ─── CDP helpers ──────────────────────────────────────────────────────────────

function cdpGet(path) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      { hostname: 'localhost', port: CDP_PORT, path, method: 'GET' },
      (res) => {
        let d = ''
        res.on('data', (c) => (d += c))
        res.on('end', () => {
          try {
            resolve(JSON.parse(d))
          } catch {
            resolve(d)
          }
        })
      }
    )
    req.on('error', reject)
    req.end()
  })
}

function cdpPut(path) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      { hostname: 'localhost', port: CDP_PORT, path, method: 'PUT' },
      (res) => {
        let d = ''
        res.on('data', (c) => (d += c))
        res.on('end', () => {
          try {
            resolve(JSON.parse(d))
          } catch {
            resolve(null)
          }
        })
      }
    )
    req.on('error', reject)
    req.end()
  })
}

function connectWs(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl)
    ws.once('open', () => resolve(ws))
    ws.once('error', reject)
    setTimeout(() => reject(new Error('WS connect timeout')), 5000)
  })
}

function makeSession(ws) {
  let msgId = 1

  function send(method, params = {}, timeoutMs = 10000) {
    return new Promise((resolve) => {
      const id = msgId++
      const timer = setTimeout(() => {
        ws.removeListener('message', handler)
        resolve({ _timeout: true })
      }, timeoutMs)

      function handler(raw) {
        let msg
        try {
          msg = JSON.parse(raw)
        } catch {
          return
        }
        if (msg.id === id) {
          clearTimeout(timer)
          ws.removeListener('message', handler)
          resolve(msg.result || {})
        }
      }
      ws.on('message', handler)
      ws.send(JSON.stringify({ id, method, params }))
    })
  }

  // Navigate and wait for loadEventFired (full page load)
  function navigate(url) {
    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        ws.removeListener('message', loadHandler)
        resolve({ _timeout: true })
      }, 15000)

      function loadHandler(raw) {
        let msg
        try {
          msg = JSON.parse(raw)
        } catch {
          return
        }
        if (msg.method === 'Page.loadEventFired') {
          clearTimeout(timer)
          ws.removeListener('message', loadHandler)
          resolve({})
        }
      }
      ws.on('message', loadHandler)
      send('Page.navigate', { url }, 15000).then((r) => {
        if (r._timeout) {
          clearTimeout(timer)
          ws.removeListener('message', loadHandler)
          resolve(r)
        }
      })
    })
  }

  // Poll for JS expression returning true
  async function pollTrue(expression, maxMs = 12000, intervalMs = 500) {
    const deadline = Date.now() + maxMs
    while (Date.now() < deadline) {
      const r = await send('Runtime.evaluate', { expression, returnByValue: true }, 3000)
      if (r.result && r.result.value === true) return true
      await new Promise((r) => setTimeout(r, intervalMs))
    }
    return false
  }

  async function evaluate(expression) {
    const r = await send(
      'Runtime.evaluate',
      { expression, returnByValue: true, awaitPromise: false },
      5000
    )
    if (r._timeout) return null
    return r.result?.value ?? null
  }

  async function evaluateJSON(fnBody) {
    const expr = `JSON.stringify((function(){ ${fnBody} })()`
    const val = await evaluate(expr + ')')
    if (!val) return null
    try {
      return JSON.parse(val)
    } catch {
      return null
    }
  }

  async function screenshot(path, timeoutMs = 8000) {
    const r = await send('Page.captureScreenshot', { format: 'png', fromSurface: true }, timeoutMs)
    if (r.data) {
      fs.writeFileSync(path, Buffer.from(r.data, 'base64'))
      return path
    }
    return null
  }

  return { send, navigate, pollTrue, evaluate, evaluateJSON, screenshot }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

// ─── Dismiss starter modal ────────────────────────────────────────────────────

async function dismissModal(session) {
  await session.evaluate(`
    (function() {
      const m = document.getElementById('starter-modal');
      if (m && !m.classList.contains('d-none')) m.classList.add('d-none');
    })()
  `)
}

// ─── Check: Apps main page ────────────────────────────────────────────────────

async function checkAppsMain(session) {
  console.log('\n📋 Проверка главной страницы /apps ...')

  await session.navigate(BASE_URL + '/#/apps')
  await dismissModal(session)

  // Poll for card images (CSS modules hash class names, so use img count)
  // Apps catalog has 7 apps (6 with cardImage + 1 internal) => >=3 imgs = rendered
  const ready = await session.pollTrue(`document.querySelectorAll('img[src]').length >= 3`, 12000)
  if (!ready) await sleep(3000) // last chance

  // Simple direct evaluate calls (avoid evaluateJSON which can fail on first load)
  const bg = await session.evaluate(`window.getComputedStyle(document.body).backgroundColor`)
  const imgsCount = await session.evaluate(`document.querySelectorAll('img[src]').length`)
  const h1Text = await session.evaluate(`document.querySelector('h1') ? document.querySelector('h1').textContent.trim() : ''`)
  const url = await session.evaluate(`location.href`)

  const shot = await session.screenshot('/tmp/mcw-apps-main.png')
  console.log('  URL:', url)
  console.log('  bg:', bg)
  console.log('  H1:', h1Text)
  console.log('  Картинок (карточки апп):', imgsCount)
  console.log('  Скриншот:', shot)

  const isWhite = !bg || bg === 'rgb(255, 255, 255)' || bg === 'rgba(0, 0, 0, 0)'
  const hasContent = imgsCount >= 3
  const status = !hasContent
    ? '❌ НЕТ КОНТЕНТА (apps не отрендерился)'
    : isWhite
      ? '⚠️  БЕЛЫЙ ФОН'
      : '✅ OK'

  console.log('  Статус:', status)
  return {
    id: 'apps-main',
    url: BASE_URL + '/#/apps',
    status,
    bg,
    shot,
    h1: h1Text,
  }
}

// ─── Check: Single app page ────────────────────────────────────────────────────

async function checkApp(session, app) {
  console.log(`\n🔌 [${app.id}] Переход ...`)

  await session.navigate(BASE_URL + `/#/apps/${app.id}`)
  await dismissModal(session)

  // Poll for iframe with walletBridge (up to 12s)
  const hasIframe = await session.pollTrue(
    `!!Array.from(document.querySelectorAll('iframe')).find(f => f.src && f.src.includes('walletBridge'))`,
    12000
  )

  const iframeInfo = await session.evaluateJSON(`
    const iframes = Array.from(document.querySelectorAll('iframe'));
    const bridge = iframes.find(f => f.src && f.src.includes('walletBridge'));
    return {
      total: iframes.length,
      bridgeSrc: bridge ? bridge.src.substring(0, 120) : null,
    };
  `)

  const bg = await session.evaluate(`window.getComputedStyle(document.body).backgroundColor`)
  const shot = await session.screenshot(`/tmp/mcw-app-${app.id}.png`)

  console.log('  iframes:', iframeInfo?.total, '| bridge:', iframeInfo?.bridgeSrc || 'НЕТ')
  console.log('  bg:', bg)
  console.log('  Скриншот:', shot)

  const isWhite = bg === 'rgb(255, 255, 255)'
  let status = '✅ OK'
  if (!iframeInfo?.bridgeSrc) status = '❌ НЕТ IFRAME с walletBridge'
  else if (isWhite) status = '⚠️  БЕЛЫЙ ФОН'

  console.log('  Статус:', status)
  return {
    id: app.id,
    status,
    hasIframe: !!iframeInfo?.bridgeSrc,
    bridgeSrc: iframeInfo?.bridgeSrc,
    bg,
    shot,
  }
}

// ─── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  console.log('🚀 MCW Apps Check')
  console.log(`CDP :${CDP_PORT}  |  ${BASE_URL}`)
  console.log('='.repeat(60))

  const tabs = await cdpGet('/json')
  if (!Array.isArray(tabs)) {
    console.error('❌ Chrome CDP не отвечает на порту', CDP_PORT)
    process.exit(1)
  }
  console.log('Chrome вкладок:', tabs.length)

  const tab = await cdpPut('/json/new?about:blank')
  const allTabs = await cdpGet('/json')
  const myTab = allTabs.find((t) => t.id === tab.id) || tab

  const ws = await connectWs(myTab.webSocketDebuggerUrl)
  const session = makeSession(ws)

  await session.send('Page.enable')
  await session.send('Runtime.enable')

  const results = []

  try {
    results.push(await checkAppsMain(session))

    for (const app of APPS) {
      results.push(await checkApp(session, app))
    }
  } finally {
    ws.close()
    await cdpGet('/json/close/' + tab.id)
  }

  // ─── Итоговый отчет ──────────────────────────────────────────────────────────
  console.log('\n' + '='.repeat(60))
  console.log('📊 ИТОГОВЫЙ ОТЧЕТ')
  console.log('='.repeat(60))

  for (const r of results) {
    console.log(`${r.status}  [${r.id}]`)
    if (r.bg) console.log(`   bg: ${r.bg}`)
    if (r.bridgeSrc) console.log(`   iframe: ${r.bridgeSrc}`)
    if (r.shot) console.log(`   screenshot: ${r.shot}`)
  }

  const ok = results.filter((r) => r.status.startsWith('✅')).length
  const warn = results.filter((r) => r.status.startsWith('⚠️')).length
  const fail = results.filter((r) => r.status.startsWith('❌')).length

  console.log('\n' + '─'.repeat(60))
  console.log(`✅ ${ok}  ⚠️  ${warn}  ❌ ${fail}  / всего ${results.length}`)

  if (fail > 0) process.exit(1)
}

main().catch((e) => {
  console.error('Fatal:', e.message)
  process.exit(1)
})
