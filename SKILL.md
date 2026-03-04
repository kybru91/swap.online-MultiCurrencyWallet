# SKILL.md — MCW Setup & Operations Guide

Полное руководство по локальной разработке, конфигурации и деплою MultiCurrencyWallet.

---

## Quick Start (Local Dev)

```bash
# 1. Установить зависимости
npm install

# 2. Запустить dev-сервер (testnet, localhost:9001)
npm run dev

# 3. Открыть браузер
open http://localhost:9001
```

Dev-сервер использует **testnet** конфиг. MetaMask нужно переключить на Sepolia/BSC Testnet.

---

## Config System

Конфиги выбираются через `CONFIG` env variable при сборке.

```
src/front/config/
├── mainnet/          # Production config
│   ├── evmNetworks.js  — chainId, networkVersion, chainName для каждой EVM-сети
│   ├── web3.js         — RPC endpoint URLs
│   ├── api.js          — Explorer API keys, WalletConnect Project ID, Infura key
│   └── link.js         — Block explorer URLs для отображения
└── testnet/          # Testnet config (same structure)
```

**Добавить новую EVM chain:**
1. `mainnet/web3.js` — добавь `newchain_provider: 'https://rpc.newchain.io'`
2. `mainnet/evmNetworks.js` — добавь объект с `currency, chainId, networkVersion, chainName, rpcUrls, blockExplorerUrls`
3. `mainnet/link.js` — добавь explorer URL
4. `mainnet/api.js` — добавь `newchainscan` URL и `newchain_ApiKey`
5. Повтори для `testnet/`
6. Проверь RPC: `curl -X POST <rpcUrl> -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}'`

**Текущие активные chains (mainnet):** ETH, BNB, MATIC, ARBETH, AURETH, XDAI, AVAX, MOVR, ONE, AME

---

## API Keys & Secrets

Все ключи зашиты в конфиге и попадают в бандл — это нормально для open-source crypto wallet.

| Ключ | Файл | Значение |
|------|------|---------|
| WalletConnect Project ID | `mainnet/api.js → WalletConnectProjectId` | `a23677c4af3139b4eccb52981f76ad94` |
| Infura API Key | `mainnet/api.js → InfuraApiKey` | `fdd4494101ed4a28b41bb66d7fe9c692` |
| Etherscan V2 API Key | `mainnet/api.js → etherscan_V2ApiKey` | `GK6YHJ5NMEF67R4FTRNQS2EK3HRBP5VVHW` |
| BSCScan API Key | `mainnet/api.js → bscscan_ApiKey` | `WI4QEJSV19U3TF2H1DPQ2HR6712HW4MYKJ` |

**GitHub Actions secret:** `buildbot` — SSH ключ для деплоя в `swaponline.github.io` репозиторий.

---

## Build Commands

```bash
npm run dev               # Dev server: testnet, localhost:9001 (hot reload)
npm run build:mainnet     # Production build (mainnet config, NODE_ENV=production)
npm run build:testnet     # Testnet build (for WordPress plugin testnet widget)
npm run build:wordpress   # WordPress plugin ZIP (mainnet + testnet widgets)
```

Webpack конфиг: `webpack/webpack.config.js` (common + dev/prod overlays).
**Важно:** `webpack/rules/jsx.js` должен использовать `babel-loader` (НЕ esbuild-loader — ломает react-css-modules).

---

## Testing

```bash
# Unit tests (Jest, jsdom environment)
npm run test:unit
npx jest tests/unit/walletConnect.test.ts   # 39 тестов WalletConnectProviderV2
npx jest tests/unit/appsCatalog.test.ts     # Тесты каталога dApps

# E2E smoke tests (требует запущенного dev-сервера на :9001)
npm run dev &
npm run test:e2e_apps_smoke                 # 9 Puppeteer тестов Apps
```

**E2E тесты медленные (~66 сек):** Puppeteer запускает Chromium, 9 тестов последовательно, каждый ждёт React init.

---

## Deployment

**Автоматический деплой:** push в `master` → GitHub Actions → build mainnet → push в `swaponline.github.io`.

**Ручная проверка после деплоя:**
```bash
curl -sf https://swaponline.github.io | grep -c "root" && echo "✅ site up" || echo "❌ site down"
```

