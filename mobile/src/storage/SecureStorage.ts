import * as Keychain from 'react-native-keychain';

/**
 * Secure storage wrapper using react-native-keychain.
 * iOS: Keychain Services, Android: Android Keystore + EncryptedSharedPreferences
 *
 * Mirrors Kotlin SecureStorage API exactly for easy cross-platform parity.
 */

const SERVICE_MNEMONIC = 'mcw.mnemonic';
const SERVICE_BTC_WIF = 'mcw.btc.wif';
const SERVICE_ETH_HEX = 'mcw.eth.hex';
const SERVICE_PASSWORD_HASH = 'mcw.password.hash';
const SERVICE_WC_SESSIONS = 'mcw.wc.sessions';
const SERVICE_AUTH_STATE = 'mcw.auth.state';
const SERVICE_SETTINGS = 'mcw.settings';

// --- Mnemonic ---

/**
 * Stores BIP39 mnemonic as space-separated words.
 */
export async function saveMnemonic(words: string[]): Promise<void> {
  await Keychain.setGenericPassword('mnemonic', words.join(' '), {
    service: SERVICE_MNEMONIC,
    accessControl: Keychain.ACCESS_CONTROL.BIOMETRY_ANY_OR_DEVICE_PASSCODE,
    accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

/**
 * Retrieves BIP39 mnemonic as array of words.
 * @returns word array, or null if no mnemonic stored
 */
export async function getMnemonic(): Promise<string[] | null> {
  const result = await Keychain.getGenericPassword({service: SERVICE_MNEMONIC});
  if (!result) return null;
  return result.password.split(' ');
}

/**
 * Checks whether a wallet (mnemonic) exists in secure storage.
 */
export async function hasWallet(): Promise<boolean> {
  const result = await Keychain.getGenericPassword({service: SERVICE_MNEMONIC});
  return result !== false;
}

// --- Private Keys ---

/**
 * Stores BTC WIF private key.
 */
export async function saveBtcPrivateKey(wif: string): Promise<void> {
  await Keychain.setGenericPassword('btc', wif, {
    service: SERVICE_BTC_WIF,
    accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

/**
 * Retrieves BTC WIF private key.
 */
export async function getBtcPrivateKey(): Promise<string | null> {
  const result = await Keychain.getGenericPassword({service: SERVICE_BTC_WIF});
  return result ? result.password : null;
}

/**
 * Stores ETH hex private key.
 */
export async function saveEthPrivateKey(hex: string): Promise<void> {
  await Keychain.setGenericPassword('eth', hex, {
    service: SERVICE_ETH_HEX,
    accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

/**
 * Retrieves ETH hex private key.
 */
export async function getEthPrivateKey(): Promise<string | null> {
  const result = await Keychain.getGenericPassword({service: SERVICE_ETH_HEX});
  return result ? result.password : null;
}

// --- Password Hash ---

/**
 * Stores password hash (bcrypt format).
 */
export async function savePasswordHash(hash: string): Promise<void> {
  await Keychain.setGenericPassword('password', hash, {
    service: SERVICE_PASSWORD_HASH,
    accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

/**
 * Retrieves password hash.
 */
export async function getPasswordHash(): Promise<string | null> {
  const result = await Keychain.getGenericPassword({service: SERVICE_PASSWORD_HASH});
  return result ? result.password : null;
}

// --- WalletConnect Sessions ---

/**
 * Stores WalletConnect v2 session data as JSON string.
 */
export async function saveWalletConnectSessions(json: string): Promise<void> {
  await Keychain.setGenericPassword('wc', json, {
    service: SERVICE_WC_SESSIONS,
    accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

/**
 * Retrieves WalletConnect session data.
 */
export async function getWalletConnectSessions(): Promise<string | null> {
  const result = await Keychain.getGenericPassword({service: SERVICE_WC_SESSIONS});
  return result ? result.password : null;
}

// --- Auth State (lockout persistence) ---
// Stores auth state as JSON object in keychain

async function getAuthState(): Promise<Record<string, number>> {
  const result = await Keychain.getGenericPassword({service: SERVICE_AUTH_STATE});
  if (!result) return {};
  try {
    return JSON.parse(result.password);
  } catch {
    return {};
  }
}

async function setAuthState(state: Record<string, number>): Promise<void> {
  await Keychain.setGenericPassword('auth', JSON.stringify(state), {
    service: SERVICE_AUTH_STATE,
    accessible: Keychain.ACCESSIBLE.ALWAYS,
  });
}

export async function saveAuthInt(key: string, value: number): Promise<void> {
  const state = await getAuthState();
  state[key] = value;
  await setAuthState(state);
}

export async function getAuthInt(key: string): Promise<number> {
  const state = await getAuthState();
  return state[key] ?? 0;
}

export async function saveAuthLong(key: string, value: number): Promise<void> {
  await saveAuthInt(key, value);
}

export async function getAuthLong(key: string): Promise<number> {
  return getAuthInt(key);
}

// --- Settings (non-sensitive) ---
// Active chain ID, custom RPC config — stored in keychain for simplicity

async function getSettings(): Promise<Record<string, unknown>> {
  const result = await Keychain.getGenericPassword({service: SERVICE_SETTINGS});
  if (!result) return {};
  try {
    return JSON.parse(result.password);
  } catch {
    return {};
  }
}

async function setSettings(settings: Record<string, unknown>): Promise<void> {
  await Keychain.setGenericPassword('settings', JSON.stringify(settings), {
    service: SERVICE_SETTINGS,
    accessible: Keychain.ACCESSIBLE.ALWAYS,
  });
}

export async function saveActiveChainId(chainId: number): Promise<void> {
  const settings = await getSettings();
  settings.activeChainId = chainId;
  await setSettings(settings);
}

export async function getActiveChainId(): Promise<number> {
  const settings = await getSettings();
  return (settings.activeChainId as number) ?? 1; // default: ETH mainnet
}

export async function saveCustomRpcUrl(chainId: number, url: string): Promise<void> {
  const settings = await getSettings();
  const customRpc = (settings.customRpc as Record<string, string>) ?? {};
  customRpc[String(chainId)] = url;
  settings.customRpc = customRpc;
  await setSettings(settings);
}

export async function getCustomRpcUrl(chainId: number): Promise<string | null> {
  const settings = await getSettings();
  const customRpc = (settings.customRpc as Record<string, string>) ?? {};
  return customRpc[String(chainId)] ?? null;
}

// --- Clear ---

/**
 * Wipes all secure storage. Use on wallet reset or KeyStore corruption.
 */
export async function clearAll(): Promise<void> {
  await Promise.allSettled([
    Keychain.resetGenericPassword({service: SERVICE_MNEMONIC}),
    Keychain.resetGenericPassword({service: SERVICE_BTC_WIF}),
    Keychain.resetGenericPassword({service: SERVICE_ETH_HEX}),
    Keychain.resetGenericPassword({service: SERVICE_PASSWORD_HASH}),
    Keychain.resetGenericPassword({service: SERVICE_WC_SESSIONS}),
    Keychain.resetGenericPassword({service: SERVICE_AUTH_STATE}),
    Keychain.resetGenericPassword({service: SERVICE_SETTINGS}),
  ]);
}
