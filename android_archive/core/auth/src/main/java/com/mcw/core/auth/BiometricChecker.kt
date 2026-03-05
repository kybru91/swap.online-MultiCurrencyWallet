package com.mcw.core.auth

/**
 * Abstraction over [androidx.biometric.BiometricManager.canAuthenticate] for testability.
 *
 * Returns one of:
 * - [BiometricAvailability.AVAILABLE] — biometric hardware present and enrolled
 * - [BiometricAvailability.NO_HARDWARE] — device has no biometric sensor
 * - [BiometricAvailability.NOT_ENROLLED] — hardware present but no biometrics enrolled
 */
interface BiometricChecker {
    fun checkAvailability(): BiometricAvailability
}

/**
 * Biometric hardware/enrollment status.
 */
enum class BiometricAvailability {
    /** Biometric hardware present and at least one biometric enrolled. */
    AVAILABLE,
    /** Device has no biometric sensor. */
    NO_HARDWARE,
    /** Biometric sensor present but user has not enrolled any biometrics. */
    NOT_ENROLLED,
}
