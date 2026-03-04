package com.mcw.core.auth

import android.content.SharedPreferences
import com.mcw.core.storage.SecureStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Unit tests for AuthManager covering all TDD anchors:
 * - testBiometricAvailability -- BiometricManager.canAuthenticate() check
 * - testPasswordValidation -- 8+ chars valid, 7 invalid
 * - testLockoutAfter5Failures -- exponential backoff (60s, 120s, 300s)
 * - testAutoLockInactivity -- 5 minutes inactivity -> locked
 * - testAutoLockBackground -- >30 seconds background -> locked
 *
 * Uses fakes for TimeProvider, PasswordHasher, BiometricChecker.
 * Uses mock SharedPreferences backed by in-memory maps for SecureStorage
 * (same pattern as SecureStorageTest in :core:storage).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthManagerTest {

    // --- Fakes ---

    private class FakeTimeProvider(var currentTime: Long = 1_000_000_000L) : TimeProvider {
        override fun currentTimeMillis(): Long = currentTime
        fun advanceBy(ms: Long) { currentTime += ms }
    }

    private class FakePasswordHasher(private val correctPassword: String) : PasswordHasher {
        override fun checkPassword(plaintext: String, bcryptHash: String): Boolean {
            return plaintext == correctPassword
        }
    }

    private class FakeBiometricChecker(
        var availability: BiometricAvailability = BiometricAvailability.AVAILABLE
    ) : BiometricChecker {
        override fun checkAvailability(): BiometricAvailability = availability
    }

    // --- In-memory SharedPreferences backing ---
    // Mirrors the approach in SecureStorageTest: mutable maps + mock editor/prefs.

    private val stringStore = mutableMapOf<String, String?>()
    private val intStore = mutableMapOf<String, Int>()
    private val longStore = mutableMapOf<String, Long>()

    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockPrefs: SharedPreferences

    private fun clearStores() {
        stringStore.clear()
        intStore.clear()
        longStore.clear()
    }

    private fun snapshotStores(): Triple<Map<String, String?>, Map<String, Int>, Map<String, Long>> {
        return Triple(stringStore.toMap(), intStore.toMap(), longStore.toMap())
    }

    private fun restoreStores(snap: Triple<Map<String, String?>, Map<String, Int>, Map<String, Long>>) {
        stringStore.clear(); stringStore.putAll(snap.first)
        intStore.clear(); intStore.putAll(snap.second)
        longStore.clear(); longStore.putAll(snap.third)
    }

    private fun buildMockEditor(): SharedPreferences.Editor {
        return mock {
            on { putString(any(), anyOrNull()) } doAnswer { invocation ->
                stringStore[invocation.getArgument(0)] = invocation.getArgument(1)
                mockEditor
            }
            on { putInt(any(), any()) } doAnswer { invocation ->
                intStore[invocation.getArgument(0)] = invocation.getArgument(1)
                mockEditor
            }
            on { putLong(any(), any()) } doAnswer { invocation ->
                longStore[invocation.getArgument(0)] = invocation.getArgument(1)
                mockEditor
            }
            on { remove(any()) } doAnswer { invocation ->
                val key = invocation.getArgument<String>(0)
                stringStore.remove(key)
                intStore.remove(key)
                longStore.remove(key)
                mockEditor
            }
            on { clear() } doAnswer {
                clearStores()
                mockEditor
            }
            on { apply() } doAnswer { /* no-op */ }
            on { commit() } doReturn true
        }
    }

    private fun buildMockPrefs(): SharedPreferences {
        return mock {
            on { edit() } doReturn mockEditor
            on { getString(any(), anyOrNull()) } doAnswer { invocation ->
                val key = invocation.getArgument<String>(0)
                val default = invocation.getArgument<String?>(1)
                if (stringStore.containsKey(key)) stringStore[key] else default
            }
            on { getInt(any(), any()) } doAnswer { invocation ->
                val key = invocation.getArgument<String>(0)
                val default = invocation.getArgument<Int>(1)
                intStore.getOrDefault(key, default)
            }
            on { getLong(any(), any()) } doAnswer { invocation ->
                val key = invocation.getArgument<String>(0)
                val default = invocation.getArgument<Long>(1)
                longStore.getOrDefault(key, default)
            }
            on { contains(any()) } doAnswer { invocation ->
                val key = invocation.getArgument<String>(0)
                key in stringStore || key in intStore || key in longStore
            }
        }
    }

    // --- Test fixtures ---

    private lateinit var fakeTime: FakeTimeProvider
    private lateinit var fakeHasher: FakePasswordHasher
    private lateinit var fakeBiometric: FakeBiometricChecker
    private lateinit var secureStorage: SecureStorage
    private lateinit var authManager: AuthManager

    private val testScope = TestScope()

    companion object {
        private const val CORRECT_PASSWORD = "test1234"
        private const val FAKE_HASH = "\$2a\$12\$fakeBcryptHashForTesting"
    }

    @Before
    fun setUp() {
        clearStores()
        fakeTime = FakeTimeProvider()
        fakeHasher = FakePasswordHasher(CORRECT_PASSWORD)
        fakeBiometric = FakeBiometricChecker()

        mockEditor = buildMockEditor()
        mockPrefs = buildMockPrefs()

        secureStorage = SecureStorage.createForTesting(mockPrefs)
        secureStorage.savePasswordHash(FAKE_HASH)

        authManager = AuthManager(
            secureStorage = secureStorage,
            passwordHasher = fakeHasher,
            biometricChecker = fakeBiometric,
            timeProvider = fakeTime,
        )
    }

    /** Creates a fresh AuthManager using the same backing stores (simulates app restart). */
    private fun restartAuthManager(): AuthManager {
        // Rebuild mocks pointing to the same backing stores
        mockEditor = buildMockEditor()
        mockPrefs = buildMockPrefs()
        val newStorage = SecureStorage.createForTesting(mockPrefs)
        return AuthManager(
            secureStorage = newStorage,
            passwordHasher = fakeHasher,
            biometricChecker = fakeBiometric,
            timeProvider = fakeTime,
        )
    }

    // =====================================================================
    // TDD Anchor: testBiometricAvailability
    // =====================================================================

    @Test
    fun testBiometricAvailability_available() {
        fakeBiometric.availability = BiometricAvailability.AVAILABLE
        assertEquals(BiometricAvailability.AVAILABLE, authManager.checkBiometricAvailability())
        assertTrue(authManager.isBiometricAvailable())
    }

    @Test
    fun testBiometricAvailability_noHardware() {
        fakeBiometric.availability = BiometricAvailability.NO_HARDWARE
        assertEquals(BiometricAvailability.NO_HARDWARE, authManager.checkBiometricAvailability())
        assertFalse(authManager.isBiometricAvailable())
    }

    @Test
    fun testBiometricAvailability_notEnrolled() {
        fakeBiometric.availability = BiometricAvailability.NOT_ENROLLED
        assertEquals(BiometricAvailability.NOT_ENROLLED, authManager.checkBiometricAvailability())
        assertFalse(authManager.isBiometricAvailable())
    }

    // =====================================================================
    // TDD Anchor: testPasswordValidation
    // =====================================================================

    @Test
    fun testPasswordValidation_8CharsValid() {
        assertTrue(authManager.isPasswordValid("12345678"))
    }

    @Test
    fun testPasswordValidation_7CharsInvalid() {
        assertFalse(authManager.isPasswordValid("1234567"))
    }

    @Test
    fun testPasswordValidation_emptyInvalid() {
        assertFalse(authManager.isPasswordValid(""))
    }

    @Test
    fun testPasswordValidation_longPasswordValid() {
        assertTrue(authManager.isPasswordValid("a".repeat(100)))
    }

    // =====================================================================
    // Password Authentication
    // =====================================================================

    @Test
    fun testPasswordVerification_correctPassword() {
        val result = authManager.authenticateWithPassword(CORRECT_PASSWORD)
        assertEquals(AuthResult.Success, result)
        assertEquals(LockState.Unlocked, authManager.lockState)
    }

    @Test
    fun testPasswordVerification_wrongPassword() {
        val result = authManager.authenticateWithPassword("wrongpwd")
        assertTrue(result is AuthResult.WrongPassword)
        assertEquals(4, (result as AuthResult.WrongPassword).remainingAttempts)
    }

    @Test
    fun testPasswordVerification_tooShort() {
        val result = authManager.authenticateWithPassword("short")
        assertEquals(AuthResult.PasswordTooShort, result)
    }

    @Test
    fun testPasswordVerification_noPasswordSet() {
        // Clear password hash
        clearStores()
        mockEditor = buildMockEditor()
        mockPrefs = buildMockPrefs()
        val emptyStorage = SecureStorage.createForTesting(mockPrefs)
        val manager = AuthManager(emptyStorage, fakeHasher, fakeBiometric, fakeTime)

        val result = manager.authenticateWithPassword(CORRECT_PASSWORD)
        assertEquals(AuthResult.NoPasswordSet, result)
    }

    @Test
    fun testPasswordVerification_successResetsFailureCounter() {
        // Fail 3 times
        repeat(3) { authManager.authenticateWithPassword("wrongpwd") }
        assertEquals(2, authManager.getRemainingAttempts())

        // Succeed
        val result = authManager.authenticateWithPassword(CORRECT_PASSWORD)
        assertEquals(AuthResult.Success, result)
        assertEquals(5, authManager.getRemainingAttempts())
    }

    // =====================================================================
    // TDD Anchor: testLockoutAfter5Failures
    // =====================================================================

    @Test
    fun testLockoutAfter5Failures_triggersLockedOutState() {
        for (i in 1..4) {
            val result = authManager.authenticateWithPassword("wrongpwd")
            assertTrue("Attempt $i should be WrongPassword", result is AuthResult.WrongPassword)
            assertEquals(5 - i, (result as AuthResult.WrongPassword).remainingAttempts)
        }

        val result = authManager.authenticateWithPassword("wrongpwd")
        assertTrue("5th failure should trigger lockout", result is AuthResult.LockedOut)
        assertTrue(authManager.lockState is LockState.LockedOut)
    }

    @Test
    fun testLockoutAfter5Failures_60sDuration() {
        repeat(5) { authManager.authenticateWithPassword("wrongpwd") }

        val lockout = authManager.lockState as LockState.LockedOut
        val duration = lockout.untilTimestamp - fakeTime.currentTimeMillis()
        assertEquals("First lockout should be 60s", 60_000L, duration)
    }

    @Test
    fun testLockoutDuringLockout_rejectedWithLockedOut() {
        repeat(5) { authManager.authenticateWithPassword("wrongpwd") }

        val result = authManager.authenticateWithPassword(CORRECT_PASSWORD)
        assertTrue(result is AuthResult.LockedOut)
    }

    @Test
    fun testLockoutExpiry_allowsNewAttempts() {
        repeat(5) { authManager.authenticateWithPassword("wrongpwd") }

        fakeTime.advanceBy(61_000L)

        val result = authManager.authenticateWithPassword(CORRECT_PASSWORD)
        assertEquals(AuthResult.Success, result)
    }

    // =====================================================================
    // Lockout Progression: 60s -> 120s -> 300s
    // =====================================================================

    @Test
    fun testLockoutProgression_60s_120s_300s() {
        // First lockout: 60s
        repeat(5) { authManager.authenticateWithPassword("wrongpwd") }
        var lockout = authManager.lockState as LockState.LockedOut
        assertEquals(60_000L, lockout.untilTimestamp - fakeTime.currentTimeMillis())

        // Wait and trigger second lockout: 120s
        fakeTime.advanceBy(61_000L)
        repeat(5) { authManager.authenticateWithPassword("wrongpwd") }
        lockout = authManager.lockState as LockState.LockedOut
        assertEquals(120_000L, lockout.untilTimestamp - fakeTime.currentTimeMillis())

        // Wait and trigger third lockout: 300s
        fakeTime.advanceBy(121_000L)
        repeat(5) { authManager.authenticateWithPassword("wrongpwd") }
        lockout = authManager.lockState as LockState.LockedOut
        assertEquals(300_000L, lockout.untilTimestamp - fakeTime.currentTimeMillis())
    }

    @Test
    fun testLockoutProgression_capsAt300s() {
        for (cycle in 0 until 4) {
            repeat(5) { authManager.authenticateWithPassword("wrongpwd") }
            fakeTime.advanceBy(301_000L)
        }

        repeat(5) { authManager.authenticateWithPassword("wrongpwd") }
        val lockout = authManager.lockState as LockState.LockedOut
        assertEquals(300_000L, lockout.untilTimestamp - fakeTime.currentTimeMillis())
    }

    @Test
    fun testSuccessfulAuth_resetsLockoutLevel() {
        // First lockout (60s)
        repeat(5) { authManager.authenticateWithPassword("wrongpwd") }
        fakeTime.advanceBy(61_000L)

        // Success resets level
        authManager.authenticateWithPassword(CORRECT_PASSWORD)

        // Next lockout should be 60s again (not 120s)
        repeat(5) { authManager.authenticateWithPassword("wrongpwd") }
        val lockout = authManager.lockState as LockState.LockedOut
        assertEquals(60_000L, lockout.untilTimestamp - fakeTime.currentTimeMillis())
    }

    // =====================================================================
    // Lockout Persistence (across app restarts)
    // =====================================================================

    @Test
    fun testLockoutPersistence_failureCountSurvivesRestart() {
        // Fail 3 times
        repeat(3) { authManager.authenticateWithPassword("wrongpwd") }

        // Simulate restart (same backing stores survive)
        val newManager = restartAuthManager()
        // Need to re-save password hash since stores are shared but SecureStorage is new
        newManager.authenticateWithPassword("wrongpwd") // failure #4
        val result = newManager.authenticateWithPassword("wrongpwd") // failure #5
        assertTrue("5th cumulative failure should trigger lockout", result is AuthResult.LockedOut)
    }

    @Test
    fun testLockoutPersistence_lockoutTimestampSurvivesRestart() {
        repeat(5) { authManager.authenticateWithPassword("wrongpwd") }
        val originalUntil = (authManager.lockState as LockState.LockedOut).untilTimestamp

        val newManager = restartAuthManager()
        newManager.checkPersistedLockoutState()

        assertTrue(newManager.lockState is LockState.LockedOut)
        assertEquals(originalUntil, (newManager.lockState as LockState.LockedOut).untilTimestamp)
    }

    @Test
    fun testLockoutPersistence_expiredLockoutAfterRestart() {
        repeat(5) { authManager.authenticateWithPassword("wrongpwd") }
        fakeTime.advanceBy(61_000L)

        val newManager = restartAuthManager()
        newManager.checkPersistedLockoutState()

        assertFalse(newManager.lockState is LockState.LockedOut)
    }

    // =====================================================================
    // TDD Anchor: testBiometricFallbackAfter3Failures
    // =====================================================================

    @Test
    fun testBiometricFallbackAfter3Failures() {
        assertFalse("1st failure: no fallback", authManager.onBiometricFailure())
        assertFalse("2nd failure: no fallback", authManager.onBiometricFailure())
        assertTrue("3rd failure: fallback to password", authManager.onBiometricFailure())
    }

    @Test
    fun testBiometricSuccess_resetsFailureCount() {
        authManager.onBiometricFailure()
        authManager.onBiometricFailure()
        authManager.onBiometricSuccess()

        assertFalse(authManager.onBiometricFailure())
        assertFalse(authManager.onBiometricFailure())
        assertTrue(authManager.onBiometricFailure())
    }

    @Test
    fun testBiometricSuccess_unlocksWallet() {
        assertEquals(LockState.Locked, authManager.lockState)
        authManager.onBiometricSuccess()
        assertEquals(LockState.Unlocked, authManager.lockState)
    }

    @Test
    fun testBiometricFailureCount_manualReset() {
        authManager.onBiometricFailure()
        authManager.onBiometricFailure()
        authManager.resetBiometricFailureCount()

        assertFalse(authManager.onBiometricFailure())
        assertFalse(authManager.onBiometricFailure())
        assertTrue(authManager.onBiometricFailure())
    }

    // =====================================================================
    // TDD Anchor: testAutoLockInactivity (5 minutes)
    // =====================================================================

    @Test
    fun testAutoLock5Minutes_locksAfterInactivity() = testScope.runTest {
        authManager.onBiometricSuccess()
        authManager.startInactivityTimer(this)

        fakeTime.advanceBy(4 * 60_000L)
        advanceTimeBy(4 * 60_000L)
        assertEquals(LockState.Unlocked, authManager.lockState)

        fakeTime.advanceBy(62_000L)
        advanceTimeBy(62_000L)
        assertEquals(LockState.Locked, authManager.lockState)
    }

    @Test
    fun testAutoLock5Minutes_doesNotLockBeforeTimeout() = testScope.runTest {
        authManager.onBiometricSuccess()
        authManager.startInactivityTimer(this)

        fakeTime.advanceBy(299_000L)
        advanceTimeBy(299_000L)
        assertEquals(LockState.Unlocked, authManager.lockState)

        // Clean up the running coroutine to avoid UncompletedCoroutinesError
        authManager.stopInactivityTimer()
    }

    // =====================================================================
    // TDD Anchor: testUserInteractionResetsInactivityTimer
    // =====================================================================

    @Test
    fun testUserInteractionResetsInactivityTimer() = testScope.runTest {
        authManager.onBiometricSuccess()
        authManager.startInactivityTimer(this)

        fakeTime.advanceBy(4 * 60_000L)
        advanceTimeBy(4 * 60_000L)
        assertEquals(LockState.Unlocked, authManager.lockState)

        authManager.onUserInteraction()

        fakeTime.advanceBy(4 * 60_000L)
        advanceTimeBy(4 * 60_000L)
        assertEquals("Still unlocked -- 4m since interaction", LockState.Unlocked, authManager.lockState)

        fakeTime.advanceBy(2 * 60_000L)
        advanceTimeBy(2 * 60_000L)
        assertEquals(LockState.Locked, authManager.lockState)
    }

    // =====================================================================
    // TDD Anchor: testAutoLockBackground (>30 seconds)
    // =====================================================================

    @Test
    fun testAutoLock30SecondsBackground_locks() {
        authManager.onBiometricSuccess()
        authManager.onAppBackgrounded()
        fakeTime.advanceBy(31_000L)
        authManager.onAppForegrounded(testScope)

        assertEquals(LockState.Locked, authManager.lockState)
    }

    @Test
    fun testAutoLockBackground_under30s_staysUnlocked() {
        authManager.onBiometricSuccess()
        authManager.onAppBackgrounded()
        fakeTime.advanceBy(29_000L)
        authManager.onAppForegrounded(testScope)

        assertEquals(LockState.Unlocked, authManager.lockState)
    }

    @Test
    fun testAutoLockBackground_exactly30s_staysUnlocked() {
        authManager.onBiometricSuccess()
        authManager.onAppBackgrounded()
        fakeTime.advanceBy(30_000L)
        authManager.onAppForegrounded(testScope)

        assertEquals(LockState.Unlocked, authManager.lockState)
    }

    @Test
    fun testAutoLockBackground_alreadyLocked_noStateChange() {
        assertEquals(LockState.Locked, authManager.lockState)
        authManager.onAppBackgrounded()
        fakeTime.advanceBy(60_000L)
        authManager.onAppForegrounded(testScope)

        assertEquals(LockState.Locked, authManager.lockState)
    }

    // =====================================================================
    // Lock / Unlock
    // =====================================================================

    @Test
    fun testInitialStateIsLocked() {
        assertEquals(LockState.Locked, authManager.lockState)
    }

    @Test
    fun testManualLock() {
        authManager.onBiometricSuccess()
        assertEquals(LockState.Unlocked, authManager.lockState)
        authManager.lock()
        assertEquals(LockState.Locked, authManager.lockState)
    }

    @Test
    fun testLockOnlyFromUnlocked() {
        assertEquals(LockState.Locked, authManager.lockState)
        authManager.lock()
        assertEquals(LockState.Locked, authManager.lockState)
    }

    @Test
    fun testStopInactivityTimer_preventsLock() = testScope.runTest {
        authManager.onBiometricSuccess()
        authManager.startInactivityTimer(this)

        fakeTime.advanceBy(3 * 60_000L)
        advanceTimeBy(3 * 60_000L)

        authManager.stopInactivityTimer()

        fakeTime.advanceBy(10 * 60_000L)
        advanceTimeBy(10 * 60_000L)
        assertEquals(LockState.Unlocked, authManager.lockState)
    }

    @Test
    fun testRemainingAttempts_decrements() {
        assertEquals(5, authManager.getRemainingAttempts())
        authManager.authenticateWithPassword("wrongpwd")
        assertEquals(4, authManager.getRemainingAttempts())
        authManager.authenticateWithPassword("wrongpwd")
        assertEquals(3, authManager.getRemainingAttempts())
    }
}
