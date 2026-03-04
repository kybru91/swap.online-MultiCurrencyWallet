# Customization guide

## WordPress Customizer options

When using the **Multi Currency Wallet Pro** WordPress plugin, all design settings are configured through **Appearance → Customize → MCWallet Design**.

> These settings are stored as WordPress theme mods and injected as CSS variables at page load. They also automatically sync to the external WalletConnect/AppKit modal.

### MCWallet Design → Global Settings

| Setting | WP key | Type | Default | Description |
|---------|--------|------|---------|-------------|
| Button border radius | `button_border_radius` | number (rem) | `0` | Border-radius for all buttons. `0` = sharp, `0.5` = 8px, `1` = 16px. Applied as `--button-border-radius: Nrem`. Also synced to the WalletConnect modal. |
| Main component border radius | `main_component_border_radius` | number (rem) | `0` | Border-radius for cards and components. Applied as `--main-component-border-radius: Nrem`. |

### MCWallet Design → Site Color Scheme

| Setting | WP key | Choices | Default | Description |
|---------|--------|---------|---------|-------------|
| Color scheme | `color_scheme` | `light` / `dark` / `only_light` / `only_dark` | `light` | Initial theme. `only_*` disables the toggle. |

### MCWallet Design → Light Scheme / Dark Scheme

| Setting | WP key | Default (light) | Default (dark) |
|---------|--------|----------------|----------------|
| Text Color | `color_text` | `#000000` | `#ffffff` |
| Site Background | `color_background` | `#f7f7f7` | `#222427` |
| Brand Color | `color_brand` | `#6144e5` | `#6144e5` |
| Brand Color Hover | `color_brand_hover` | `#7371ff` | `#7371ff` |
| Brand Background | `color_brand_background` | `#6144e51a` | `#6144e51a` |

Dark variants use suffix `_dark` (e.g. `color_brand_dark`).

---

## Global `window.*` options (index.html / externalConfig)

For **standalone** (non-WordPress) deployments. Set in `src/front/client/index.html` or your domain config (e.g. `src/front/externalConfigs/your-domain.js`).

### Branding

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `window.brandColor` | `string` (hex/rgb) | `#6144e5` | Primary brand color. Applied to buttons, links, accents. Also synced to the external wallet modal (WalletConnect / AppKit). |
| `window.brandColorHover` | `string` (hex/rgb) | `#7371ff` | Hover state of the primary color. |
| `window.brandBorderRadius` | `string` (CSS value) | — | Border-radius for all UI elements (buttons, cards, components). Also applied to the external WalletConnect modal. Examples: `'0px'` — sharp, `'8px'` — rounded, `'20px'` — pill. **WordPress equivalent:** `button_border_radius` in the Customizer. |
| `window.logoUrl` | `string` (URL) | `'#'` | Logo image URL (light theme). |
| `window.darkLogoUrl` | `string` (URL) | `''` | Logo image URL for dark theme. Falls back to `logoUrl` if empty. |
| `window.loaderLogoUrl` | `string` (URL) | SwapOnline logo | Preloader spinner image URL. |
| `window.widgetName` | `string` | `'own widget name'` | Product name shown in "too many tabs" screen. |
| `window.defaultWindowTitle` | `string` | — | Browser tab title (widget mode only). |

### Fiat & Regional

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `window.DEFAULT_FIAT` | `string` | `'USD'` | Default fiat currency for balance display. |
| `window.DefaultCountryCode` | `string` | `'+1'` | Default phone country code. |
| `window.buyViaCreditCardLink` | `string` (URL) | `'https://buy.itez.com/'` | URL for "buy via credit card" button. |

### Exchange & Behavior

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `window.EXCHANGE_DISABLED` | `boolean` | `false` | Disable the exchange tab entirely. |
| `window.CUR_NEXT_DISABLED` | `boolean` | — | Disable NEXT currency. Set to `false` to enable. |
| `window.SWAP_HIDE_EXPORT_PRIVATEKEY` | `boolean` | `false` | Hide "Export private key" option. |
| `window.invoiceEnabled` | `boolean` | `false` | Enable invoice creation feature. |
| `window.hideServiceLinks` | `boolean` | `false` | Hide external service links in UI. |
| `window.showHowItWorksOnExchangePage` | `boolean` | `false` | Show "How it works" block on exchange page. |
| `window.isUserRegisteredAndLoggedIn` | `boolean` | `false` | Show exit/logout button (set to `true` in embedded widget). |

