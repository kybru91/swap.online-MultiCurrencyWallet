# Code Research: Apps Catalog — Card Images, Domain Detection, Theme Forwarding

**Feature:** Add more apps to the catalog with card images, fix dex.onout.org iframe domain detection, forward theme to iframes.

---

## 1. Entry Points

### `/root/MultiCurrencyWallet/src/front/shared/pages/Apps/Apps.tsx`
Main page component. Handles two states: (1) catalog grid listing all apps, (2) full-screen iframe view for a selected app. Connected to Redux (`user.ethData`) and React Router (`:appId?` param).

Key function signatures:
```ts
const Apps = (props: AppsProps) => { ... }
// Props: history, intl.locale, match.params.appId, ethData: { address, currency } | null
```

### `/root/MultiCurrencyWallet/src/front/shared/pages/Apps/appsCatalog.ts`
Defines `WalletApp` type, `walletAppsCatalog` array, allowlist set, and URL resolution helpers.

Key exports:
```ts
export const walletAppsCatalog: WalletApp[]
export const getWalletAppById = (appId?: string): WalletApp | undefined
export const resolveWalletAppUrl = (app: WalletApp, currentLocation?: Location): string
export const isAllowedWalletAppUrl = (appUrl: string, currentLocation?: Location): boolean
```

### `/root/MultiCurrencyWallet/src/front/shared/pages/Apps/walletBridge.ts`
PostMessage bridge between MCW host and iframe dApps. Implements EIP-1193 provider proxy.

Key export:
```ts
export const createWalletAppsBridge = ({
  iframe, appUrl, internalWallet, onClientHello
}: WalletAppsBridgeOptions): WalletAppsBridge
```

### `/root/MultiCurrencyWallet/src/front/shared/components/Header/config.tsx`
Builds nav menu items for desktop and mobile. Reads `externalConfig.opts.ui.apps` to decide if Apps nav is shown. Imports `walletAppsCatalog` to generate dropdown items.

### `/root/MultiCurrencyWallet/src/front/shared/routes/index.tsx` (line 68)
Route registration:
```tsx
<Route path={`${links.apps}/:appId?`} component={Apps} />
```

---

## 2. Data Layer

### `WalletApp` type (appsCatalog.ts lines 1-11)
```ts
export type WalletApp = {
  id: string               // URL slug, e.g. 'onout-dex'
  title: string            // Display name in catalog grid tile
  menuTitle?: string       // Optional shorter name in nav dropdown
  description: string      // Currently NOT rendered in the UI tile
  iconSymbol?: string      // 2-letter fallback text in 48x48 icon box
  routeUrl: string         // Internal path OR full https:// URL
  supportedChains: string[]
  walletBridge?: 'none' | 'eip1193'
  isInternal?: boolean     // If true, resolves to same-origin hash URL
}
```

**No image/card image field exists in the type.** The tile UI currently renders only:
- `iconSymbol` (or first letter of `title`) in a 48x48 colored box
- `title` text below the icon
- An "Internal" badge label if `isInternal === true`

### Current catalog entries (appsCatalog.ts lines 18-50)
```ts
walletAppsCatalog = [
  { id: 'swapio-exchange', routeUrl: '/exchange/quick', isInternal: true, walletBridge: 'none', iconSymbol: 'SO' },
  { id: 'onout-dex', routeUrl: 'https://dex.onout.org/?walletBridge=swaponline', walletBridge: 'eip1193', iconSymbol: 'OD' },
  { id: 'polyfactory', routeUrl: 'https://polyfactory.wpmix.net/?walletBridge=swaponline', walletBridge: 'eip1193', iconSymbol: 'PF' },
]
```

### `EXTERNAL_ALLOWED_HOSTS` allowlist (appsCatalog.ts lines 13-16)
```ts
const EXTERNAL_ALLOWED_HOSTS = new Set([
  'dex.onout.org',
  'polyfactory.wpmix.net',
])
```

