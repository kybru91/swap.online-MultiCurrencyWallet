import {create} from 'zustand';
import {fetchBtcBalance, fetchBtcHistory, type TransactionRecord} from '@network/BtcApi';
import {fetchEvmBalance, fetchEvmHistory, fetchCoinGeckoPrices} from '@network/EvmApi';
import {getMnemonic, saveMnemonic, saveBtcPrivateKey, saveEthPrivateKey} from '@storage/SecureStorage';
import {deriveKeys, type WalletKeys} from '@crypto/index';

export interface WalletBalance {
  btcBalance: string | null;    // in BTC
  ethBalance: string | null;    // in ETH
  btcUsd: number | null;
  ethUsd: number | null;
}

export interface WalletState {
  // Addresses (public info, not secret)
  btcAddress: string | null;
  ethAddress: string | null;

  // Balances
  balances: WalletBalance;
  balancesLoading: boolean;
  balancesError: string | null;

  // Transaction history
  btcHistory: TransactionRecord[] | null;
  ethHistory: TransactionRecord[] | null;
  historyLoading: boolean;

  // Active EVM chain
  activeChainId: number;

  // Wallet actions
  initWallet: () => Promise<void>;
  createWallet: (mnemonic: string[]) => Promise<void>;
  importWallet: (mnemonic: string[]) => Promise<void>;
  refreshBalances: () => Promise<void>;
  refreshHistory: () => Promise<void>;
  setActiveChain: (chainId: number) => void;
  clearWallet: () => void;
}

/**
 * Zustand store for wallet state: addresses, balances, transaction history.
 *
 * Replaces the ViewModel + Repository pattern from the Kotlin app.
 * State is derived from SecureStorage on init, never duplicates private keys.
 */
export const useWalletStore = create<WalletState>((set, get) => ({
  btcAddress: null,
  ethAddress: null,
  balances: {
    btcBalance: null,
    ethBalance: null,
    btcUsd: null,
    ethUsd: null,
  },
  balancesLoading: false,
  balancesError: null,
  btcHistory: null,
  ethHistory: null,
  historyLoading: false,
  activeChainId: 1,

  /**
   * Initializes wallet from stored mnemonic.
   * Derives addresses (not keys — keys stay in keychain).
   * Call on app start after authentication.
   */
  initWallet: async () => {
    const words = await getMnemonic();
    if (!words) return;

    const keys = await deriveKeys(words.join(' '));
    set({
      btcAddress: keys.btcAddress,
      ethAddress: keys.ethAddress,
    });

    // Auto-fetch balances on init
    get().refreshBalances();
  },

  /**
   * Creates a new wallet from generated mnemonic.
   * Stores mnemonic + derived private keys in secure storage.
   */
  createWallet: async (words: string[]) => {
    const keys: WalletKeys = await deriveKeys(words.join(' '));
    await saveMnemonic(words);
    await saveBtcPrivateKey(keys.btcPrivateKeyWIF);
    await saveEthPrivateKey(keys.ethPrivateKeyHex);
    set({
      btcAddress: keys.btcAddress,
      ethAddress: keys.ethAddress,
    });
  },

  /**
   * Imports an existing wallet from mnemonic.
   * Same flow as createWallet — stores keys and derives addresses.
   */
  importWallet: async (words: string[]) => {
    const keys: WalletKeys = await deriveKeys(words.join(' '));
    await saveMnemonic(words);
    await saveBtcPrivateKey(keys.btcPrivateKeyWIF);
    await saveEthPrivateKey(keys.ethPrivateKeyHex);
    set({
      btcAddress: keys.btcAddress,
      ethAddress: keys.ethAddress,
    });
    get().refreshBalances();
  },

  /**
   * Refreshes BTC + ETH balances and USD prices from network.
   * Null result = offline mode (API unavailable).
   */
  refreshBalances: async () => {
    const {btcAddress, ethAddress, activeChainId} = get();
    if (!btcAddress || !ethAddress) return;

    set({balancesLoading: true, balancesError: null});

    try {
      // Fetch all in parallel
      const [btcBal, ethBal, prices] = await Promise.all([
        fetchBtcBalance(btcAddress),
        fetchEvmBalance(ethAddress, activeChainId),
        fetchCoinGeckoPrices(['BTC', 'ETH']),
      ]);

      set({
        balances: {
          btcBalance: btcBal ? String(btcBal.balance) : null,
          ethBalance: ethBal,
          btcUsd: prices?.BTC ?? null,
          ethUsd: prices?.ETH ?? null,
        },
        balancesLoading: false,
      });
    } catch (err) {
      set({
        balancesLoading: false,
        balancesError: 'Failed to fetch balances',
      });
    }
  },

  /**
   * Refreshes transaction history for BTC + ETH.
   */
  refreshHistory: async () => {
    const {btcAddress, ethAddress, activeChainId} = get();
    if (!btcAddress || !ethAddress) return;

    set({historyLoading: true});

    const [btcHist, ethHist] = await Promise.all([
      fetchBtcHistory(btcAddress),
      fetchEvmHistory(ethAddress, activeChainId),
    ]);

    set({
      btcHistory: btcHist,
      ethHistory: ethHist,
      historyLoading: false,
    });
  },

  setActiveChain: (chainId: number) => {
    set({activeChainId: chainId});
  },

  clearWallet: () => {
    set({
      btcAddress: null,
      ethAddress: null,
      balances: {btcBalance: null, ethBalance: null, btcUsd: null, ethUsd: null},
      btcHistory: null,
      ethHistory: null,
    });
  },
}));
