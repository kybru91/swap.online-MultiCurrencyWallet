import {create} from 'zustand';
import {
  type LockState,
  checkPersistedLockoutState,
  authenticateWithPassword,
  authenticateWithBiometric,
  setPassword,
  checkBiometricAvailability,
  type BiometricAvailability,
  INACTIVITY_TIMEOUT_MS,
  BACKGROUND_TIMEOUT_MS,
} from '@auth/index';

export interface AuthStoreState {
  lockState: LockState;
  biometricAvailability: BiometricAvailability | null;

  // Timers
  lastInteractionTime: number;
  backgroundedAt: number;
  inactivityTimerRef: ReturnType<typeof setInterval> | null;

  // Actions
  initAuth: () => Promise<void>;
  unlockWithBiometric: () => Promise<boolean>;
  unlockWithPassword: (password: string) => Promise<{success: boolean; error?: string}>;
  lock: () => void;
  onUserInteraction: () => void;
  onAppBackgrounded: () => void;
  onAppForegrounded: () => void;
  setupPassword: (password: string) => Promise<void>;
}

/**
 * Zustand store for authentication state.
 * Handles lock/unlock, biometric auth, password auth, auto-lock timers.
 *
 * Mirrors Kotlin AuthManager behavior exactly.
 */
export const useAuthStore = create<AuthStoreState>((set, get) => ({
  lockState: {type: 'locked'},
  biometricAvailability: null,
  lastInteractionTime: 0,
  backgroundedAt: 0,
  inactivityTimerRef: null,

  /**
   * Initializes auth state on app start.
   * Checks biometric availability and persisted lockout state.
   */
  initAuth: async () => {
    const [lockState, biometricAvailability] = await Promise.all([
      checkPersistedLockoutState(),
      checkBiometricAvailability(),
    ]);
    set({lockState, biometricAvailability});
  },

  /**
   * Attempts biometric authentication.
   * @returns true if authentication succeeded
   */
  unlockWithBiometric: async () => {
    const success = await authenticateWithBiometric('Unlock MCW Wallet');
    if (success) {
      set({lockState: {type: 'unlocked'}});
      get().onUserInteraction();
      get()._startInactivityTimer();
    }
    return success;
  },

  /**
   * Attempts password authentication.
   */
  unlockWithPassword: async (password: string) => {
    const result = await authenticateWithPassword(password);
    if (result.type === 'success') {
      set({lockState: {type: 'unlocked'}});
      get().onUserInteraction();
      get()._startInactivityTimer();
      return {success: true};
    }
    if (result.type === 'locked_out') {
      set({lockState: {type: 'locked_out', lockoutUntil: result.lockoutUntil}});
      return {success: false, error: `Too many attempts. Try again later.`};
    }
    if (result.type === 'wrong_password') {
      return {success: false, error: `Wrong password. ${result.remainingAttempts} attempts remaining.`};
    }
    return {success: false, error: 'Authentication failed'};
  },

  lock: () => {
    const {inactivityTimerRef} = get();
    if (inactivityTimerRef) {
      clearInterval(inactivityTimerRef);
    }
    set({lockState: {type: 'locked'}, inactivityTimerRef: null});
  },

  onUserInteraction: () => {
    set({lastInteractionTime: Date.now()});
  },

  onAppBackgrounded: () => {
    const {inactivityTimerRef} = get();
    if (inactivityTimerRef) {
      clearInterval(inactivityTimerRef);
    }
    set({backgroundedAt: Date.now(), inactivityTimerRef: null});
  },

  onAppForegrounded: () => {
    const {backgroundedAt, lockState} = get();
    if (backgroundedAt > 0 && lockState.type === 'unlocked') {
      const elapsed = Date.now() - backgroundedAt;
      if (elapsed > BACKGROUND_TIMEOUT_MS) {
        get().lock();
        set({backgroundedAt: 0});
        return;
      }
    }
    set({backgroundedAt: 0});
    if (get().lockState.type === 'unlocked') {
      get()._startInactivityTimer();
    }
  },

  setupPassword: async (password: string) => {
    await setPassword(password);
  },

  // Internal: starts inactivity timer (checks every second)
  _startInactivityTimer: () => {
    const existing = get().inactivityTimerRef;
    if (existing) clearInterval(existing);

    const ref = setInterval(() => {
      const {lastInteractionTime, lockState} = get();
      if (lockState.type !== 'unlocked') {
        clearInterval(ref);
        return;
      }
      const elapsed = Date.now() - lastInteractionTime;
      if (elapsed >= INACTIVITY_TIMEOUT_MS) {
        get().lock();
      }
    }, 1000);

    set({inactivityTimerRef: ref, lastInteractionTime: Date.now()});
  },
} as AuthStoreState & {_startInactivityTimer: () => void}));