Only these two external hosts pass `isAllowedWalletAppUrl()`. Adding new external apps requires adding their hostname here.

---

## 3. How Theme Parameter Is Currently Handled

In `Apps.tsx` (lines 63-71):
```ts
const appUrl = useMemo(() => {
  if (!selectedApp) return ''
  const base = resolveWalletAppUrl(selectedApp)
  if (selectedApp.isInternal) return base          // Internal: no theme param
  const rawScheme = document.body.dataset.scheme || 'default'
  const scheme = rawScheme === 'dark' ? 'dark' : 'light'  // 'default' maps to 'light'
  const sep = base.includes('?') ? '&' : '?'
  return `${base}${sep}theme=${scheme}`
}, [selectedApp])
```

Theme is read from `document.body.dataset.scheme` (set by `ThemeSwitcher.tsx`). External apps get `?theme=light` or `?theme=dark` appended. Internal apps get no theme param.

The E2E test verifies this: `expect(iframeSrc).toMatch(/theme=(dark|light)/)` (appsSmoke.test.js line 213).

---

## 4. How the Iframe URL Is Constructed

1. `resolveWalletAppUrl(app)` — if external, returns `app.routeUrl` verbatim (e.g. `https://dex.onout.org/?walletBridge=swaponline`)
2. Theme appended: `&theme=light` or `&theme=dark` (since routeUrl already has `?`)
3. Final result for Onout DEX: `https://dex.onout.org/?walletBridge=swaponline&theme=light`
4. Result passed as `src` to `<iframe>` element (Apps.tsx line 131)
5. Security check: `isAllowedWalletAppUrl(appUrl)` must return true, else security notice is shown and no iframe renders

The walletBridge origin detection in `walletBridge.ts` (line 183):
```ts
targetOrigin = new URL(appUrl).origin  // e.g. 'https://dex.onout.org'
```
Messages are only accepted/sent to this exact origin. The iframe app must send `BRIDGE_HELLO` from `dex.onout.org` to establish connection.

**Domain detection issue with dex.onout.org:** The bridge checks `event.origin !== targetOrigin` strictly. If the dex page redirects internally (e.g. to a different path) or the bridge client code checks `window.location.hostname` to decide whether to activate, any mismatch would break the flow. The `walletBridge=swaponline` query param is what the DEX uses to detect it's running in iframe mode.

---

## 5. Existing Tests

### Unit tests: `/root/MultiCurrencyWallet/tests/unit/appsCatalog.test.ts`
Framework: Jest + jsdom (default testEnvironment). 4 tests covering:
- `defaultWalletAppId` value
- `resolveWalletAppUrl` for internal apps
- `isAllowedWalletAppUrl` allowlist enforcement
- `polyfactory` entry existence and shape

Representative signatures:
```ts
it('allows only configured external hosts in allowlist', () => {
  expect(isAllowedWalletAppUrl('https://dex.onout.org/')).toBe(true)
  expect(isAllowedWalletAppUrl('https://evil.example.com/')).toBe(false)
})

it('polyfactory app exists with eip1193 bridge', () => {
  const app = getWalletAppById('polyfactory')
  expect(app!.routeUrl).toContain('polyfactory.wpmix.net')
})
```

### E2E smoke tests: `/root/MultiCurrencyWallet/tests/e2e/appsSmoke.test.js`
Framework: Jest + Puppeteer, `@jest-environment node` docblock. 9 tests. Requires dev server OR `build-testnet/index.html`.

The `APPS` array in this file (lines 28-41) is a **manual copy** of the catalog — must be kept in sync with `appsCatalog.ts` when adding new apps:
```js
const APPS = [
  { id: 'onout-dex', title: 'Onout DEX', urlPattern: 'dex.onout.org', externalUrl: 'https://dex.onout.org/' },
  { id: 'polyfactory', title: 'PolyFactory', urlPattern: 'polyfactory.wpmix.net', externalUrl: 'https://polyfactory.wpmix.net/' },
]
```

