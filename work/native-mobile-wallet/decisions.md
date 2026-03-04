# Decisions Log: native-mobile-wallet

Отчёты агентов о выполнении задач. Каждая запись создаётся агентом, выполнившим задачу.

---

<!-- Записи добавляются агентами по мере выполнения задач.

Формат строгий — используй только эти секции, не добавляй другие.
Не включай: списки файлов, таблицы файндингов, JSON-отчёты, пошаговые логи.
Детали ревью — в JSON-файлах по ссылкам. QA-отчёт — в logs/working/.

## Task N: [название]

**Status:** Done
**Commit:** abc1234
**Agent:** [имя тиммейта или "основной агент"]
**Summary:** 1-3 предложения: что сделано, ключевые решения. Не список файлов.
**Deviations:** Нет / Отклонились от спека: [причина], сделали [что].

**Reviews:**

*Round 1:*
- code-reviewer: 2 findings → [logs/working/task-N/code-reviewer-1.json]
- security-auditor: OK → [logs/working/task-N/security-auditor-1.json]

*Round 2 (после исправлений):*
- code-reviewer: OK → [logs/working/task-N/code-reviewer-2.json]

**Verification:**
- `npm test` → 42 passed
- Manual check → OK

-->

## Task 1: Android Project Setup

**Status:** Done
**Commit:** 7450be9f0, 268eca7c6
**Agent:** project-setup-android
**Summary:** Created multi-module Android Gradle project in android/ with 9 modules (:app, :core:crypto, :core:storage, :core:auth, :core:network, :core:btc, :core:evm, :feature:dapp-browser, :feature:walletconnect). Configured Hilt DI, Jetpack Compose BOM 2024.02.00, all dependencies from tech-spec, network_security_config.xml with cleartextTrafficPermitted=false, GitHub Actions CI, and BouncyCastle resolution strategy (bcprov-jdk18on:1.77) to resolve bitcoinj/web3j conflict.
**Deviations:** WalletConnect sign version changed from spec 2.28.0 to 2.31.0 because 2.28.0 does not exist on Maven Central. Closest compatible version used.

**Reviews:**

*Round 1:*
- code-reviewer: 4 findings (2 low, 2 info) → [logs/working/task-1/code-reviewer-round1.json]
- security-auditor: 5 findings (all positive/info) → [logs/working/task-1/security-auditor-round1.json]
- infrastructure-reviewer: 5 findings (1 medium accepted, 2 low, 2 info) → [logs/working/task-1/infrastructure-reviewer-round1.json]

*Round 2 (after fixes):*
- code-reviewer: OK → [logs/working/task-1/code-reviewer-round2.json]
- security-auditor: OK → [logs/working/task-1/security-auditor-round2.json]
- infrastructure-reviewer: OK → [logs/working/task-1/infrastructure-reviewer-round2.json]

**Verification:**
- `./gradlew assembleDebug` → BUILD SUCCESSFUL, APK produced (67MB)
- network_security_config.xml present with cleartextTrafficPermitted=false
- All 9 modules defined in settings.gradle.kts
- AndroidManifest.xml references networkSecurityConfig

## Task 2: Crypto Core — BIP39/BIP44 Key Derivation

**Status:** Done
**Commit:** e4d1cc396, 5b5df2355 (review fixes folded into task 3 commit)
**Agent:** crypto-engineer
**Summary:** Implemented CryptoManager in :core:crypto with BIP39 mnemonic generation/validation (bitcoinj) and BIP44 key derivation for BTC (P2PKH at m/44'/0'/0'/0/0) and ETH (EIP-55 checksummed at m/44'/60'/0'/0/0, shared across all EVM chains). Cross-platform validated against web wallet using two known mnemonics ("abandon" and "zoo" vectors). Key security improvements from review: entropy/seed zeroed after use, WalletKeys.toString() redacts sensitive fields.
**Deviations:** None. All derivation paths, address formats, and test vectors match web wallet output exactly.

**Reviews:**

*Round 1:*
- code-reviewer: 4 findings (1 minor, 2 low, 1 info) → [logs/working/task-2/code-reviewer-task2-round1.json]
- security-auditor: 4 findings (2 minor, 2 info) → [logs/working/task-2/security-auditor-task2-round1.json]
- test-reviewer: 4 findings (1 minor, 1 low, 2 info) → [logs/working/task-2/test-reviewer-task2-round1.json]

