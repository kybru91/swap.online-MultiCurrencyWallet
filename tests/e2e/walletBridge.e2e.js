/**
 * E2E test: Wallet Apps Bridge — full bridge protocol verification
 *
 * Strategy:
 *   1. Serve a LOCAL test host page (bridge-test-host.html) with inline bridge logic
 *      → avoids dependency on swaponline.github.io deployment version
 *   2. The host page embeds appsource.github.io in an iframe
 *   3. Use CDP to attach to the iframe target and send messages FROM iframe context
 *      → origin/source security checks pass correctly
 *   4. Verify the bridge host responds correctly to all bridge messages
 *
 * Uses Chrome DevTools Protocol on the pre-started chrome-screen daemon (port 9223).
 *
 * Usage: node tests/e2e/walletBridge.e2e.js
 * Exit code: 0 = all pass, 1 = failures
 */

const http = require('http')
const https = require('https')
const fs = require('fs')
const path = require('path')
const WebSocket = require('ws')

const CDP_PORT = process.env.CDP_PORT || 9223
const TEST_TIMEOUT = 90000
const MOCK_ETH_ADDRESS = '0x1000000000000000000000000000000000000001'

// ─── Local HTTP server ────────────────────────────────────────────────────────

function startLocalServer() {
  return new Promise((resolve, reject) => {
    const htmlPath = path.join(__dirname, 'bridge-test-host.html')
    const html = fs.readFileSync(htmlPath, 'utf8')

    const server = http.createServer((req, res) => {
      res.writeHead(200, {
        'Content-Type': 'text/html; charset=utf-8',
        'X-Frame-Options': 'ALLOWALL',
      })
      res.end(html)
    })

    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address()
      resolve({ server, url: `http://127.0.0.1:${port}/` })
    })

    server.on('error', reject)
  })
}

