# Deployment & Operations

## Purpose
Deployment process, infrastructure, and production operations for AI agents.

---

## Deployment Platform

**Web App:** GitHub Pages (`appsource/wallet` repo → `appsource.github.io/wallet/`)
- **Type:** Static hosting (client-side SPA)
- **Why:** Free, reliable, CDN-backed, no server maintenance, perfect for client-side crypto wallet

**Market-Maker Bot:** Server-side Node.js process
- **Type:** Long-running Node.js process (babel-node)
- **Why:** Provides liquidity for P2P swaps, requires persistent connection to libp2p network
- **Note:** Bot deployment details TBD (currently manual, no automated deployment configured)

---

## Access Information

**SSH Access:** Not applicable (static hosting)

**Credentials location:** GitHub Actions secrets (`buildbot` secret for deploy script)

---

## Environment Variables

**Build-time only (no runtime .env):**
- `CONFIG` - Selects config file from `src/front/config/` (e.g., `testnet.dev`, `mainnet.prod`)
  - Set in: `.github/workflows/deploymaster.yml` (mainnet), local dev via npm scripts
- `NODE_ENV` - `production` for mainnet builds (enables minification)
- `DEBUG` - Enables debug logs (e.g., `app:*,swap.core:*`)

**GitHub Actions secrets:**
- `APPSOURCE_TOKEN` - personal token for pushing to `appsource/wallet` repo (deploy.yml)
  - Used in: `deploy.yml` step "Deploy to appsource/wallet"

**Client-side config:** RPC endpoints, contract addresses checked into repo at `src/front/config/*.js` (not env vars, part of built bundle)

**Bot config:** `bot-config.json` (not in repo, manual setup on bot server)

<!-- Keep .env.example updated. Comment each variable's purpose in that file. -->

---

## Deployment Triggers

**Production:** Auto-deploy on push to `master` → GitHub Actions `deploy.yml` → builds mainnet bundle → pushes to `appsource/wallet` repo → live at `appsource.github.io/wallet/`

**Staging:** Not configured (testnet builds are manual: `npm run build:testnet`)

**Preview:** PR builds run in CI but don't deploy anywhere (just validation)

---

## Pre-Deploy Checklist

Fully automated via CI. Push to `master` → build → deploy → live.

**Manual verification after deploy:**
- [ ] Check https://appsource.github.io/wallet/ loads
- [ ] Verify wallet creation works (testnet mode recommended first)
- [ ] Check P2P order book connects (libp2p peers visible)
- [ ] Check https://appsource.github.io/wallet/#/apps — all 7 app tiles visible with card images
- [ ] Open Onout DEX tile — iframe loads dex.onout.org with theme param
- [ ] Run CDP smoke: `node tests/e2e/appsCheck.js` (requires chrome-cdp:9222 + dev server:9001)

## GitHub Actions Workflows

| Workflow | File | Trigger | What It Does |
|----------|------|---------|--------------|
| **Deploy MCW** | `deploy.yml` | push to `master` | Builds mainnet bundle → pushes to `appsource/wallet` GitHub Pages. Also builds WordPress plugin ZIP. Includes health check for `appsource.github.io/wallet/`. |
| **Deploy WordPress Plugin** | `deploy.yml` | push to `master`/`main` | Builds mainnet+testnet widgets → packages as ZIP → uploads to farm.wpmix.net/updates/ → updates mcw-info.json |
| **Apps Smoke** | `appsSmoke.yml` | changes in `Apps/**`, `Header/Nav/**`, `appsSmoke.test.js` | Builds testnet → runs 9 E2E smoke tests via Puppeteer (catalog tiles, iframe navigation, theme forwarding, external URL reachability) |

### Apps Catalog E2E Tests

File: `tests/e2e/appsSmoke.test.js`

The `APPS` array in this file is a **manual copy** of `appsCatalog.ts`. When adding a new app to the catalog, MUST update both files.

Currently tracked apps: `onout-dex`, `polyfactory`, `farm-factory`, `ido-launchpad`, `crypto-lottery`, `lenda`

**Static file hosting — appsource.github.io repos:**
- onout-dex → static at `appsource.github.io/dex/` (CNAME: `dex.onout.org`)
- ido-launchpad → static at `appsource.github.io/launchpad/` (CNAME: `launchpad.onout.org`)
- crypto-lottery → static at `appsource.github.io/lottery/` (CNAME: `lottery.onout.org`)
- farm-factory → static at `appsource.github.io/farm/` (no CNAME, direct URL)

**IMPORTANT — iframe redirect rule:** GitHub Pages with CNAME redirects `appsource.github.io/{repo}/` → 301 → custom domain.
Browsers do NOT follow cross-origin 301 inside iframes → blank content.
Use whichever URL returns HTTP 200 directly (no redirect). Check with `node tests/e2e/appsUrlCheck.js`.
**Current (2026-03-10):** `dex.onout.org` returns 404 — use `appsource.github.io/dex/` directly.

### walletBridge Integration in External Apps

When adding a new external app to the catalog:
1. Add hostname to `EXTERNAL_ALLOWED_HOSTS` in `appsCatalog.ts`
2. Add `walletBridge: 'eip1193'` to the catalog entry
3. The external app MUST have bridge client support:
   - Check if `wallet-bridge-init.js` is present in the app's HTML `<head>`
   - Bridge client URL: `https://appsource.github.io/wallet/wallet-apps-bridge-client.js`
   - Detection: `?walletBridge=swaponline` URL param + iframe check
4. Add card screenshot PNG to `src/front/shared/pages/Apps/images/{app-id}.png`
5. Update `APPS` array in `tests/e2e/appsSmoke.test.js`

---

## Rollback Procedure

**Platform rollback:** Revert commit on `master` branch → push → CI auto-deploys previous version

**Alternative:** Manually checkout previous commit in `appsource/wallet` repo and force push

**Approximate time:** ~5 minutes (CI build + deploy)

---

## Environments

**Web App - Production:** https://appsource.github.io/wallet/ - Deploys from `master` branch (mainnet config)

**Web App - Local testnet:** `npm run dev` - localhost:9001 (testnet RPC endpoints)

**Bot - Production:** Manual deployment (no CI/CD). Run via `npm run marketmaker` (mainnet) or `npm run marketmaker:testnet`

**Bot - Configuration:** Uses `bot-config.json` (not in repo). Requires market-maker wallet with funds for providing liquidity.

<!-- If single environment, only list Production -->

---

## Monitoring & Observability

<!--
SCALING HINT: If this section grows beyond ~80 lines, extract to references/monitoring.md.
If no monitoring configured, write: "Logs output to stdout only. No error tracking configured."
-->

### Logging

**Where:** Browser console only (client-side app)
**Format:** `debug` library with namespaces (`app:*`, `swap.core:*`)

### Error Tracking

**Tool:** None configured
**Config:** Client-side errors stay in browser console, no centralized error tracking

### Health Checks

**Endpoint:** None (static site)
**Checks:** Manual verification: wallet loads, P2P connects, balances fetch

### Metrics

**Analytics:** Not configured (privacy-focused wallet)
**Key metrics:** N/A

### Alerts

**Tool:** None
**Rules:** N/A
