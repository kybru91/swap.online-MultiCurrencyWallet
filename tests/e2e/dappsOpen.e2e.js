/**
 * E2E test: All DApps open correctly
 *
 * Tests that every app from walletAppsCatalog:
 *   1. Has an accessible URL (HTTP 200 or redirect)
 *   2. Loads in the wallet iframe without errors
 *   3. iframe src matches expected host
 *
 * Uses Chrome DevTools Protocol (CDP) on the pre-started chrome-screen daemon.
 *
 * Usage: node tests/e2e/dappsOpen.e2e.js
 * Exit code: 0 = all pass, 1 = failures
 */

const http = require('http')
const https = require('https')
const WebSocket = require('ws')

const CDP_PORT = process.env.CDP_PORT || 9223
const WALLET_BASE = process.env.WALLET_BASE || 'https://swaponline.github.io'
const TEST_TIMEOUT = 120000

// ─── App catalog (mirrors src/front/shared/pages/Apps/appsCatalog.ts) ─────────

const APPS = [
  {
    id: 'onout-dex',
    title: 'Onout DEX',
    routeUrl: 'https://appsource.github.io/dex/?walletBridge=swaponline',
    expectedHost: 'appsource.github.io',
    walletBridge: 'eip1193',
  },
  {
    id: 'polyfactory',
    title: 'PolyFactory',
    routeUrl: 'https://appsource.github.io/predict/?walletBridge=swaponline',
    expectedHost: 'appsource.github.io',
    walletBridge: 'eip1193',
  },
  {
    id: 'farm-factory',
    title: 'FarmFactory',
    routeUrl: 'https://appsource.github.io/farm/?walletBridge=swaponline',
    expectedHost: 'appsource.github.io',
    walletBridge: 'eip1193',
  },
  {
    id: 'ido-launchpad',
    title: 'IDO Launchpad',
    routeUrl: 'https://appsource.github.io/launchpad/?walletBridge=swaponline',
    expectedHost: 'appsource.github.io',
    walletBridge: 'eip1193',
  },
  {
    id: 'crypto-lottery',
    title: 'Crypto Lottery',
    routeUrl: 'https://appsource.github.io/lottery/?walletBridge=swaponline',
    expectedHost: 'appsource.github.io',
    walletBridge: 'eip1193',
  },
]

// ─── HTTP helpers ───────────────────────────────────────────────────────────

function checkUrl(url) {
  return new Promise((resolve) => {
    const lib = url.startsWith('https') ? https : http
    const req = lib.request(url, { method: 'HEAD', timeout: 10000 }, (res) => {
      resolve({ status: res.statusCode, ok: res.statusCode < 400 })
    })
    req.on('error', (err) => resolve({ status: 0, ok: false, error: err.message }))
    req.on('timeout', () => {
      req.destroy()
      resolve({ status: 0, ok: false, error: 'timeout' })
    })
    req.end()
  })
}

// ─── CDP helpers ─────────────────────────────────────────────────────────────

function cdpRequest(method, path) {
  return new Promise((resolve, reject) => {
    const req = http.request({ hostname: '127.0.0.1', port: CDP_PORT, path, method }, (res) => {
      let body = ''
      res.on('data', (d) => (body += d))
      res.on('end', () => {
        try {
          resolve(JSON.parse(body))
        } catch (_) {
          resolve(body)
        }
      })
    })
    req.on('error', reject)
    req.end()
  })
}

async function cdpNewTab(url) {
  return cdpRequest('PUT', '/json/new?' + encodeURIComponent(url))
}

async function cdpCloseTab(tabId) {
  return cdpRequest('GET', '/json/close/' + tabId)
}

class CDPSession {
  constructor(wsUrl) {
    this.wsUrl = wsUrl
    this.ws = null
    this.nextId = 1
    this.pending = {}
  }

  connect() {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(this.wsUrl, { perMessageDeflate: false })
      this.ws.on('open', resolve)
      this.ws.on('error', reject)
      this.ws.on('message', (raw) => {
        const msg = JSON.parse(raw)
        if (msg.id && this.pending[msg.id]) {
          this.pending[msg.id](msg)
          delete this.pending[msg.id]
        }
      })
    })
  }

  send(method, params = {}) {
    return new Promise((resolve, reject) => {
      const id = this.nextId++
      const timeout = setTimeout(() => {
        delete this.pending[id]
        reject(new Error(`CDP timeout: ${method}`))
      }, 30000)
      this.pending[id] = (msg) => {
        clearTimeout(timeout)
        if (msg.error) reject(new Error(msg.error.message))
        else resolve(msg.result)
      }
      this.ws.send(JSON.stringify({ id, method, params }))
    })
  }

  async evaluate(expression) {
    const result = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true,
    })
    if (result.exceptionDetails) {
      throw new Error(
        result.exceptionDetails.exception?.description ||
          result.exceptionDetails.text ||
          'Evaluate error'
      )
    }
    return result.result?.value
  }

  close() {
    if (this.ws) this.ws.close()
  }
}

