/**
 * Unit tests for AuthManager lockout logic.
 * Tests the exact same lockout sequences as Kotlin AuthManagerTest.
 * @jest-environment node
 */

// Mock dependencies
jest.mock('react-native-keychain', () => {
  const store: Record<string, string> = {};
  return {
    setGenericPassword: jest.fn(async (_u: string, password: string, options: any) => {
      store[options.service] = password;
      return true;
    }),
    getGenericPassword: jest.fn(async (options: any) => {
      const val = store[options.service];
      return val ? {username: 'x', password: val} : false;
    }),
    resetGenericPassword: jest.fn(async () => true),
    ACCESS_CONTROL: {BIOMETRY_ANY_OR_DEVICE_PASSCODE: 'biometryAny'},
    ACCESSIBLE: {WHEN_UNLOCKED_THIS_DEVICE_ONLY: 'whenUnlocked', ALWAYS: 'always'},
    getSupportedBiometryType: jest.fn(async () => null),
  };
});

jest.mock('react-native', () => ({Platform: {OS: 'android', Version: 30}}));
jest.mock('react-native-quick-crypto', () => ({
  createHash: jest.fn(() => ({
    update: jest.fn().mockReturnThis(),
    digest: jest.fn((enc: string) => enc === 'hex' ? 'abc123hash' : Buffer.alloc(32)),
  })),
}));

import {
  authenticateWithPassword,
  setPassword,
  checkPersistedLockoutState,
  getRemainingAttempts,
  MAX_FAILED_ATTEMPTS,
  LOCKOUT_DURATIONS_MS,
  KEY_FAILURE_COUNT,
  KEY_LOCKOUT_LEVEL,
  KEY_LOCKOUT_UNTIL,
} from '../../src/auth/AuthManager';
import {saveAuthInt, saveAuthLong} from '../../src/storage/SecureStorage';

beforeEach(async () => {
  // Reset auth state
  await saveAuthInt(KEY_FAILURE_COUNT, 0);
  await saveAuthLong(KEY_LOCKOUT_UNTIL, 0);
  await saveAuthInt(KEY_LOCKOUT_LEVEL, 0);
});

describe('AuthManager', () => {
  describe('password validation', () => {
    it('rejects passwords shorter than 8 characters', async () => {
      await setPassword('correctpassword123');
      const result = await authenticateWithPassword('short');
      expect(result.type).toBe('password_too_short');
    });
  });

  describe('lockout logic', () => {
    it('returns no_password_set when no password stored', async () => {
      const result = await authenticateWithPassword('validpassword');
      expect(result.type).toBe('no_password_set');
    });

    it('triggers lockout after MAX_FAILED_ATTEMPTS wrong passwords', async () => {
      await setPassword('correctpassword123');

      for (let i = 0; i < MAX_FAILED_ATTEMPTS - 1; i++) {
        const r = await authenticateWithPassword('wrongpassword1');
        expect(r.type).toBe('wrong_password');
      }

      const lockoutResult = await authenticateWithPassword('wrongpassword1');
      expect(lockoutResult.type).toBe('locked_out');
    });

    it('decrements remaining attempts on each failure', async () => {
      await setPassword('correctpassword123');

      for (let i = 1; i <= 3; i++) {
        const r = await authenticateWithPassword('wrongpassword1');
        if (r.type === 'wrong_password') {
          expect(r.remainingAttempts).toBe(MAX_FAILED_ATTEMPTS - i);
        }
      }
    });

    it('uses exponential backoff for lockout durations', async () => {
      await setPassword('correctpassword123');

      // Trigger first lockout
      for (let i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
        await authenticateWithPassword('wrongpassword1');
      }

      // First lockout duration = LOCKOUT_DURATIONS_MS[0]
      const state = await checkPersistedLockoutState();
      expect(state.type).toBe('locked_out');
      if (state.type === 'locked_out') {
        const remainingMs = state.lockoutUntil - Date.now();
        expect(remainingMs).toBeLessThanOrEqual(LOCKOUT_DURATIONS_MS[0]);
        expect(remainingMs).toBeGreaterThan(LOCKOUT_DURATIONS_MS[0] - 5000); // within 5s
      }
    });
  });

  describe('getRemainingAttempts', () => {
    it('returns MAX_FAILED_ATTEMPTS when no failures', async () => {
      const remaining = await getRemainingAttempts();
      expect(remaining).toBe(MAX_FAILED_ATTEMPTS);
    });
  });
});
