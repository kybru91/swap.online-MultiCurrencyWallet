/**
 * Unit tests for SecureStorage module.
 * Mocks react-native-keychain to test storage logic without native layer.
 * @jest-environment node
 */

// Mock react-native-keychain
const mockStore: Record<string, string> = {};

jest.mock('react-native-keychain', () => ({
  setGenericPassword: jest.fn(async (username: string, password: string, options: any) => {
    mockStore[options.service] = password;
    return true;
  }),
  getGenericPassword: jest.fn(async (options: any) => {
    const val = mockStore[options.service];
    if (!val) return false;
    return {username: 'user', password: val, service: options.service};
  }),
  resetGenericPassword: jest.fn(async (options: any) => {
    delete mockStore[options.service];
    return true;
  }),
  ACCESS_CONTROL: {BIOMETRY_ANY_OR_DEVICE_PASSCODE: 'biometryAny'},
  ACCESSIBLE: {WHEN_UNLOCKED_THIS_DEVICE_ONLY: 'whenUnlockedThisDevice', ALWAYS: 'always'},
  getSupportedBiometryType: jest.fn(async () => 'FaceID'),
}));

import {
  saveMnemonic,
  getMnemonic,
  hasWallet,
  saveBtcPrivateKey,
  getBtcPrivateKey,
  saveActiveChainId,
  getActiveChainId,
  saveCustomRpcUrl,
  getCustomRpcUrl,
  clearAll,
  saveAuthInt,
  getAuthInt,
} from '../../src/storage/SecureStorage';

beforeEach(() => {
  Object.keys(mockStore).forEach(k => delete mockStore[k]);
});

describe('SecureStorage', () => {
  describe('mnemonic', () => {
    const words = ['word1', 'word2', 'word3', 'word4', 'word5', 'word6',
                   'word7', 'word8', 'word9', 'word10', 'word11', 'word12'];

    it('saves and retrieves mnemonic', async () => {
      await saveMnemonic(words);
      const result = await getMnemonic();
      expect(result).toEqual(words);
    });

    it('returns null when no mnemonic stored', async () => {
      const result = await getMnemonic();
      expect(result).toBeNull();
    });

    it('hasWallet returns true after saving mnemonic', async () => {
      await saveMnemonic(words);
      const has = await hasWallet();
      expect(has).toBe(true);
    });

    it('hasWallet returns false when no mnemonic', async () => {
      const has = await hasWallet();
      expect(has).toBe(false);
    });
  });

  describe('private keys', () => {
    it('saves and retrieves BTC WIF key', async () => {
      await saveBtcPrivateKey('KxBTC123WIF');
      const key = await getBtcPrivateKey();
      expect(key).toBe('KxBTC123WIF');
    });

    it('returns null when no BTC key stored', async () => {
      const key = await getBtcPrivateKey();
      expect(key).toBeNull();
    });
  });

  describe('settings', () => {
    it('saves and retrieves active chain ID', async () => {
      await saveActiveChainId(56);
      const chainId = await getActiveChainId();
      expect(chainId).toBe(56);
    });

    it('defaults to chain 1 when not set', async () => {
      const chainId = await getActiveChainId();
      expect(chainId).toBe(1);
    });

    it('saves and retrieves custom RPC URL', async () => {
      await saveCustomRpcUrl(1, 'https://custom.rpc.eth');
      const url = await getCustomRpcUrl(1);
      expect(url).toBe('https://custom.rpc.eth');
    });

    it('returns null for custom RPC when not set', async () => {
      const url = await getCustomRpcUrl(1);
      expect(url).toBeNull();
    });
  });

  describe('auth state', () => {
    it('saves and retrieves auth integers', async () => {
      await saveAuthInt('auth_failure_count', 3);
      const val = await getAuthInt('auth_failure_count');
      expect(val).toBe(3);
    });

    it('returns 0 for unset keys', async () => {
      const val = await getAuthInt('unknown_key');
      expect(val).toBe(0);
    });
  });
});