// ─── Test framework ───────────────────────────────────────────────────────────

const results = []

async function test(name, fn) {
  try {
    await fn()
    results.push({ name, ok: true })
    console.log(`  ✓ ${name}`)
  } catch (err) {
    results.push({ name, ok: false, error: err.message })
    console.log(`  ✗ ${name}`)
    console.log(`    ${err.message}`)
  }
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms))
}

// ─── Single DApp test ─────────────────────────────────────────────────────────

async function testDApp(app) {
  console.log(`\n  ── ${app.title} (${app.id}) ──`)

  // 1. DApp URL responds with HTTP 200 / redirect
  await test(`${app.title}: URL accessible (HTTP)`, async () => {
    const result = await checkUrl(app.routeUrl)
    if (!result.ok) {
      throw new Error(`HTTP ${result.status} ${result.error || ''} for ${app.routeUrl}`)
    }
    console.log(`    → HTTP ${result.status}`)
  })

  // 2. Wallet page loads the app in an iframe
  const walletUrl = `${WALLET_BASE}/#/apps/${app.id}`
  let tab, session

  try {
    tab = await cdpNewTab(walletUrl)
    session = new CDPSession(tab.webSocketDebuggerUrl)
    await session.connect()
    await session.send('Runtime.enable')
    await session.send('Page.enable')

    // Wait for wallet SPA to mount and render the iframe
    await sleep(10000)

    await test(`${app.title}: wallet page loads (no JS error)`, async () => {
      const readyState = await session.evaluate('document.readyState')
      if (!readyState || readyState === 'loading') {
        throw new Error(`Page not loaded, readyState=${readyState}`)
      }
    })

    await test(`${app.title}: iframe rendered in wallet`, async () => {
      const iframeData = await session.evaluate(`
        (() => {
          const frames = Array.from(document.querySelectorAll('iframe'))
          // Match by src host or title
          const match = frames.find(f => {
            try {
              const u = new URL(f.src)
              return u.hostname === '${app.expectedHost}'
            } catch(_) { return false }
          })
          if (!match) {
            // Return all iframes for debug
            return JSON.stringify(frames.map(f => f.src).slice(0, 5))
          }
          return match.src
        })()
      `)

      if (!iframeData || iframeData.startsWith('[')) {
        throw new Error(`No iframe with host ${app.expectedHost}. Found: ${iframeData}`)
      }

      // Verify walletBridge param present
      if (!iframeData.includes('walletBridge=swaponline')) {
        throw new Error(`iframe src missing walletBridge=swaponline: ${iframeData}`)
      }
      console.log(`    → iframe: ${iframeData.slice(0, 80)}...`)
    })
  } finally {
    if (session) session.close()
    if (tab) await cdpCloseTab(tab.id)
  }
}

// ─── Main ─────────────────────────────────────────────────────────────────────

async function runTests() {
  console.log('\n═══ DApps Open E2E Tests ═══')
  console.log(`Wallet base: ${WALLET_BASE}`)
  console.log(`Apps to test: ${APPS.map((a) => a.id).join(', ')}\n`)

  // Check Chrome daemon
  let tabs
  try {
    tabs = await cdpRequest('GET', '/json')
  } catch (err) {
    console.error(`Chrome daemon not running on port ${CDP_PORT}. Start it:`)
    console.error('  cd /root/tools/chrome-daemon && pm2 start ecosystem.config.js')
    process.exit(1)
  }
  console.log(`Chrome daemon OK (${tabs.length} existing tabs)`)

  // Run tests for each app sequentially (avoid opening too many tabs at once)
  for (const app of APPS) {
    await testDApp(app)
  }

  // Summary
  console.log('\n═══ Summary ═══')
  const passed = results.filter((r) => r.ok).length
  const failed = results.filter((r) => !r.ok).length
  console.log(`${passed} passed, ${failed} failed, ${results.length} total\n`)

  if (failed > 0) {
    console.log('Failed tests:')
    results.filter((r) => !r.ok).forEach((r) => console.log(`  ✗ ${r.name}: ${r.error}`))
    console.log('')
  }

  process.exit(failed > 0 ? 1 : 0)
}

const globalTimeout = setTimeout(() => {
  console.error('Global timeout exceeded')
  process.exit(1)
}, TEST_TIMEOUT)

runTests()
  .catch((err) => {
    console.error('Fatal error:', err.message)
    process.exit(1)
  })
  .finally(() => clearTimeout(globalTimeout))
