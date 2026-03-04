# Pre-deploy QA Report: Native Mobile Wallet (Android)

**Date:** 2026-03-05
**Agent:** qa-engineer
**Task:** 14 (Wave 8)
**Branch:** feature/native-mobile-wallet
**Status:** FAILED (2 critical findings)

---

## Test Suite

**Result:** ALL 457 UNIQUE TESTS PASSED (0 failures, 0 skipped)

| Module | Tests | Status |
|--------|-------|--------|
| :app | 113 | PASS |
| :core:crypto | 31 | PASS |
| :core:btc | 50 | PASS |
| :core:evm | 55 | PASS |
| :core:network | 50 | PASS |
| :core:storage | 22 | PASS |
| :feature:dapp-browser | 70 | PASS |
| :feature:walletconnect | 66 | PASS |
| :core:auth | 0 | N/A (stub module) |
| **Total** | **457** | **PASS** |

Both debug and release test variants pass (914 total test executions including both variants).

## Build Results

| Build | Status | Notes |
|-------|--------|-------|
| `assembleDebug` | PASS | app-debug.apk (64 MB) |
| `assembleRelease` | **FAIL** | R8 error: missing `-dontwarn org.slf4j.impl.StaticLoggerBinder` |
| `lint` | PASS | 0 errors, 50 warnings (non-blocking) |

## Critical Findings

### CRITICAL 1: Task 4 (Biometric Auth + Auto-lock) NOT IMPLEMENTED

`core:auth/AuthManager.kt` is an empty stub class (7 bytes). Task 4 is marked `status: done` in its frontmatter and has review reports (commit dc8754c0c), but there is no implementation commit. No decisions.md entry for Task 4 exists.

**Missing features:**
- BiometricPrompt integration
- Password verification against bcrypt hash
- Exponential lockout (5 failed attempts -> 60s/120s/300s)
- Auto-lock (5-minute inactivity, 30-second background)
- LockState sealed class
- 0 unit tests for auth module

**Impact:** All authentication-related acceptance criteria fail (AC-7, AC-28, AC-36, AC-37, AC-42). The app has no lock screen, no biometric prompt, and no protection against unauthorized access.

### CRITICAL 2: Release APK Build Fails

`./gradlew assembleRelease` fails at `:app:minifyReleaseWithR8` due to missing class `org.slf4j.impl.StaticLoggerBinder` (referenced by bitcoinj/web3j dependencies).

**Fix:** Add `-dontwarn org.slf4j.impl.StaticLoggerBinder` to `app/proguard-rules.pro`. This is a 1-line fix confirmed by the R8-generated `missing_rules.txt`.

### MAJOR: core:auth Module Has Zero Test Coverage

Per QA coverage rules, every source module should have corresponding tests. `core:auth` has 1 source file and 0 test files. This is inherently tied to Critical 1 (module is a stub), but flagged separately as a coverage gap.

## Acceptance Criteria Summary

| Category | Passed | Failed | Not Verifiable |
|----------|--------|--------|----------------|
| Core Wallet (AC 1-7) | 6 | 1 | 0 |
| Balances & Transactions (AC 8-10, 20) | 4 | 0 | 0 |
| dApp Browser (AC 11-16, 27, 29-31) | 10 | 0 | 0 |
| WalletConnect (AC 17-19) | 3 | 0 | 0 |
| Settings (AC 21-22, 32) | 3 | 0 | 0 |
| White-label (AC 23) | 1 | 0 | 0 |
| Security (AC 24-28, 36-37, 42) | 3 | 2 | 3 |
| Crashlytics (AC 24-25, 40) | 2 | 0 | 1 |
| Build/Deploy (AC 33-34, 41) | 1 | 1 | 1 |
| Offline/Device (AC 35, 38-39) | 0 | 0 | 3 |
| **Total** | **31** | **3** | **8** |

## Deferred to Post-deploy

8 criteria require live device/environment verification:
- AC-35: Offline mode UX (needs airplane mode toggle)
- AC-36/37/42: Biometric/password lockout (needs Task 4 implementation first)
- AC-38: QR camera scanner (needs physical camera)
- AC-39: WebView page loading (needs network)
- AC-40: Firebase Crashlytics (needs live Firebase project)
- AC-41: Documentation (APK install + Play Console guides)

## Recommendations

1. **Task 4 must be re-executed.** The biometric auth + auto-lock feature is entirely missing. This blocks the security model described in the tech-spec.
2. **Add slf4j ProGuard rule** to unblock release builds (1-line fix).
3. **After both fixes:** re-run this QA check to confirm release build succeeds and auth tests pass.