Key test assertions when adding new apps:
- Tile appears in catalog page body text (line 133)
- `iframeSrc` contains `urlPattern` (line 211)
- `iframeSrc` matches `/theme=(dark|light)/` (line 213)
- No critical console errors (line 215-219)
- External URL is reachable via `fetch HEAD no-cors` (line 102-113)

### Bridge smoke tests: `/root/MultiCurrencyWallet/tests/e2e/walletAppsBridge.smoke.js`
2 tests. Tests auto-connect with imported wallet, and modal fallback without wallet. Uses `DEX_IFRAME_PATTERN = 'onout.org'`.

### CI workflow: `/root/MultiCurrencyWallet/.github/workflows/appsSmoke.yml`
Triggers on push/PR when `src/front/shared/pages/Apps/**` or `src/front/shared/components/Header/Nav/**` or `tests/e2e/appsSmoke.test.js` or `Header/config.tsx` changes. Runs `npm run build:testnet-tests` then `npm run test:e2e_apps_smoke`. Uploads screenshots as artifacts.

---

## 6. Similar Features / Patterns for Card Images

Existing image usage patterns in the codebase:
- Currency icons: `/root/MultiCurrencyWallet/src/front/shared/components/ui/CurrencyIcon/images/` — SVG/PNG files imported directly
- Wallet provider logos: `/root/MultiCurrencyWallet/src/front/shared/images/` — SVG files for metamask, walletconnect, etc.
- Feature images: `/root/MultiCurrencyWallet/src/front/shared/pages/Marketmaker/images/` — PNG promo images

Pattern for using images in components:
```tsx
import logoImg from './images/logo.svg'
// or
import logoImg from 'shared/images/logo.svg'
// then: <img src={logoImg} alt="..." />
```

**No existing `images/` subfolder** exists under `src/front/shared/pages/Apps/`. This directory currently contains only 4 files: `Apps.tsx`, `Apps.scss`, `appsCatalog.ts`, `walletBridge.ts`.

The `WalletApp` type has no `imageUrl` or `cardImage` field. To add card images, the type needs a new optional field (e.g. `iconImage?: string`) and the tile JSX in Apps.tsx needs an `<img>` element.

---

## 7. Integration Points

### externalConfig apps toggle (externalConfig.ts lines 139-143)
Default config has `apps.enabled: true`. Can be overridden via `window.SO_WalletAppsEnabled`. In E2E tests, explicitly set: `page.evaluate(() => { window.SO_WalletAppsEnabled = true })`.

### Header nav integration (config.tsx lines 140-149)
When `enabled`, the "Apps" nav item gets a `dropdown` array built from `walletAppsCatalog`. Adding new apps to catalog automatically populates the dropdown. No separate nav config needed.

### Redux connection
`Apps.tsx` connects to `user.ethData` (Ethereum address/currency) via `redaction` HOC. Passed to `createWalletAppsBridge` as `internalWallet`. No other Redux state consumed by Apps.

### Route
`/apps` and `/apps/:appId` — registered in `src/front/shared/routes/index.tsx`.

---

## 8. Shared Utilities Used

- `helpers/links` — `links.apps = '/apps'`, used for navigation
- `helpers/locale` — `localisedUrl(locale, path)` — wraps paths with locale prefix
- `helpers/metamask` — `metamask.isConnected()`, `metamask.getWeb3()`, `metamask.web3connect.on()` — used in walletBridge.ts

---

## 9. Potential Problems

### Adding new apps to E2E tests
The `APPS` array in `appsSmoke.test.js` is a plain-JS copy of the catalog. When adding a new app to `appsCatalog.ts`, the test file must be updated manually. There is no automatic sync.

