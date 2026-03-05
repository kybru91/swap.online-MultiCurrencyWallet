import {getCustomRpcUrl} from '@storage/SecureStorage';

/**
 * EVM chain configuration (matching web wallet's externalConfig chains).
 * Supports failover: primary + fallback RPC endpoints.
 */
export interface ChainConfig {
  chainId: number;
  name: string;
  symbol: string;       // native currency symbol
  decimals: number;
  rpcUrls: string[];    // ordered by priority, failover to next on error
  explorerUrl: string;
  coingeckoId: string;  // for CoinGecko price API
}

export const SUPPORTED_CHAINS: ChainConfig[] = [
  {
    chainId: 1,
    name: 'Ethereum',
    symbol: 'ETH',
    decimals: 18,
    rpcUrls: [
      'https://cloudflare-eth.com',
      'https://rpc.ankr.com/eth',
      'https://ethereum.publicnode.com',
    ],
    explorerUrl: 'https://etherscan.io',
    coingeckoId: 'ethereum',
  },
  {
    chainId: 56,
    name: 'BNB Smart Chain',
    symbol: 'BNB',
    decimals: 18,
    rpcUrls: [
      'https://bsc-dataseed.binance.org',
      'https://bsc-dataseed1.defibit.io',
      'https://rpc.ankr.com/bsc',
    ],
    explorerUrl: 'https://bscscan.com',
    coingeckoId: 'binancecoin',
  },
  {
    chainId: 137,
    name: 'Polygon',
    symbol: 'MATIC',
    decimals: 18,
    rpcUrls: [
      'https://polygon-rpc.com',
      'https://rpc.ankr.com/polygon',
      'https://polygon.publicnode.com',
    ],
    explorerUrl: 'https://polygonscan.com',
    coingeckoId: 'matic-network',
  },
];

/** Default active chain: ETH mainnet */
export const DEFAULT_CHAIN_ID = 1;

/**
 * Returns the active RPC URL for a chain, with custom override support.
 * Custom URL (set by user in Settings) takes priority over defaults.
 */
export async function getActiveRpcUrl(chainId: number): Promise<string> {
  const customUrl = await getCustomRpcUrl(chainId);
  if (customUrl) {
    return customUrl;
  }
  const chain = SUPPORTED_CHAINS.find(c => c.chainId === chainId);
  if (!chain) {
    throw new Error(`Unsupported chain ID: ${chainId}`);
  }
  return chain.rpcUrls[0];
}

/**
 * Returns chain config by chain ID.
 * @throws Error if chain is not supported
 */
export function getChainConfig(chainId: number): ChainConfig {
  const chain = SUPPORTED_CHAINS.find(c => c.chainId === chainId);
  if (!chain) {
    throw new Error(`Unsupported chain ID: ${chainId}`);
  }
  return chain;
}

/**
 * BTC network configuration.
 * API: Bitpay (Bitcore) for balance/UTXOs/broadcast, Blockcypher for fees/history
 */
export const BTC_CONFIG = {
  bitpayBaseUrl: 'https://api.bitcore.io/api/BTC/mainnet',
  blockcypherBaseUrl: 'https://api.blockcypher.com/v1/btc/main',
};
