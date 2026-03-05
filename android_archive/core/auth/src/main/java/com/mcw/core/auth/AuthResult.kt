package com.mcw.core.auth

/**
 * Result of an authentication attempt.
 */
sealed class AuthResult {
    /** Authentication succeeded. */
    object Success : AuthResult()

    /** Wrong password. [remainingAttempts] before lockout (0 means lockout just triggered). */
    data class WrongPassword(val remainingAttempts: Int) : AuthResult()

    /** Account is locked out until [untilTimestamp] (epoch ms). */
    data class LockedOut(val untilTimestamp: Long) : AuthResult()

    /** Password is too short (must be >= 8 characters). */
    object PasswordTooShort : AuthResult()

    /** No password hash stored — user needs to set up a password first. */
    object NoPasswordSet : AuthResult()
}