// ─── CDP helpers ──────────────────────────────────────────────────────────────

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
    this.eventHandlers = {}
  }

  connect() {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(this.wsUrl, { perMessageDeflate: false })
      this.ws.on('open', resolve)
      this.ws.on('error', reject)
      this.ws.on('message', (raw) => {
        const msg = JSON.parse(raw)
        // Route response — id is globally unique across all sessions
        if (msg.id && this.pending[msg.id]) {
          this.pending[msg.id](msg)
          delete this.pending[msg.id]
        }
        // Route top-level events
        if (!msg.sessionId && msg.method && this.eventHandlers[msg.method]) {
          this.eventHandlers[msg.method].forEach((fn) => fn(msg.params))
        }
      })
    })
  }

  /**
   * Send a CDP command.
   * @param {string} method
   * @param {object} params
   * @param {string|undefined} sessionId - route to sub-session (iframe target) if provided
   */
  send(method, params = {}, sessionId = undefined) {
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
      const msgObj = { id, method, params }
      if (sessionId !== undefined) msgObj.sessionId = sessionId
      this.ws.send(JSON.stringify(msgObj))
    })
  }

  on(event, fn) {
    if (!this.eventHandlers[event]) this.eventHandlers[event] = []
    this.eventHandlers[event].push(fn)
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

  /** Execute JS in a sub-session (e.g. iframe CDP target) */
  async evaluateInSession(expression, sessionId) {
    const result = await this.send(
      'Runtime.evaluate',
      { expression, awaitPromise: true, returnByValue: true },
      sessionId
    )
    if (result.exceptionDetails) {
      throw new Error(
        result.exceptionDetails.exception?.description ||
          result.exceptionDetails.text ||
          'Evaluate error in sub-session'
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

/**
 * Find and attach to the appsource.github.io iframe as a CDP sub-session.
 * Returns sessionId, or null if not found.
 */
async function attachToIframe(session, expectedUrlPart) {
  const { targetInfos } = await session.send('Target.getTargets')
  const iframeTarget = targetInfos.find(
    (t) => t.type === 'iframe' && t.url.includes(expectedUrlPart)
  )
  if (!iframeTarget) return null

  const { sessionId } = await session.send('Target.attachToTarget', {
    targetId: iframeTarget.targetId,
    flatten: true,
  })
  await session.send('Runtime.enable', {}, sessionId)
  return sessionId
}

/**
 * Send a bridge REQUEST from the iframe context via CDP.
 * Adds a message listener, posts the request to parent, awaits the RESPONSE.
 * Returns the response payload object { result, error }.
 */
function sendBridgeRequest(session, sessionId, method, params, timeoutMs = 5000) {
  const reqId = `e2e-${method}-${Date.now()}`
  const expression = `
    new Promise(function(resolve, reject) {
      var reqId = ${JSON.stringify(reqId)}
      var tid = setTimeout(function() {
        reject(new Error('Bridge timeout for ' + ${JSON.stringify(method)}))
      }, ${timeoutMs})

      window.addEventListener('message', function handler(e) {
        if (
          e.data &&
          e.data.source === 'swap.wallet.apps.bridge.host' &&
          e.data.type === 'WALLET_APPS_BRIDGE_RESPONSE' &&
          e.data.payload &&
          e.data.payload.requestId === reqId
        ) {
          clearTimeout(tid)
          window.removeEventListener('message', handler)
          resolve(e.data.payload)
        }
      })

      window.parent.postMessage({
        source: 'swap.wallet.apps.bridge.client',
        type: 'WALLET_APPS_BRIDGE_REQUEST',
        payload: {
          requestId: reqId,
          method: ${JSON.stringify(method)},
          params: ${JSON.stringify(params || [])}
        }
      }, '*')
    })
  `
  return session.evaluateInSession(expression, sessionId)
}

// ─── Main test suite ──────────────────────────────────────────────────────────

async function runTests() {
  console.log('\n═══ Wallet Apps Bridge E2E Tests ═══\n')

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

  // Start local server with our bridge test host page
  const { server, url: localUrl } = await startLocalServer()
  console.log(`Local test host: ${localUrl}\n`)

  let tab, session

  try {
    // Open tab and navigate to local test host
    tab = await cdpNewTab('about:blank')
    session = new CDPSession(tab.webSocketDebuggerUrl)
    await session.connect()
    await session.send('Runtime.enable')
    await session.send('Page.enable')
    await session.send('Target.setDiscoverTargets', { discover: true })

    await session.send('Page.navigate', { url: localUrl })

    console.log('Waiting for page + PolyFactory iframe to load...')
    await sleep(12000)

    // ─── Structural tests ───────────────────────────────────────
    console.log('\n--- Page & iframe tests ---')

    await test('Page loads and has title', async () => {
      const title = await session.evaluate('document.title')
      if (!title) throw new Error('No page title')
    })

    await test('Iframe for PolyFactory rendered in page', async () => {
      const hasIframe = await session.evaluate(`
        (() => {
          const frames = Array.from(document.querySelectorAll('iframe'))
          return frames.some(f => {
            try { return new URL(f.src).hostname === 'appsource.github.io' } catch(_) { return false }
          })
        })()
      `)
      if (!hasIframe) throw new Error('No appsource.github.io iframe found')
    })

    await test('Iframe src points to appsource.github.io with walletBridge param', async () => {
      const src = await session.evaluate(`
        (() => {
          const f = Array.from(document.querySelectorAll('iframe')).find(f => {
            try { return new URL(f.src).hostname === 'appsource.github.io' } catch(_) { return false }
          })
          return f ? f.src : ''
        })()
      `)
      if (!src.includes('appsource.github.io'))
        throw new Error(`Expected appsource.github.io in src, got: ${src}`)
      if (!src.includes('walletBridge=swaponline'))
        throw new Error(`Expected walletBridge=swaponline in src, got: ${src}`)
    })

    // ─── Attach to iframe via CDP ────────────────────────────────
    console.log('\n--- Attaching to iframe via CDP ---')

    let iframeSessionId = null

    await test('CDP can attach to appsource.github.io iframe target', async () => {
      iframeSessionId = await attachToIframe(session, 'appsource.github.io')
      if (!iframeSessionId) throw new Error('appsource.github.io not found as CDP iframe target')
      console.log(`    sessionId: ${iframeSessionId.slice(0, 16)}...`)
    })

    if (!iframeSessionId) {
      console.log('\n⚠ Cannot attach to iframe — skipping bridge tests')
      return
    }

    // ─── Bridge handshake ────────────────────────────────────────
    console.log('\n--- Bridge handshake tests ---')

    await test('Bridge HELLO from iframe → host sends READY response', async () => {
      const result = await session.evaluateInSession(
        `
        new Promise(function(resolve, reject) {
          var tid = setTimeout(function() {
            reject(new Error('No READY from bridge host (5s timeout)'))
          }, 5000)
          window.addEventListener('message', function handler(e) {
            if (
              e.data &&
              e.data.source === 'swap.wallet.apps.bridge.host' &&
              e.data.type === 'WALLET_APPS_BRIDGE_READY'
            ) {
              clearTimeout(tid)
              window.removeEventListener('message', handler)
              resolve(e.data.payload)
            }
          })
          window.parent.postMessage({
            source: 'swap.wallet.apps.bridge.client',
            type: 'WALLET_APPS_BRIDGE_HELLO',
            payload: {}
          }, '*')
        })
      `,
        iframeSessionId
      )

      if (!result) throw new Error('READY payload is null')
      if (typeof result.providerAvailable !== 'boolean')
        throw new Error(`READY missing providerAvailable field: ${JSON.stringify(result)}`)
      if (!result.providerAvailable)
        throw new Error('READY.providerAvailable is false — bridge has no wallet')
      if (!Array.isArray(result.accounts) || result.accounts.length === 0)
        throw new Error(`READY has no accounts: ${JSON.stringify(result.accounts)}`)
      if (!result.accounts[0].startsWith('0x'))
        throw new Error(`READY account is not 0x address: ${result.accounts[0]}`)
      console.log(`    providerAvailable: ${result.providerAvailable}`)
      console.log(`    account: ${result.accounts[0]}`)
    })

    // ─── Internal provider tests ─────────────────────────────────
    console.log('\n--- Internal provider tests (via iframe CDP) ---')

    await test('eth_chainId returns hex chain ID', async () => {
      const payload = await sendBridgeRequest(session, iframeSessionId, 'eth_chainId', [])
      if (payload.error)
        throw new Error(`eth_chainId error: ${payload.error.message} (${payload.error.code})`)
      if (!payload.result || !payload.result.startsWith('0x'))
        throw new Error(`Expected hex chainId, got: ${payload.result}`)
      console.log(`    chainId: ${payload.result}`)
    })

    await test('wallet_switchEthereumChain (BSC 0x38) returns null', async () => {
      const payload = await sendBridgeRequest(
        session,
        iframeSessionId,
        'wallet_switchEthereumChain',
        [{ chainId: '0x38' }]
      )
      if (payload.error)
        throw new Error(`wallet_switchEthereumChain error: ${payload.error.message}`)
      if (payload.result !== null && payload.result !== undefined)
        throw new Error(`Expected null result, got: ${JSON.stringify(payload.result)}`)
    })

    await test('wallet_switchEthereumChain emits chainChanged event to iframe', async () => {
      const result = await session.evaluateInSession(
        `
        new Promise(function(resolve, reject) {
          var reqId = 'e2e-switch-event-' + Date.now()
          var chainChanged = null
          var tid = setTimeout(function() {
            reject(new Error('Timeout waiting for chainChanged'))
          }, 5000)

          window.addEventListener('message', function evtHandler(e) {
            if (
              e.data &&
              e.data.source === 'swap.wallet.apps.bridge.host' &&
              e.data.type === 'WALLET_APPS_BRIDGE_EVENT' &&
              e.data.payload &&
              e.data.payload.eventName === 'chainChanged'
            ) {
              chainChanged = e.data.payload.data
              window.removeEventListener('message', evtHandler)
            }
          })

          window.addEventListener('message', function resHandler(e) {
            if (
              e.data &&
              e.data.source === 'swap.wallet.apps.bridge.host' &&
              e.data.type === 'WALLET_APPS_BRIDGE_RESPONSE' &&
              e.data.payload &&
              e.data.payload.requestId === reqId
            ) {
              window.removeEventListener('message', resHandler)
              clearTimeout(tid)
              setTimeout(function() { resolve(chainChanged) }, 150)
            }
          })

          window.parent.postMessage({
            source: 'swap.wallet.apps.bridge.client',
            type: 'WALLET_APPS_BRIDGE_REQUEST',
            payload: {
              requestId: reqId,
              method: 'wallet_switchEthereumChain',
              params: [{ chainId: '0x89' }]
            }
          }, '*')
        })
      `,
        iframeSessionId
      )

      if (result !== '0x89')
        throw new Error(`Expected chainChanged '0x89', got: ${JSON.stringify(result)}`)
    })

    await test('eth_requestAccounts returns wallet address', async () => {
      const payload = await sendBridgeRequest(session, iframeSessionId, 'eth_requestAccounts', [])
      if (payload.error) throw new Error(`eth_requestAccounts error: ${payload.error.message}`)
      if (!Array.isArray(payload.result) || payload.result.length === 0)
        throw new Error(`Expected accounts array, got: ${JSON.stringify(payload.result)}`)
      if (!payload.result[0].startsWith('0x'))
        throw new Error(`Expected 0x address, got: ${payload.result[0]}`)
      console.log(`    account: ${payload.result[0]}`)
    })

    await test('wallet_addEthereumChain returns null (silently accepted)', async () => {
      const payload = await sendBridgeRequest(session, iframeSessionId, 'wallet_addEthereumChain', [
        { chainId: '0x38', chainName: 'BSC', rpcUrls: ['https://bsc-dataseed.binance.org/'] },
      ])
      if (payload.error) throw new Error(`wallet_addEthereumChain error: ${payload.error.message}`)
      if (payload.result !== null && payload.result !== undefined)
        throw new Error(`Expected null, got: ${JSON.stringify(payload.result)}`)
    })

    await test('Blocked method eth_subscribe returns error code 4200', async () => {
      const payload = await sendBridgeRequest(session, iframeSessionId, 'eth_subscribe', [])
      if (!payload.error) throw new Error('Expected error for blocked method eth_subscribe')
      if (payload.error.code !== 4200)
        throw new Error(`Expected error code 4200, got: ${payload.error.code}`)
    })

    await test('net_version returns numeric string', async () => {
      const payload = await sendBridgeRequest(session, iframeSessionId, 'net_version', [])
      if (payload.error) throw new Error(`net_version error: ${payload.error.message}`)
      if (!payload.result || isNaN(Number(payload.result)))
        throw new Error(`Expected numeric string, got: ${payload.result}`)
      console.log(`    version: ${payload.result}`)
    })
  } finally {
    if (session) session.close()
    if (tab) await cdpCloseTab(tab.id)
    server.close()
  }

  // ─── Summary ───────────────────────────────────────────────────
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

// Global timeout
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