**GitHub Actions workflows:**
| Workflow | Триггер | Что делает |
|----------|---------|------------|
| `deploymaster.yml` | push to master | Mainnet build → GitHub Pages |
| `deploy.yml` | push to master | WordPress plugin ZIP → farm.wpmix.net |
| `appsSmoke.yml` | changes in Apps/** | 9 E2E Puppeteer тестов |
| `rpc-healthcheck.yml` | cron (каждые 6ч) | Проверка всех 10 RPC endpoints |

**Rollback:**
```bash
git revert HEAD && git push   # Или: git reset --hard HEAD~1 && git push --force
```

---

## Wallet Connection Stack

MCW использует **Reown AppKit + Wagmi v2 + Viem** для подключения внешних кошельков.

```
@reown/appkit          — UI модал (MetaMask, WalletConnect, injected wallets)
@reown/appkit-adapter-wagmi — мост AppKit ↔ wagmi
wagmi v2 + viem        — EVM wallet state hooks, type-safe RPC
web3 v1.10             — atomic swap механика (отдельно от wallet connection)
```

Инициализация AppKit: `src/front/shared/lib/appkit.ts`
Хелпер для подключения: `src/front/shared/helpers/metamask.ts`
Header chip: `src/front/shared/components/Header/WalletChip/index.tsx`

---

## dApp Bridge (walletBridge)

Wallet может работать как EIP-1193 провайдер для внешних dApps через iframe + postMessage.

```
src/front/shared/pages/Apps/
├── appsCatalog.ts      — каталог dApps (URL, название, иконка, walletBridge: 'eip1193')
├── walletBridge.ts     — EIP-1193 bridge (postMessage с iframe)
└── images/             — PNG иконки для карточек dApps
```

**Добавить новый dApp:**
1. `appsCatalog.ts` — добавить объект в `APPS` массив
2. `EXTERNAL_ALLOWED_HOSTS` — добавить hostname
3. `tests/e2e/appsSmoke.test.js` — добавить в `APPS` массив (ручная синхронизация!)
4. Добавить `images/{app-id}.png`
5. Убедиться что dApp поддерживает bridge client (`wallet-bridge-init.js` в head)

---

## Atomic Swap Architecture

```
src/core/
├── swap.app/    — центральный синглтон, соединяет все сервисы
├── swap.auth/   — деривация ключей (BIP44) для каждого блокчейна
├── swap.room/   — libp2p pubsub (P2P order book)
├── swap.orders/ — управление ордерами
├── swap.swaps/  — blockchain-specific swap instances
└── swap.flows/  — HTLC протоколы (BTC↔ETH и другие пары)
```

P2P signaling сервер: `star.wpmix.net` (WebRTC STAR transport).

---

## WordPress Plugin

MCW можно встраивать в WordPress как виджет:

```bash
npm run build:wordpress   # Создаёт dist/wordpress-plugin/*.zip
```

Deploy: GitHub Actions `deploy.yml` пушит ZIP в `farm.wpmix.net/updates/` и обновляет `mcw-info.json`.
Плагин доступен на: https://farm.wpmix.net

---

## Pre-push Hook

`.git/hooks/pre-push` выполняет перед каждым push:
1. TypeScript compile check (`tsc --noEmit`)
2. ESLint на изменённых `.ts/.tsx` файлах (no-unused-vars, no-debugger, eqeqeq)
3. Prettier `--check` на изменённых файлах

При ошибке: `npx prettier --write <file>` для авто-форматирования.

---

## Key File Locations

| Что | Путь |
|-----|------|
| Точка входа React | `src/front/client/index.tsx` |
| AppKit init | `src/front/shared/lib/appkit.ts` |
| Redux store | `src/front/shared/redux/` |
| EVM chain конфиг | `src/front/config/mainnet/evmNetworks.js` |
| Header | `src/front/shared/components/Header/Header.tsx` |
| WalletChip | `src/front/shared/components/Header/WalletChip/index.tsx` |
| Apps каталог | `src/front/shared/pages/Apps/appsCatalog.ts` |
| walletBridge | `src/front/shared/pages/Apps/walletBridge.ts` |
| Webpack config | `webpack/webpack.config.js` |
| Jest config | `jest.config.js` |

**Обновлено:** 2026-03-04
