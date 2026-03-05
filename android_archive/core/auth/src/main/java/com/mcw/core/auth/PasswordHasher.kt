package com.mcw.core.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over password hashing for testability.
 * Production implementation uses jBCrypt with cost factor 12.
 */
interface PasswordHasher {
    /** Checks if [plaintext] matches the given [bcryptHash]. */
    fun checkPassword(plaintext: String, bcryptHash: String): Boolean
}

/**
 * Production [PasswordHasher] using jBCrypt.
 */
@Singleton
class BCryptPasswordHasher @Inject constructor() : PasswordHasher {
    override fun checkPassword(plaintext: String, bcryptHash: String): Boolean {
        return org.mindrot.jbcrypt.BCrypt.checkpw(plaintext, bcryptHash)
    }
}