*Round 2 (after fixes):*
- code-reviewer: OK → [logs/working/task-2/code-reviewer-task2-round2.json]
- security-auditor: OK → [logs/working/task-2/security-auditor-task2-round2.json]
- test-reviewer: OK → [logs/working/task-2/test-reviewer-task2-round2.json]

**Verification:**
- `./gradlew :core:crypto:test` → BUILD SUCCESSFUL, 31 tests passed (0 failures)
- Known mnemonic "abandon...about" → BTC 1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA, ETH 0x9858EfFD232B4033E47d90003D41EC34EcaEda94 (matches web wallet)
- Known mnemonic "zoo...wrong" → BTC 1EjnS13zBgN6tUgy6U64qFeh53fyAeUsqE, ETH 0xfc2077CA7F403cBECA41B1B0F62D91B5EA631B5E (matches web wallet)

## Task 3: Secure Storage + App Password

**Status:** Done
**Commit:** 5c357ef08, e83d6f5b9
**Agent:** storage-engineer
**Summary:** Implemented SecureStorage class in :core:storage wrapping EncryptedSharedPreferences (AES256-GCM values, AES256-SIV keys, Android KeyStore MasterKey). Stores mnemonic (space-separated), BTC/ETH private keys, bcrypt password hash, WalletConnect sessions, and active chain ID. KeyStore corruption detected via dual SecurityException/GeneralSecurityException catch, clears all storage and throws KeyStoreCorruptionException. Used private primary constructor + @Inject secondary constructor pattern for Hilt DI with internal createForTesting() factory for unit test access.
**Deviations:** None. All 6 storage keys and corruption handling match task spec exactly.

**Reviews:**

*Round 1:*
- code-reviewer: 1 low finding (unused import, fixed) → [logs/working/task-3/code-reviewer-task3-round1.json]
- security-auditor: OK (all positive/info) → [logs/working/task-3/security-auditor-task3-round1.json]
- test-reviewer: OK (22/22 tests, all TDD anchors present) → [logs/working/task-3/test-reviewer-task3-round1.json]

**Verification:**
- `./gradlew :core:storage:test` → BUILD SUCCESSFUL, 22 tests passed (debug + release)
- KeyStore corruption tests pass with "Wallet data corrupted, please reimport your seed phrase" message verified
- Password hash test verifies $2a$12$ prefix

## Task 5: Network Layer + API Failover

