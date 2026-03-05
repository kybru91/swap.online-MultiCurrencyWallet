package com.mcw.core.auth

/**
 * Represents the authentication lock state of the wallet.
 *
 * - [Unlocked]: User has authenticated, wallet operations are permitted.
 * - [Locked]: Wallet is locked due to inactivity or backgrounding; re-authentication required.
 * - [LockedOut]: Too many failed password attempts; locked until [untilTimestamp] (epoch ms).
 */
sealed class LockState {
    object Unlocked : LockState()
    object Locked : LockState()
    data class LockedOut(val untilTimestamp: Long) : LockState()
}
