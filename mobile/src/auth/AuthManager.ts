import {
  getPasswordHash,
  savePasswordHash,
  getAuthInt,
  saveAuthInt,
  getAuthLong,
  saveAuthLong,
} from '@storage/SecureStorage';
import {authenticateWithBiometric, checkBiometricAvailability} from './BiometricAuth';

// --- Constants (matching Kotlin AuthManager) ---

export const MIN_PASSWORD_LENGTH = 8;
export const MAX_FAILED_ATTEMPTS = 5;
export const MAX_BIOMETRIC_FAILURES = 3;
export const INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
export const BACKGROUND_TIMEOUT_MS = 30 * 1000;      // 30 seconds
export const LOCKOUT_DURATIONS_MS = [60_000, 120_000, 300_000]; // 60s, 120s, 300s

// Auth state storage keys (matching Kotlin AuthManager)
export const KEY_FAILURE_COUNT = 'auth_failure_count';
export const KEY_LOCKOUT_UNTIL = 'auth_lockout_until';
export const KEY_LOCKOUT_LEVEL = 'auth_lockout_level';

// --- Types ---

export type LockState =
  | {type: 'locked'}
  | {type: 'unlocked'}
  | {type: 'locked_out'; lockoutUntil: number};

export type AuthResult =
  | {type: 'success'}
  | {type: 'wrong_password'; remainingAttempts: number}
  | {type: 'locked_out'; lockoutUntil: number}
  | {type: 'no_password_set'}
  | {type: 'password_too_short'}
  | {type: 'biometric_success'}
  | {type: 'biometric_failed'}
  | {type: 'biometric_cancelled'};

// --- Password validation ---

export function isPasswordValid(password: string): boolean {
  return password.length >= MIN_PASSWORD_LENGTH;
}

/**
 * Hashes a password for storage. Uses SHA-256 as simple hash
 * (production: use bcrypt via native module).
 *
 * Note: For production, replace with react-native-bcrypt or
 * expo-crypto with proper cost factor.
 */
export function hashPassword(password: string): string {
  // Simple deterministic hash for now — replace with bcrypt in production
  // Using a prefix to identify the hash scheme
  const crypto = require('react-native-quick-crypto');
  const hash = crypto.createHash('sha256').update(password).digest('hex');
  return `sha256:${hash}`;
}

export function checkPassword(password: string, storedHash: string): boolean {
  const computed = hashPassword(password);
  return computed === storedHash;
}

// --- Password Auth ---

/**
 * Authenticates with password against stored hash.
 * Handles lockout check, failure counting, exponential backoff.
 *
 * Matches Kotlin AuthManager.authenticateWithPassword() behavior.
 */
export async function authenticateWithPassword(password: string): Promise<AuthResult> {
  const now = Date.now();

  // Check lockout
  const lockoutUntil = await getAuthLong(KEY_LOCKOUT_UNTIL);
  if (lockoutUntil > 0 && now < lockoutUntil) {
    return {type: 'locked_out', lockoutUntil};
  }
  if (lockoutUntil > 0 && now >= lockoutUntil) {
    // Lockout expired — clear timestamp and failure count
    await saveAuthLong(KEY_LOCKOUT_UNTIL, 0);
    await saveAuthInt(KEY_FAILURE_COUNT, 0);
  }

  // Validate length
  if (!isPasswordValid(password)) {
    return {type: 'password_too_short'};
  }

  // Get stored hash
  const storedHash = await getPasswordHash();
  if (!storedHash) {
    return {type: 'no_password_set'};
  }

  // Verify password
  if (checkPassword(password, storedHash)) {
    // Success — reset all failure state
    await Promise.all([
      saveAuthInt(KEY_FAILURE_COUNT, 0),
      saveAuthLong(KEY_LOCKOUT_UNTIL, 0),
      saveAuthInt(KEY_LOCKOUT_LEVEL, 0),
    ]);
    return {type: 'success'};
  }

  // Wrong password — increment counter
  const failureCount = (await getAuthInt(KEY_FAILURE_COUNT)) + 1;
  await saveAuthInt(KEY_FAILURE_COUNT, failureCount);

  if (failureCount >= MAX_FAILED_ATTEMPTS) {
    // Trigger lockout
    const lockoutLevel = await getAuthInt(KEY_LOCKOUT_LEVEL);
    const durationIndex = Math.min(lockoutLevel, LOCKOUT_DURATIONS_MS.length - 1);
    const lockoutDuration = LOCKOUT_DURATIONS_MS[durationIndex];
    const newLockoutUntil = Date.now() + lockoutDuration;

    await Promise.all([
      saveAuthLong(KEY_LOCKOUT_UNTIL, newLockoutUntil),
      saveAuthInt(KEY_LOCKOUT_LEVEL, lockoutLevel + 1),
      saveAuthInt(KEY_FAILURE_COUNT, 0),
    ]);

    return {type: 'locked_out', lockoutUntil: newLockoutUntil};
  }

  const remaining = MAX_FAILED_ATTEMPTS - failureCount;
  return {type: 'wrong_password', remainingAttempts: remaining};
}

/**
 * Sets wallet password — hashes and stores.
 */
export async function setPassword(password: string): Promise<void> {
  if (!isPasswordValid(password)) {
    throw new Error(`Password must be at least ${MIN_PASSWORD_LENGTH} characters`);
  }
  const hash = hashPassword(password);
  await savePasswordHash(hash);
}

// --- Lockout State Query ---

/**
 * Checks persisted lockout state. Call on app launch.
 * @returns current LockState based on persisted data
 */
export async function checkPersistedLockoutState(): Promise<LockState> {
  const lockoutUntil = await getAuthLong(KEY_LOCKOUT_UNTIL);
  if (lockoutUntil > 0 && Date.now() < lockoutUntil) {
    return {type: 'locked_out', lockoutUntil};
  }
  return {type: 'locked'};
}

/**
 * Returns remaining password attempts before lockout.
 */
export async function getRemainingAttempts(): Promise<number> {
  const failures = await getAuthInt(KEY_FAILURE_COUNT);
  return Math.max(MAX_FAILED_ATTEMPTS - failures, 0);
}

// Re-export biometric functions
export {authenticateWithBiometric, checkBiometricAvailability};