**Status:** Done
**Commit:** 83b3d1919, 2cc9546b6
**Agent:** network-engineer
**Summary:** Implemented OkHttp-based network layer in :core:network with ApiFailoverInterceptor (round-robin endpoint switching ported from web's apiLooper.ts), 500ms request queuing between retries, in-memory EndpointHealthTracker, RPC URL validation (HTTPS-only + private IP blocking), Retrofit interfaces for Bitpay/Etherscan/Blockcypher/CoinGecko, ApiConfig data classes with endpoints extracted from web config, and Hilt DI module. All API keys extracted from web config as constants per Decision 10.
**Deviations:** None. Implementation matches tech-spec requirements exactly.

**Reviews:**

*Round 1:*
- code-reviewer: 4 findings (2 low fixed, 2 info accepted) -> [logs/working/task-5/code-reviewer-task5-round1.json]
- security-auditor: 3 findings (1 minor fixed, 2 info accepted) -> [logs/working/task-5/security-auditor-task5-round1.json]
- test-reviewer: OK (all 7 TDD anchors pass) -> [logs/working/task-5/test-reviewer-task5-round1.json]

*Round 2 (after fixes):*
- code-reviewer: OK -> [logs/working/task-5/code-reviewer-task5-round2.json]
- security-auditor: OK -> [logs/working/task-5/security-auditor-task5-round2.json]
- test-reviewer: OK -> [logs/working/task-5/test-reviewer-task5-round2.json]

**Verification:**
- `./gradlew :core:network:test` -> BUILD SUCCESSFUL, 50 tests passed (0 failures)
- `./gradlew assembleDebug` -> BUILD SUCCESSFUL
- All 7 TDD anchors pass: failover switches endpoint, 500ms queuing verified, health tracking works, HTTPS allowed, HTTP rejected, private IP rejected, localhost rejected

## Task 6: BTC Operations (Balance + Transactions)

**Status:** Done
**Commit:** ab7b792a2, cd67eab03
**Agent:** btc-engineer
**Summary:** Implemented BtcManager in :core:btc with full BTC transaction lifecycle: balance fetching (Bitpay API, satoshis-to-BTC BigDecimal conversion), UTXO selection (ported from web's btc.ts prepareUnspents: sort ascending, try single UTXO, accumulate from smallest), fee estimation (Blockcypher API with default rate fallback from DEFAULT_CURRENCY_PARAMETERS), P2PKH transaction construction and signing (bitcoinj Transaction class), dust change absorption (change < 546 sat added to fee), and broadcast (Bitpay POST /tx/send). All constants (DUST_SAT=546, P2PKH_IN_SIZE=148, P2PKH_OUT_SIZE=34, TX_SIZE=15) match web wallet's TRANSACTION.ts.
**Deviations:** None. Algorithm, constants, and API endpoints match web wallet implementation exactly.

**Reviews:**

*Round 1:*
- code-reviewer-6: 2 low findings (unused imports, fixed) + 2 info -> [logs/working/task-6/code-reviewer-6-round1.json]
- security-auditor-6: OK (4 info, no vulnerabilities) -> [logs/working/task-6/security-auditor-6-round1.json]
- test-reviewer-6: 2 low findings (tx tests strengthened with deserialization assertions, fixed) + 3 info -> [logs/working/task-6/test-reviewer-6-round1.json]

**Verification:**
- `./gradlew :core:btc:test` -> BUILD SUCCESSFUL, 33 tests passed (debug + release, 0 failures)
- All 7 TDD anchors pass: fetchBalance, utxoSelectionSingleInput, utxoSelectionMultipleInputs, utxoSelectionInsufficientFunds, changeHandlingDust, feeCalculation, transactionConstruction

## Task 7: EVM Operations — Balance, ERC20, Gas, Broadcast

**Status:** Done
**Commit:** b0d3a9c40, 3def5621d
**Agent:** evm-engineer-7
**Summary:** Implemented EvmManager in :core:evm with full EVM operations: native balance via eth_getBalance, ERC20 token balance via ABI-encoded balanceOf call, CoinGecko fiat price parsing, gas estimation with 1.05x buffer for tokens (matching web wallet's ethLikeAction.ts), RawTransaction building, EIP-155 signing via web3j, and eth_sendRawTransaction broadcast. All network methods return null on failure (offline mode). Added @Inject constructor for Hilt DI, hex input validation on broadcast, and GasEstimate data class.
**Deviations:** None. Gas buffer (1.05x for tokens, no buffer for native) matches web wallet exactly. CoinGecko ID mapping covers BTC/ETH/BNB/MATIC per spec.

**Reviews:**

*Round 1:*
- code-reviewer: 4 findings (1 minor, 3 low) -> [logs/working/task-7/code-reviewer-7-round1.json]
- security-auditor: 4 findings (1 medium, 2 low, 1 info) -> [logs/working/task-7/security-auditor-7-round1.json]
- test-reviewer: 4 findings (2 low, 2 info) -> [logs/working/task-7/test-reviewer-7-round1.json]

All actionable findings addressed in fix commit 3def5621d.

**Verification:**
- `./gradlew :core:evm:test` -> BUILD SUCCESSFUL, 38 tests passed (0 failures)
- Balance parsing: 1 ETH = 1e18 wei, USDT 6 decimals, WBTC 8 decimals, standard 18 decimals
- Gas buffer: 50000 * 1.05 = 52500 for tokens, 21000 unchanged for native
- Offline mode: all 3 network methods (balance, token balance, gas) return null on error
- Fiat: 1.5 * $50000 = $75000.00
- EIP-155 signing produces different signatures for different keys

## Task 8: Wallet UI + Navigation

**Status:** Done
**Commit:** bef60aeac, 52b723e88
**Agent:** ui-engineer
**Summary:** Implemented Compose single-activity architecture with NavHost, bottom navigation (Wallet/dApps tabs), OnboardingViewModel with seed confirmation retry logic (3 random words, max 3 attempts then reset), WalletViewModel with pull-to-refresh balance display and offline mode state, FLAG_SECURE on mnemonic/seed screens, and stub screens for Send/History/Settings/dApp Browser. ViewModels are plain classes (not @HiltViewModel) because CryptoManager lacks @Inject constructor; the NavHost uses OnboardingPlaceholder pending assisted injection setup. EvmBalanceFetcher interface introduced to decouple app module from web3j dependency.
**Deviations:** ViewModels are not @HiltViewModel -- CryptoManager and BtcManager have non-injectable constructor params (NetworkParameters). Onboarding NavHost destination uses placeholder composable instead of real OnboardingScreen. WalletScreen uses state hoisting instead of internal ViewModel. These will be wired via assisted injection in integration phase. Used compose-material pullRefresh (deprecated) instead of Material3 PullToRefreshBox because BOM 2024.02.00 does not include it.

**Reviews:**

*Round 1:*
- code-reviewer-8: 1 low, 3 info -> [logs/working/task-8/code-reviewer-8-round1.json]
- test-reviewer-8: 1 low, 3 info -> [logs/working/task-8/test-reviewer-8-round1.json]

*Round 2 (after fixes):*
- code-reviewer-8: OK -> [logs/working/task-8/code-reviewer-8-round2.json]
- test-reviewer-8: OK -> [logs/working/task-8/test-reviewer-8-round2.json]

**Verification:**
- `./gradlew :app:testDebugUnitTest` -> BUILD SUCCESSFUL, 21 tests passed (15 onboarding + 8 wallet, debug variant; same on release)
- `./gradlew :app:lintDebug` -> BUILD SUCCESSFUL, 0 errors
- Seed confirmation: 3 failures reset to mnemonic display, attempt counter resets to 0
- Offline mode: retain previous balances, show error banner, disable Send, clear on reconnect
- Password validation: 8+ chars required, mismatch detected, bcrypt $2a$12$ hash verified

## Task 11: WalletConnect v2 Integration

**Status:** Done
**Commit:** d88a618c9, 2b37b9ac3
**Agent:** walletconnect-engineer
**Summary:** Implemented WalletConnect v2 wallet-side SDK integration in :feature:walletconnect with URI parsing (no Android framework dependency for testability), relay server validation (only relay.walletconnect.com / relay.walletconnect.org), session lifecycle management (pair, approve, reject, remove), 24-hour session expiry with cleanup on app launch, JSON serialization via org.json, and TimeProvider abstraction for deterministic testing. 66 unit tests cover all 5 TDD anchors.
**Deviations:** None. Relay validation, session expiry (24h), persistence via SecureStorage, and error handling all match tech-spec exactly.

**Reviews:**

*Round 1:*
- code-reviewer: 2 low findings (stale KDoc reference, redundant validation), 2 info -> [logs/working/task-11/code-reviewer-11-round1.json]
- security-auditor: OK (4 info, no vulnerabilities) -> [logs/working/task-11/security-auditor-11-round1.json]
- test-reviewer: OK (4 info, all TDD anchors pass) -> [logs/working/task-11/test-reviewer-11-round1.json]

*Round 2 (after fixes):*
- code-reviewer: OK -> [logs/working/task-11/code-reviewer-11-round2.json]

**Verification:**
- `./gradlew :feature:walletconnect:test` -> BUILD SUCCESSFUL, 66 tests passed (debug + release, 0 failures)
- Session expiry: 25h old session removed, 23h59m retained, exactly 24h removed
- Relay validation: walletconnect.com allowed, custom.relay.com rejected, subdomain attacks rejected
- Persistence: approve session -> restart -> session restored from EncryptedSharedPreferences

## Task 9: Send Transaction UI

**Status:** Done
**Commit:** d96b35c1d, 71a46b7da
**Agent:** send-engineer
**Summary:** Implemented send transaction screen replacing SendStubScreen with full flow: SendViewModel state machine (Idle -> Building -> Confirming -> Submitting -> Success/Error), address validation (BTC P2PKH regex + EVM EIP-55 checksum warning), amount validation (> 0, <= balance), fee tier selector (BTC: Blockcypher fast/normal/slow sat/KB, EVM: 1.5x/1.0x/0.8x gas price multipliers), duplicate submission prevention (button disabled during Submitting state), and ConfirmationDialog composable. Introduced EvmSendHelper and AddressChecksumProvider interfaces to decouple app module from web3j dependency (same pattern as WalletViewModel's EvmBalanceFetcher). DefaultAddressChecksumProvider implements EIP-55 using BouncyCastle's KeccakDigest.
**Deviations:** BiometricPrompt integration point defined (confirmSend called after biometric success) but actual BiometricPrompt trigger is deferred to Activity layer since core:auth's AuthManager is still a placeholder. Added self-send prevention (not in original spec but critical for fund safety).

**Reviews:**

*Round 1:*
- code-reviewer-9: 3 findings (unused param, self-send, bounds check), all fixed -> [logs/working/task-9/code-reviewer-9-round1.json]
- security-auditor-9: 1 finding (self-send prevention), fixed -> [logs/working/task-9/security-auditor-9-round1.json]
- test-reviewer-9: OK (42/42 tests pass, all TDD anchors covered) -> [logs/working/task-9/test-reviewer-9-round1.json]

**Verification:**
- `./gradlew :app:testDebugUnitTest` -> BUILD SUCCESSFUL, 64 tests passed (42 send + 13 onboarding + 8 wallet + 1 smoke)
- `./gradlew :app:lintDebug` -> BUILD SUCCESSFUL, 0 errors
- Address validation: BTC P2PKH 26-35 chars alphanumeric, EVM 0x+40 hex with EIP-55 checksum
- State machine: Idle -> Building -> Confirming -> Submitting -> Success (BTC + EVM paths tested)
- Duplicate prevention: isSubmitting=true during broadcast, re-enabled on error
- Self-send prevention: "Cannot send to your own address" error for same-address send

## Task 10: dApp Browser + window.ethereum EIP-1193 Provider

**Status:** Done
**Commit:** b476afd74, a42dd7b7d
**Agent:** dapp-engineer
**Summary:** Implemented WebView-based dApp browser in :feature:dapp-browser with injected window.ethereum EIP-1193 provider (Object.freeze for tamper protection), JS-to-Native bridge via single @JavascriptInterface request() method, origin validation per call, rate limiting (10 calls/sec atomic sliding window), domain allowlist policy (unknown domains blocked), gas thresholds (>1M warning, >15M reject), transaction confirmation dialog with function signature decoding (transfer/approve/swap), unlimited approval (MAX_UINT256) warnings, eth_sign rejection, wallet_addEthereumChain chain ID allowlist (1/56/137), WebView security hardening per Decision 9, EIP-6963 provider announcement, and URL validation (HTTPS only, private IPs blocked).
**Deviations:** None. All security settings, method support, chain restrictions, and gas thresholds match tech-spec Decision 9 exactly.

**Reviews:**

*Round 1:*
- code-reviewer: 2 findings (1 minor bridge reference fix, 1 low unused imports), 2 info -> [logs/working/task-10/code-reviewer-10-round1.json]
- security-auditor: 2 findings (1 minor event name injection fix, 1 minor error message escaping), 4 info -> [logs/working/task-10/security-auditor-10-round1.json]
- test-reviewer: OK (4 info, all 7 TDD anchors pass) -> [logs/working/task-10/test-reviewer-10-round1.json]

All findings fixed in commit a42dd7b7d.

**Verification:**
- `./gradlew :feature:dapp-browser:test` -> BUILD SUCCESSFUL, 70 tests passed (35 debug + 35 release, 0 failures)
- All 7 TDD anchors pass: injection (Object.freeze + window.ethereum), origin validation, rate limiting (15 calls -> 5 queued), gas warning (1M+1), gas reject (15M+1), eth_sign rejection, chain allowlist (1/56/137 allowed, 999 rejected)
- WebView security: allowFileAccess=false, mixedContentMode=MIXED_CONTENT_NEVER_ALLOW, geolocationEnabled=false confirmed
- URL validation: https allowed, http/file/javascript/data blocked, private IPs blocked
- Function decoding: transfer/approve/swap selectors decoded, MAX_UINT256 unlimited approval detected
