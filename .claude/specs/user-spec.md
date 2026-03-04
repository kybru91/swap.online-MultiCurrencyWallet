# User Spec: Apps Catalog Expansion with Card Images

## Problem

The MCW Apps page (`/#/apps`) currently shows only 3 apps — Swap.Online Exchange (internal), Onout DEX, and PolyFactory. The catalog tiles are visually empty (only a 2-letter symbol in a colored square). The other onout.org crypto products (FarmFactory, Launchpad/IDO, Crypto Lottery) are not listed, although they are live and support the `walletBridge=swaponline` iframe protocol.

## Goal

Expand the apps catalog with the remaining onout.org crypto products and make each tile visually recognizable with a card screenshot. The wallet bridge (EIP-1193) and theme forwarding must work correctly for all new apps.

---

## User Stories

**US-1 — New apps in catalog**
As a wallet user I want to see FarmFactory, IDO Launchpad, and Crypto Lottery tiles in the Apps catalog so I can open them inside the wallet UI without leaving the page.

**US-2 — Card images on tiles**
As a wallet user I want each app tile to show a preview screenshot instead of a blank colored box so I can immediately recognize the dApp.

**US-3 — DEX "create admin" issue**
As a wallet user I want to open Onout DEX inside the wallet without being redirected to an admin-creation screen. The DEX must detect it is running in iframe mode and show the normal trading interface.

**US-4 — Documentation accuracy**
As a developer I want the README and CI workflow docs to reflect the current state of the apps catalog, wallet bridge architecture, and deployment steps.

---

## Acceptance Criteria

### AC-1: Catalog entries

| # | Criterion | Testable? |
|---|-----------|-----------|
| 1.1 | `walletAppsCatalog` contains entries for `farm-factory`, `ido-launchpad`, `crypto-lottery` | Unit test |
| 1.2 | Each new entry has `walletBridge: 'eip1193'` | Unit test |
| 1.3 | `EXTERNAL_ALLOWED_HOSTS` includes `farm.wpmix.net`, `launchpad.onout.org`, `lottery.onout.org` | Unit test |
| 1.4 | All new app entries are reachable via HTTP 200 (or redirect to 200) | E2E / manual |
| 1.5 | Nav dropdown shows all catalog apps (including new ones) | E2E smoke |

### AC-2: Card images

| # | Criterion | Testable? |
|---|-----------|-----------|
| 2.1 | `WalletApp` type has optional `cardImage?: string` field | TS compile |
| 2.2 | PNG screenshot files exist in `src/front/shared/pages/Apps/images/` for each app (5 total: DEX, PolyFactory, FarmFactory, Launchpad, Lottery) | File exists |
| 2.3 | App tile renders `<img>` when `cardImage` is set; falls back to letter symbol when not | Unit test / visual |
| 2.4 | Images are imported via webpack (not external URLs) | Build check |

### AC-3: Theme forwarding

| # | Criterion | Testable? |
|---|-----------|-----------|
| 3.1 | All new external apps receive `?theme=light` or `?theme=dark` appended to iframe URL | E2E smoke (existing assertion) |

### AC-4: Wallet bridge

| # | Criterion | Testable? |
|---|-----------|-----------|
| 4.1 | Opening any new app in iframe does NOT show "Blocked by allowlist policy" error | E2E smoke |
| 4.2 | All existing walletBridge unit tests still pass (27 tests) | Unit test |

### AC-5: DEX admin screen (investigate → fix)

| # | Criterion | Testable? |
|---|-----------|-----------|
| 5.1 | Opening `dex.onout.org` in the wallet iframe shows the DEX trading interface, NOT an admin-creation form | Manual / E2E |
| 5.2 | Fix is applied without rebuilding the DEX (configuration via URL param or localStorage seed) | Manual verify |

### AC-6: Tests stay green

| # | Criterion | Testable? |
|---|-----------|-----------|
| 6.1 | `appsSmoke.test.js` `APPS` array synced with new catalog entries | E2E smoke (9→12 tests or extended existing) |
| 6.2 | `appsCatalog.test.ts` updated unit tests pass | Unit test |
| 6.3 | All existing 9/9 E2E smoke tests still pass | CI |

### AC-7: Documentation

| # | Criterion | Testable? |
|---|-----------|-----------|
| 7.1 | `README.md` mentions all catalog apps with their URLs | Manual review |
| 7.2 | `README.md` documents wallet bridge architecture and `walletBridge=swaponline` param | Manual review |
| 7.3 | CI workflow comments / docs reflect current build steps | Manual review |

---

## Apps to Add

| App | URL | walletBridge | Chains |
|-----|-----|--------------|--------|
| FarmFactory | `https://farm.wpmix.net/?walletBridge=swaponline` | `eip1193` | BSC, Ethereum |
| IDO Launchpad | `https://launchpad.onout.org/?walletBridge=swaponline` | `eip1193` | Ethereum, BSC, Polygon |
| Crypto Lottery | `https://lottery.onout.org/?walletBridge=swaponline` | `eip1193` | Ethereum, BSC |

**Note:** Internal Swap.Online Exchange stays as the first entry (isInternal, no bridge). Onout DEX and PolyFactory stay unchanged.

---

## Card Images Strategy

- **Method:** One-time Puppeteer screenshot capture, stored as static PNG files in the repo
- **Path:** `src/front/shared/pages/Apps/images/{app-id}.png`
- **Size:** 800×500px (landscape, 16:10) — suitable for tile thumbnail at 100% width
- **When:** Generated manually once by the developer, committed to repo
- **Fallback:** If `cardImage` is not set, tile falls back to existing letter-symbol icon — no regression for apps without images

---

## DEX Admin Screen Investigation

The Onout DEX (`dex.onout.org`) shows an "admin creation" form when it detects no local admin config. Based on code research:

- `window.location.hostname` IS `dex.onout.org` inside the iframe — domain detection is correct
- The "create admin" screen is an app-level flow, likely based on absence of a localStorage key (admin credentials)
- Hypothesis: `walletBridge=swaponline` param is read by the DEX app; if it also reads an `adminSkip=1` or similar param, the screen can be bypassed
- **Action:** Inspect DEX app source (`~/onout.org/dex/` or `dex.onout.org` dist) for localStorage key; update MCW catalog URL to pass required param OR set localStorage seed in `walletBridge.ts` after bridge connect

---

## Out of Scope

- Rebuilding or modifying the DEX, FarmFactory, or other external apps
- Adding new chains to the wallet itself
- Changing the iframe sandbox policy
- Authentication / login flows inside iframe apps
- App ratings, favorites, or search in the catalog
- Mobile-specific layout changes (catalog grid is already responsive)

---

## Known Constraints

- External app URLs must be in `EXTERNAL_ALLOWED_HOSTS` for iframe to render (security allowlist)
- Each external app must send `BRIDGE_HELLO` from the correct origin for the wallet bridge to activate
- `appsSmoke.test.js` contains a manual copy of the `APPS` array — must be kept in sync
- Images must go through webpack import (not raw public URL) for hash fingerprinting
- The `WalletApp.cardImage` field will be `string` (path from import) — TypeScript must accept `import img from '...'` syntax

---

## Dependencies

- PNG screenshots exist before catalog entries can be merged (images committed to repo)
- DEX investigation must complete before AC-5 fix can be designed

---

**Status:** Draft
**Date:** 2026-03-04
**Feature dir:** `.claude/specs/`
