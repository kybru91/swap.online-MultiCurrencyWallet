# Execution Plan: Native Mobile Wallet (Android)

**Создан:** 2026-03-04
**Фича:** work/native-mobile-wallet
**Branch:** feature/native-mobile-wallet

---

## Статус завершённых задач

Tasks 1-5 — DONE (Android project, BIP39/BIP44 crypto, SecureStorage, Wallet UI create/import, Network layer)

---

## Wave 4 (текущая — tasks 6 + 7, параллельно)

### Task 6: BTC Operations (Balance + Transactions)
- **Skill:** code-writing
- **Reviewers:** code-reviewer, security-auditor, test-reviewer
- **Verify:** bash — `cd android && ./gradlew test`
- **Что делает:** BtcManager — fetchBalance, UTXO selection, fee estimation, tx build/sign/broadcast

### Task 7: EVM Operations (Balance + Transactions + Fiat)
- **Skill:** code-writing
- **Reviewers:** code-reviewer, security-auditor, test-reviewer
- **Verify:** bash — `cd android && ./gradlew test`
- **Что делает:** EvmManager — eth_getBalance, ERC20, CoinGecko, gas estimation, tx sign/broadcast

---

## Wave 5 (после Wave 4 — task 8)

### Task 8: Wallet UI + Navigation
- **Skill:** code-writing
- **Reviewers:** code-reviewer, test-reviewer
- **Verify:** bash — `cd android && ./gradlew test`
- **Что делает:** MainActivity, Compose NavHost, onboarding flow, balance list, pull-to-refresh, offline mode

---

## Wave 6 (после Wave 5 — tasks 9 + 10 + 11, параллельно)

### Task 9: Send Transaction UI
- **Skill:** code-writing
- **Reviewers:** code-reviewer, security-auditor, test-reviewer
- **Verify:** bash — `cd android && ./gradlew test`
- **Что делает:** SendScreen, SendViewModel, confirmation dialog с биометрикой, duplicate prevention

### Task 10: dApp Browser + window.ethereum Provider
- **Skill:** code-writing
- **Reviewers:** code-reviewer, security-auditor, test-reviewer
- **Verify:** bash — `cd android && ./gradlew test`
- **Что делает:** WebView EIP-1193, JS-Native bridge, origin validation, rate limiting, tx confirmation dialog

### Task 11: WalletConnect v2 Integration
- **Skill:** code-writing
- **Reviewers:** code-reviewer, security-auditor, test-reviewer
- **Verify:** bash — `cd android && ./gradlew test`
- **Что делает:** WC v2 wallet SDK, QR scanner, session management (24h), signing dialogs

---

## Wave 7 (после Wave 6 — tasks 12 + 13, параллельно)

### Task 12: Transaction History
- **Skill:** code-writing
- **Reviewers:** code-reviewer, test-reviewer
- **Verify:** bash — `cd android && ./gradlew test`
- **Что делает:** Blockcypher (BTC) + Etherscan (EVM) history fetch, direction parsing, list UI

### Task 13: Settings, White-label, Crashlytics
- **Skill:** code-writing
- **Reviewers:** code-reviewer, infrastructure-reviewer
- **Verify:** bash — `cd android && ./gradlew test`
- **Что делает:** Custom RPC URLs, network selector, white-label config, Firebase Crashlytics (secret-safe)

---

## Wave 8 — Final (task 14)

### Task 14: Pre-deploy QA
- **Skill:** pre-deploy-qa
- **Reviewers:** (none)
- **Verify:** bash — `cd android && ./gradlew test && ./gradlew assembleRelease`
- **Что делает:** Запуск всех тестов, проверка AC из user-spec/tech-spec, build release APK

---

## Проверки, требующие участия пользователя

- [ ] После Wave 8: установить APK на устройство и проверить create/import wallet + отправку транзакции
- [ ] После Wave 8: проверить dApp Browser на реальном dApp (app.uniswap.org)
- [ ] После Wave 8: проверить WalletConnect v2 через QR-код

---

## Итого

- **Волн:** 5 (4–8)
- **Задач:** 9 (tasks 6–14)
- **Параллельно в пике:** 3 агента (Wave 6)