### ALLOWED_HOSTS allowlist
New external apps require adding hostname to `EXTERNAL_ALLOWED_HOSTS` in `appsCatalog.ts`. Missing entry causes "Blocked by allowlist policy" error in the UI and `isAllowedWalletAppUrl` returns false.

### dex.onout.org domain detection
The bridge detects iframe mode via the `walletBridge=swaponline` query param on the DEX URL, not via domain detection in MCW. MCW-side domain detection is in `isAllowedWalletAppUrl` which checks `parsedUrl.hostname` against `EXTERNAL_ALLOWED_HOSTS`. If dex.onout.org is accessed at a non-standard subpath or the app redirects to a different origin internally, `event.origin !== targetOrigin` check in walletBridge.ts will block bridge messages.

### No card image field in WalletApp type
Currently there is no `iconImage`, `cardImage`, or `imageUrl` field in the type. Adding card images requires: (1) extend `WalletApp` type, (2) add images to `src/front/shared/pages/Apps/images/`, (3) import them in `appsCatalog.ts` or `Apps.tsx`, (4) update tile JSX to render `<img>` when field is set with fallback to existing `appIconFallback` span.

### Theme forwarding for internal apps
`isInternal: true` apps skip theme param injection entirely (Apps.tsx line 66: `if (selectedApp.isInternal) return base`). If internal route pages need theme info they must read `document.body.dataset.scheme` themselves.

### appsSmoke CI trigger scope
The CI workflow triggers on `Header/config.tsx` changes. Adding apps involves changing `appsCatalog.ts` — which IS covered by the `src/front/shared/pages/Apps/**` glob in `appsSmoke.yml`.

---

## 10. Constraints & Infrastructure

### Runtime feature flag
Apps feature requires `externalConfig.opts.ui.apps.enabled === true`. Default in `externalConfig.ts` is `true`. In E2E tests, `window.SO_WalletAppsEnabled = true` is set explicitly before navigating.

### Security sandbox
Iframe sandbox attribute (Apps.tsx line 135):
```
sandbox="allow-forms allow-same-origin allow-scripts allow-popups allow-popups-to-escape-sandbox"
```
`allow-same-origin` is required for `postMessage` bridge to work across frames on the same origin; for cross-origin iframes it simply means the iframe runs in its own origin context.

### Build commands
- `npm run build:testnet-tests` — builds testnet with `IS_TEST=true`, produces `build-testnet/index.html`
- `npm run test:e2e_apps_smoke` — runs with `ACTIONS=true --testEnvironment=node`
- CI uses `ubuntu-latest`, Node 18.20.0, installs Chromium via `npx puppeteer browsers install chrome`

### CSS modules
Apps.tsx uses `react-css-modules` with `CSSModules(Apps, styles, { allowMultiple: true })`. Style classes are referenced via `styleName=""` (not `className=""`). New tile image styles go in `Apps.scss`.

### Webpack import aliases
Image files imported in TypeScript must be declared. The project uses `file-loader` or similar for PNG/SVG (evidenced by existing imports in other components). No additional webpack config changes needed for standard image types.

---

## 11. Known Crypto Products (referenced in codebase)

| Product | Domain | Current Status |
|---|---|---|
| Onout DEX | `dex.onout.org` | In catalog, allowlisted, has bridge |
| PolyFactory | `polyfactory.wpmix.net` | In catalog, allowlisted, has bridge |
| Farm | `farm.wpmix.net` | Referenced in `links.ts` as `links.farm`, NOT in apps catalog |
| PredictionMarket | `predictionmarket.wpmix.net` | Referenced in docs README.md, NOT in apps catalog |
| wallet.wpmix.net | `wallet.wpmix.net` | Referenced in docs/links, NOT in apps catalog |

`farm.wpmix.net` and `predictionmarket.wpmix.net` and `wallet.wpmix.net` are candidates for adding to the catalog but are not currently in `EXTERNAL_ALLOWED_HOSTS`.