### WalletConnect & Web3

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `window.SO_WalletConnectProjectId` | `string` | built-in key | Your WalletConnect Cloud Project ID from [cloud.reown.com](https://cloud.reown.com). |
| `window.SO_WalletConnectDisabled` | `boolean` | `false` | Disable WalletConnect entirely. |
| `window.SO_INFURA_API_KEY` | `string` | — | Infura API key for RPC calls. |
| `window.SO_AllowMultiTab` | `boolean` | `false` | Allow opening wallet in multiple browser tabs simultaneously. |
| `window.SO_disableInternalWallet` | `boolean` | `false` | Disable internal (seed-based) wallet, use only external wallets. |

### UI & Navigation

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `window.SO_MenuItemsBefore` | `Array<MenuItem>` | `[]` | Custom menu items inserted **before** the default menu. |
| `window.SO_MenuItemsAfter` | `Array<MenuItem>` | `[]` | Custom menu items inserted **after** the default menu. |
| `window.SO_FaqBeforeTabs` | `Array<FaqItem>` | `[]` | FAQ items inserted **before** the default tabs. |
| `window.SO_FaqAfterTabs` | `Array<FaqItem>` | `[]` | FAQ items inserted **after** the default tabs. |
| `window.SO_WalletAppsEnabled` | `boolean` | `true` | Show/hide the Apps section in the wallet. |
| `window.SO_AppsHeaderPinned` | `string[]` | `[]` | Array of app IDs to pin in the header. |
| `window.SO_ReplaceExchangeWithAppId` | `string` | — | Replace the Exchange tab with a specific app by ID. |

### Coins & Wallets

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `window.SO_defaultQuickBuy` | `string` | — | Default quick-buy coin pair (e.g. `'eth'`). |
| `window.SO_defaultQuickSell` | `string` | — | Default quick-sell coin pair (e.g. `'btc'`). |
| `window.SO_createWalletCoinsOrder` | `string[]` | — | Order of coins shown in "Create wallet" screen. |
| `window.SO_addAllEnabledWalletsAfterRestoreOrCreateSeedPhrase` | `boolean` | `false` | Auto-add all enabled wallets after seed restore/create. |
| `window.SO_fiatBuySupperted` | `string[]` | — | List of fiat currencies supported for buy (e.g. `['USD','EUR']`). |
| `window.widgetEvmLikeTokens` | `Token[]` | `[]` | Custom EVM tokens list. See structure below. |
| `window.widgetERC20Comisions` | `object` | — | ERC20 fee config. Keys are token symbols, values are `{fee, address, min}`. |

### Plugins

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `window.backupPlugin` | `boolean` | `false` | Enable backup plugin. |
| `window.backupUrl` | `string` (URL) | — | URL for backup endpoint. |
| `window.restoreUrl` | `string` (URL) | — | URL for restore endpoint. |
| `window.setItemPlugin` | `function` | — | Override `localStorage.setItem` (custom storage backend). |
| `window.getItemPlugin` | `function` | — | Override `localStorage.getItem` (custom storage backend). |
| `window.userDataPluginApi` | `object` | — | User data API plugin for server-side storage. |

### WordPress Integration

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `window.WPuserHash` | `string` | — | WordPress user hash for authentication. |
| `window.WPuserUid` | `string` | — | WordPress user ID. |

### Bulk Config Override

```js
window.buildOptions = {
  showWalletBanners: true,        // Show promotional banners
  showHowItsWork: true,           // Show "How it works" on exchange page
  addCustomTokens: true,          // Allow users to add custom EVM tokens
  ownTokens: false,               // Use only widgetEvmLikeTokens (no built-in tokens)
  invoiceEnabled: false,          // Enable invoice creation
  exchangeDisabled: false,        // Disable exchange tab
  curEnabled: {                   // Enable specific blockchains (false = all enabled)
    btc: true,
    eth: true,
    bnb: true,
    matic: true,
  },
  blockchainSwapEnabled: {        // Enable atomic swaps per blockchain
    btc: true,
    eth: false,
  },
  defaultExchangePair: {          // Default trading pair on exchange page
    buy: 'eth',
    sell: 'btc',
  },
}
```

### Token config structure (`window.widgetEvmLikeTokens`)

```js
window.widgetEvmLikeTokens = [
  {
    standard: 'erc20',            // 'erc20' | 'bep20' | 'erc20matic' | etc.
    address: '0xdac17f...',       // Contract address
    decimals: 6,
    symbol: 'USDT',
    fullName: 'Tether USD',
    icon: 'https://example.com/usdt.png',
    iconBgColor: '#26a17b',       // Optional icon background
    customExchangeRate: 1.0,      // Optional fixed USD price
    howToDeposit: 'Send USDT...',
    howToWithdraw: 'Withdraw...',
  },
]
```

---

## 1. Change logo

- copy svg logos to `src/front/shared/components/Logo/images`
- in `src/front/client/index.html` set:

```js
window.logoUrl = 'https://example.com/logo.svg'
window.darkLogoUrl = 'https://example.com/logo-dark.svg'
window.loaderLogoUrl = 'https://example.com/loader.svg'
```

- change Cryptocurrency icon in `src/front/shared/components/ui/CurrencyIcon/images`
  (same filename as the coin, e.g. `bitcoin.svg`)


## 2. Change links to social networks

Set your own links in `src/front/shared/helpers/links.js`


## 3. Change text / translations

- find text like `<FormattedMessage id="Row313" defaultMessage="Deposit" />`
- open `src/front/shared/localisation/en.json`, find by `"id": "Row313"`
- change `"message"` value


## 4. Add your ERC20 token

- `src/front/config/mainnet/erc20.js` — add token
- `src/core/swap.app/constants/COINS.js` — add coin constant
- `src/front/shared/redux/reducers/currencies.js` — add to reducer


## 5. Add token to "Create wallet" screen

In `src/front/shared/redux/reducers/currencies.js` set `addAssets: true` for your token.


## 6. Change product name

In `index.html` set `window.widgetName = 'Your Product Name'` — shown in multi-tab error screen.


## 7. Change browser tab title

In `index.html` set `window.defaultWindowTitle = 'Your Title'` (widget mode only).


## 8. Change primary color and border-radius

In `index.html` (or your domain config):

```js
window.brandColor = '#6144e5'         // Primary brand color
window.brandColorHover = '#7371ff'    // Hover state color
window.brandBorderRadius = '8px'      // Rounded corners (0px = sharp, 20px = pill)
```

These are applied to all UI elements **and** to the external wallet connection modal (WalletConnect/AppKit).


## 9. Enable/disable blockchains

Add a config for your domain in `src/front/externalConfigs/your-domain.js`:

```js
window.buildOptions = {
  showWalletBanners: true,
  curEnabled: {
    btc: true,
    eth: true,
    bnb: true,
  },
}
```

Example: [swaponline.github.io.js](https://github.com/swaponline/MultiCurrencyWallet/blob/master/src/front/externalConfigs/swaponline.github.io.js)


## 10. Add exit button to your widget

In `index.html` set `window.isUserRegisteredAndLoggedIn = true` — shows the exit/logout button.


## 11. Set custom exchange rate for your token

```js
window.widgetEvmLikeTokens = [
  {
    // ...
    customExchangeRate: 1.5,  // Fixed USD price
  }
]
```


## How to update your fork to latest version

0. `git push` all your changes
1. Go to https://github.com/swaponline/MultiCurrencyWallet/compare?expand=1 → **Compare across forks**
2. Select your repo as base branch
3. **Create pull request** → **Merge pull request**

Resolve conflicts if sources changed on your side.
