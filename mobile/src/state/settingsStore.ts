import {create} from 'zustand';
import {
  getActiveChainId,
  saveActiveChainId,
  getCustomRpcUrl,
  saveCustomRpcUrl,
} from '@storage/SecureStorage';
import {SUPPORTED_CHAINS, DEFAULT_CHAIN_ID} from '@network/RpcConfig';

export interface SettingsState {
  activeChainId: number;
  customRpcUrls: Record<number, string>;
  initialized: boolean;

  // Actions
  initSettings: () => Promise<void>;
  setActiveChain: (chainId: number) => Promise<void>;
  setCustomRpc: (chainId: number, url: string) => Promise<void>;
  clearCustomRpc: (chainId: number) => Promise<void>;
}

/**
 * Zustand store for app settings.
 * Persists active chain and custom RPC URLs via SecureStorage.
 */
export const useSettingsStore = create<SettingsState>((set, get) => ({
  activeChainId: DEFAULT_CHAIN_ID,
  customRpcUrls: {},
  initialized: false,

  initSettings: async () => {
    const activeChainId = await getActiveChainId();

    // Load custom RPC URLs for all supported chains
    const customRpcUrls: Record<number, string> = {};
    for (const chain of SUPPORTED_CHAINS) {
      const url = await getCustomRpcUrl(chain.chainId);
      if (url) {
        customRpcUrls[chain.chainId] = url;
      }
    }

    set({activeChainId, customRpcUrls, initialized: true});
  },

  setActiveChain: async (chainId: number) => {
    await saveActiveChainId(chainId);
    set({activeChainId: chainId});
  },

  setCustomRpc: async (chainId: number, url: string) => {
    await saveCustomRpcUrl(chainId, url);
    set(state => ({
      customRpcUrls: {...state.customRpcUrls, [chainId]: url},
    }));
  },

  clearCustomRpc: async (chainId: number) => {
    // Save empty string to effectively clear (getCustomRpcUrl returns null for empty)
    await saveCustomRpcUrl(chainId, '');
    set(state => {
      const updated = {...state.customRpcUrls};
      delete updated[chainId];
      return {customRpcUrls: updated};
    });
  },
}));
