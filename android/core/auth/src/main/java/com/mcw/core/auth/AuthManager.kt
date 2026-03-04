package com.mcw.core.auth

import com.mcw.core.storage.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates wallet authentication: biometric prompt, password fallback,
 * lockout with exponential backoff, and auto-lock timers.
 *
 * Security model:
 * - Biometric preferred, password fallback after 3 biometric failures
 * - Password: verified against bcrypt hash (cost 12) stored in [SecureStorage]
 * - Lockout: 5 failed password attempts triggers exponential backoff (60s, 120s, 300s)
 * - Failure counter and lockout state persisted across app restarts via [SecureStorage]
 * - Auto-lock: 5-minute inactivity timer, 30-second background timer
 *
 * All time-dependent behavior uses [TimeProvider] for deterministic testing.
 *
 * Injectable as Hilt @Singleton.
 */
@Singleton
class AuthManager @Inject constructor(
    private val secureStorage: SecureStorage,
    private val passwordHasher: PasswordHasher,
    private val biometricChecker: BiometricChecker,
    private val timeProvider: TimeProvider,
) {

    companion object {
        /** Minimum password length for validation. */
        const val MIN_PASSWORD_LENGTH = 8

        /** Maximum failed password attempts before lockout. */
        const val MAX_FAILED_ATTEMPTS = 5

        /** Biometric failures before falling back to password. */
        const val MAX_BIOMETRIC_FAILURES = 3

        /** Inactivity timeout before auto-lock (5 minutes). */
        const val INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L // 300_000 ms

        /** Background timeout before auto-lock (30 seconds). */
        const val BACKGROUND_TIMEOUT_MS = 30 * 1000L // 30_000 ms

        /**
         * Exponential backoff durations indexed by lockout level (0-based).
         * Level 0 = first lockout (after 5 failures) = 60s
         * Level 1 = second lockout = 120s
         * Level 2+ = all subsequent = 300s
         */
        val LOCKOUT_DURATIONS_MS = longArrayOf(60_000L, 120_000L, 300_000L)

        // SecureStorage keys for persisted lockout state
        internal const val KEY_FAILURE_COUNT = "auth_failure_count"
        internal const val KEY_LOCKOUT_UNTIL = "auth_lockout_until"
        internal const val KEY_LOCKOUT_LEVEL = "auth_lockout_level"
    }

    private val _lockState = MutableStateFlow<LockState>(LockState.Locked)

    /** Observable lock state for UI. Starts as Locked (require auth on app launch). */
    val lockStateFlow: StateFlow<LockState> = _lockState.asStateFlow()

    /** Current lock state snapshot. */
    val lockState: LockState get() = _lockState.value

    /** Tracks biometric failure count for the current authentication session. */
    private var biometricFailureCount = 0

    /** Timestamp of the last user interaction (for inactivity timer). */
    @Volatile
    private var lastInteractionTime: Long = 0L

    /** Timestamp when the app was backgrounded. */
    @Volatile
    private var backgroundedAt: Long = 0L

    /** Coroutine job for the inactivity timer. */
    private var inactivityTimerJob: Job? = null

    // --- Biometric Availability ---

    /**
     * Checks whether biometric authentication is available on this device.
     * Wraps BiometricManager.canAuthenticate() via [BiometricChecker].
     *
     * @return [BiometricAvailability] indicating hardware and enrollment status
     */
    fun checkBiometricAvailability(): BiometricAvailability {
        return biometricChecker.checkAvailability()
    }

    /**
     * Whether the device supports biometric auth (hardware present and enrolled).
     */
    fun isBiometricAvailable(): Boolean {
        return biometricChecker.checkAvailability() == BiometricAvailability.AVAILABLE
    }

    // --- Biometric Auth ---

    /**
     * Called when a biometric authentication attempt succeeds.
     * Resets biometric failure counter and unlocks the wallet.
     */
    fun onBiometricSuccess() {
        biometricFailureCount = 0
        unlock()
    }

    /**
     * Called when a biometric authentication attempt fails (wrong finger, etc.).
     * After [MAX_BIOMETRIC_FAILURES] consecutive failures, returns true to signal
     * the UI should fall back to password prompt.
     *
     * @return true if biometric failures have reached the threshold and password
     *         prompt should be shown; false otherwise
     */
    fun onBiometricFailure(): Boolean {
        biometricFailureCount++
        return biometricFailureCount >= MAX_BIOMETRIC_FAILURES
    }

    /**
     * Resets the biometric failure counter. Called when starting a new
     * biometric authentication session.
     */
    fun resetBiometricFailureCount() {
        biometricFailureCount = 0
    }

    // --- Password Auth ---

    /**
     * Validates password length.
     *
     * @return true if password meets minimum length requirement (8+ chars)
     */
    fun isPasswordValid(password: String): Boolean {
        return password.length >= MIN_PASSWORD_LENGTH
    }

    /**
     * Authenticates with app password against the stored bcrypt hash.
     *
     * Handles:
     * - Lockout check (if currently locked out, returns [AuthResult.LockedOut])
     * - Password validation (length check)
     * - bcrypt hash comparison
     * - Failure counter increment + lockout trigger on 5th failure
     * - Success resets all failure state
     *
     * @param password plaintext password to verify
     * @return [AuthResult] indicating outcome
     */
    fun authenticateWithPassword(password: String): AuthResult {
        // Check if currently locked out
        val lockoutUntil = getPersistedLong(KEY_LOCKOUT_UNTIL)
        if (lockoutUntil > 0) {
            val now = timeProvider.currentTimeMillis()
            if (now < lockoutUntil) {
                // Still locked out
                _lockState.value = LockState.LockedOut(lockoutUntil)
                return AuthResult.LockedOut(lockoutUntil)
            }
            // Lockout expired -- clear lockout timestamp (but keep level and failure count)
            setPersistedLong(KEY_LOCKOUT_UNTIL, 0L)
            setPersistedInt(KEY_FAILURE_COUNT, 0)
        }

        // Validate password length
        if (password.length < MIN_PASSWORD_LENGTH) {
            return AuthResult.PasswordTooShort
        }

        // Get stored hash
        val storedHash = secureStorage.getPasswordHash()
            ?: return AuthResult.NoPasswordSet

        // Verify against bcrypt hash
        val matches = passwordHasher.checkPassword(password, storedHash)

        if (matches) {
            // Success -- reset all failure state
            setPersistedInt(KEY_FAILURE_COUNT, 0)
            setPersistedLong(KEY_LOCKOUT_UNTIL, 0L)
            // Keep lockout level -- it only resets on successful auth
            setPersistedInt(KEY_LOCKOUT_LEVEL, 0)
            biometricFailureCount = 0
            unlock()
            return AuthResult.Success
        }

        // Wrong password -- increment failure counter
        val failureCount = getPersistedInt(KEY_FAILURE_COUNT) + 1
        setPersistedInt(KEY_FAILURE_COUNT, failureCount)

        if (failureCount >= MAX_FAILED_ATTEMPTS) {
            // Trigger lockout
            val lockoutLevel = getPersistedInt(KEY_LOCKOUT_LEVEL)
            val durationIndex = lockoutLevel.coerceAtMost(LOCKOUT_DURATIONS_MS.size - 1)
            val lockoutDuration = LOCKOUT_DURATIONS_MS[durationIndex]
            val lockoutTimestamp = timeProvider.currentTimeMillis() + lockoutDuration

            setPersistedLong(KEY_LOCKOUT_UNTIL, lockoutTimestamp)
            setPersistedInt(KEY_LOCKOUT_LEVEL, lockoutLevel + 1)
            setPersistedInt(KEY_FAILURE_COUNT, 0) // Reset counter for next round

            _lockState.value = LockState.LockedOut(lockoutTimestamp)
            return AuthResult.LockedOut(lockoutTimestamp)
        }

        val remaining = MAX_FAILED_ATTEMPTS - failureCount
        return AuthResult.WrongPassword(remaining)
    }

    // --- Lock / Unlock ---

    /**
     * Unlocks the wallet and resets the inactivity timer.
     */
    private fun unlock() {
        _lockState.value = LockState.Unlocked
        lastInteractionTime = timeProvider.currentTimeMillis()
    }

    /**
     * Locks the wallet. Called by auto-lock timers or manually.
     */
    fun lock() {
        if (_lockState.value is LockState.Unlocked) {
            _lockState.value = LockState.Locked
        }
    }

    // --- Auto-lock: Inactivity Timer ---

    /**
     * Records a user interaction, resetting the inactivity timer.
     * Should be called on touch events, button clicks, navigation, etc.
     */
    fun onUserInteraction() {
        lastInteractionTime = timeProvider.currentTimeMillis()
    }

    /**
     * Starts the inactivity auto-lock timer in the given [scope].
     * The timer checks every second whether 5 minutes have elapsed
     * since the last user interaction. If so, the wallet locks.
     *
     * Previous timer is cancelled if running.
     */
    fun startInactivityTimer(scope: CoroutineScope) {
        inactivityTimerJob?.cancel()
        lastInteractionTime = timeProvider.currentTimeMillis()
        inactivityTimerJob = scope.launch {
            while (true) {
                delay(1_000L) // Check every second
                val elapsed = timeProvider.currentTimeMillis() - lastInteractionTime
                if (elapsed >= INACTIVITY_TIMEOUT_MS && _lockState.value is LockState.Unlocked) {
                    lock()
                    break
                }
            }
        }
    }

    /**
     * Stops the inactivity timer (e.g., when wallet is locked or app is in background).
     */
    fun stopInactivityTimer() {
        inactivityTimerJob?.cancel()
        inactivityTimerJob = null
    }

    // --- Auto-lock: Background Timer ---

    /**
     * Called when the app moves to background (onPause / onStop).
     * Records the timestamp for background duration checking.
     */
    fun onAppBackgrounded() {
        backgroundedAt = timeProvider.currentTimeMillis()
        stopInactivityTimer()
    }

    /**
     * Called when the app returns to foreground (onResume / onStart).
     * If the app was backgrounded for more than [BACKGROUND_TIMEOUT_MS] (30 seconds),
     * the wallet is locked immediately.
     *
     * @param scope coroutine scope to restart inactivity timer
     */
    fun onAppForegrounded(scope: CoroutineScope) {
        if (backgroundedAt > 0 && _lockState.value is LockState.Unlocked) {
            val elapsed = timeProvider.currentTimeMillis() - backgroundedAt
            if (elapsed > BACKGROUND_TIMEOUT_MS) {
                lock()
            }
        }
        backgroundedAt = 0L

        // Restart inactivity timer if still unlocked
        if (_lockState.value is LockState.Unlocked) {
            startInactivityTimer(scope)
        }
    }

    // --- Lockout State Query ---

    /**
     * Checks the persisted lockout state and updates [lockStateFlow] if needed.
     * Call on app launch to restore lockout state across restarts.
     */
    fun checkPersistedLockoutState() {
        val lockoutUntil = getPersistedLong(KEY_LOCKOUT_UNTIL)
        if (lockoutUntil > 0) {
            val now = timeProvider.currentTimeMillis()
            if (now < lockoutUntil) {
                _lockState.value = LockState.LockedOut(lockoutUntil)
            }
            // If expired, leave as Locked (default) -- user still needs to auth
        }
    }

    /**
     * Returns the number of remaining password attempts before lockout.
     * Returns [MAX_FAILED_ATTEMPTS] if no failures recorded.
     */
    fun getRemainingAttempts(): Int {
        val failures = getPersistedInt(KEY_FAILURE_COUNT)
        return (MAX_FAILED_ATTEMPTS - failures).coerceAtLeast(0)
    }

    // --- Persisted Storage Helpers ---
    // Uses SecureStorage's underlying SharedPreferences for lockout state.
    // These are separate from the wallet keys -- they use the same encrypted prefs
    // but with auth-specific key names.

    private fun getPersistedInt(key: String): Int {
        return secureStorage.getAuthInt(key)
    }

    private fun setPersistedInt(key: String, value: Int) {
        secureStorage.saveAuthInt(key, value)
    }

    private fun getPersistedLong(key: String): Long {
        return secureStorage.getAuthLong(key)
    }

    private fun setPersistedLong(key: String, value: Long) {
        secureStorage.saveAuthLong(key, value)
    }
}
