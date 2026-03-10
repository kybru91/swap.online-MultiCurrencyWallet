#!/usr/bin/env node
/**
 * appsUrlCheck.js — Проверяет, что все внешние URL из appsCatalog отвечают HTTP 2xx.
 *
 * Запуск: node tests/e2e/appsUrlCheck.js
 * Не требует браузера, сервера или зависимостей — только Node.js built-ins.
 */

const https = require('https')
const http = require('http')

// Синхронизировать с appsCatalog.ts при добавлении новых апп
const APPS = [
  { id: 'onout-dex',       url: 'https://appsource.github.io/dex/' },
  { id: 'polyfactory',     url: 'https://polyfactory.wpmix.net/' },
  { id: 'farm-factory',    url: 'https://appsource.github.io/farm/' },
  { id: 'ido-launchpad',   url: 'https://launchpad.onout.org/' },
  { id: 'crypto-lottery',  url: 'https://lottery.onout.org/' },
  { id: 'lenda',           url: 'https://lenda.wpmix.net/' },
]

const TIMEOUT_MS = 8000
const MAX_REDIRECTS = 5

function checkUrl(url, redirectsLeft = MAX_REDIRECTS) {
  return new Promise((resolve) => {
    const lib = url.startsWith('https') ? https : http
    const req = lib.request(url, { method: 'HEAD', timeout: TIMEOUT_MS }, (res) => {
      const status = res.statusCode

      if (status >= 301 && status <= 308 && res.headers.location && redirectsLeft > 0) {
        const next = new URL(res.headers.location, url).href
        resolve(checkUrl(next, redirectsLeft - 1))
        return
      }

      resolve({ status, ok: status >= 200 && status < 300 })
    })

    req.on('timeout', () => {
      req.destroy()
      resolve({ status: 0, ok: false, error: 'timeout' })
    })

    req.on('error', (err) => {
      resolve({ status: 0, ok: false, error: err.message })
    })

    req.end()
  })
}

async function main() {
  console.log('Checking app URLs...')

  const results = await Promise.all(
    APPS.map(async (app) => {
      const result = await checkUrl(app.url)
      return { ...app, ...result }
    })
  )

  let failed = 0
  for (const r of results) {
    if (r.ok) {
      console.log(`  OK  [${r.status}] ${r.id}  ${r.url}`)
    } else {
      console.log(`  FAIL [${r.status}${r.error ? ' ' + r.error : ''}] ${r.id}  ${r.url}`)
      failed++
    }
  }

  if (failed > 0) {
    console.log(`\n${failed}/${results.length} app URL(s) unreachable.`)
    process.exit(1)
  }

  console.log(`\nAll ${results.length} app URLs OK.`)
}

main().catch((e) => {
  console.error('Fatal:', e.message)
  process.exit(1)
})
