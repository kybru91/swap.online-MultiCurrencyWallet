import axios from 'axios';
import {getActiveRpcUrl, getChainConfig} from './RpcConfig';
import type {TransactionRecord} from './BtcApi';

const WEI_PER_ETH = BigInt('1000000000000000000');

export interface EvmBalance {
  native: string;  // in ETH/BNB/MATIC as decimal string
  nativeUsd: number | null;
}

export interface GasEstimate {
  gasPrice: bigint;
  gasLimit: bigint;
  totalFeeWei: bigint;
  totalFeeNative: string;
}

/**
 * Fetches native token balance for an EVM address via JSON-RPC.
 * Port of Kotlin EvmManager.fetchNativeBalance().
 *
 * @param address 0x-prefixed EVM address
 * @param chainId chain ID (1=ETH, 56=BSC, 137=Polygon)
 * @returns balance as decimal string (ETH units), or null on error
 */
export async function fetchEvmBalance(address: string, chainId: number): Promise<string | null> {
  try {
    const rpcUrl = await getActiveRpcUrl(chainId);
    const resp = await axios.post(
      rpcUrl,
      {
        jsonrpc: '2.0',
        id: 1,
        method: 'eth_getBalance',
        params: [address, 'latest'],
      },
      {timeout: 10_000},
    );

    const balanceHex: string = resp.data?.result;
    if (!balanceHex) return null;

    const balanceWei = BigInt(balanceHex);
    return formatWeiToEth(balanceWei);
  } catch {
    return null;
  }
}

/**
 * Fetches ERC20 token balance via eth_call.
 * Port of Kotlin EvmManager.fetchTokenBalance().
 */
export async function fetchTokenBalance(
  tokenAddress: string,
  walletAddress: string,
  decimals: number,
  chainId: number,
): Promise<string | null> {
  try {
    const rpcUrl = await getActiveRpcUrl(chainId);
    // balanceOf(address) selector: 0x70a08231
    const paddedAddress = walletAddress.toLowerCase().replace('0x', '').padStart(64, '0');
    const data = `0x70a08231${paddedAddress}`;

    const resp = await axios.post(
      rpcUrl,
      {
        jsonrpc: '2.0',
        id: 1,
        method: 'eth_call',
        params: [{to: tokenAddress, data}, 'latest'],
      },
      {timeout: 10_000},
    );

    const resultHex: string = resp.data?.result;
    if (!resultHex || resultHex === '0x') return '0';

    const raw = BigInt(resultHex);
    const divisor = BigInt(10) ** BigInt(decimals);
    const whole = raw / divisor;
    const frac = raw % divisor;
    return `${whole}.${frac.toString().padStart(decimals, '0').replace(/0+$/, '') || '0'}`;
  } catch {
    return null;
  }
}

/**
 * Fetches current gas price via eth_gasPrice JSON-RPC.
 * Port of Kotlin EvmManager.estimateGas().
 */
export async function fetchGasPrice(chainId: number): Promise<bigint | null> {
  try {
    const rpcUrl = await getActiveRpcUrl(chainId);
    const resp = await axios.post(
      rpcUrl,
      {jsonrpc: '2.0', id: 1, method: 'eth_gasPrice', params: []},
      {timeout: 8_000},
    );
    const hex: string = resp.data?.result;
    return hex ? BigInt(hex) : null;
  } catch {
    return null;
  }
}

/**
 * Estimates gas limit for a transaction via eth_estimateGas.
 * Applies 1.05x buffer for token transfers (matching Kotlin EvmManager logic).
 */
export async function estimateGasLimit(
  from: string,
  to: string,
  value: bigint,
  data: string,
  isTokenTransfer: boolean,
  chainId: number,
): Promise<bigint | null> {
  try {
    const rpcUrl = await getActiveRpcUrl(chainId);
    const resp = await axios.post(
      rpcUrl,
      {
        jsonrpc: '2.0',
        id: 1,
        method: 'eth_estimateGas',
        params: [{
          from,
          to,
          value: `0x${value.toString(16)}`,
          data: data || '0x',
        }],
      },
      {timeout: 10_000},
    );

    const hex: string = resp.data?.result;
    if (!hex) return null;

    const raw = BigInt(hex);
    // Apply 1.05x buffer for token transfers
    if (isTokenTransfer) {
      return (raw * BigInt(105)) / BigInt(100);
    }
    return raw;
  } catch {
    return null;
  }
}

/**
 * Broadcasts a signed EVM transaction via eth_sendRawTransaction.
 * Port of Kotlin EvmManager.broadcastTransaction().
 *
 * @returns transaction hash, or null on error
 */
export async function broadcastEvmTransaction(
  signedTxHex: string,
  chainId: number,
): Promise<string | null> {
  try {
    const rpcUrl = await getActiveRpcUrl(chainId);
    const resp = await axios.post(
      rpcUrl,
      {
        jsonrpc: '2.0',
        id: 1,
        method: 'eth_sendRawTransaction',
        params: [signedTxHex],
      },
      {timeout: 15_000},
    );

    if (resp.data?.error) return null;
    return resp.data?.result ?? null;
  } catch {
    return null;
  }
}

/**
 * Fetches transaction nonce for an address via eth_getTransactionCount.
 */
export async function fetchNonce(address: string, chainId: number): Promise<number | null> {
  try {
    const rpcUrl = await getActiveRpcUrl(chainId);
    const resp = await axios.post(
      rpcUrl,
      {
        jsonrpc: '2.0',
        id: 1,
        method: 'eth_getTransactionCount',
        params: [address, 'latest'],
      },
      {timeout: 8_000},
    );
    const hex: string = resp.data?.result;
    return hex ? Number(BigInt(hex)) : null;
  } catch {
    return null;
  }
}

/**
 * Fetches EVM transaction history via Etherscan API (v2 multi-chain).
 * Port of Kotlin EvmManager.fetchTransactionHistory().
 *
 * Note: API key is optional for low-volume usage.
 */
export async function fetchEvmHistory(
  address: string,
  chainId: number,
  apiKey: string = '',
): Promise<TransactionRecord[] | null> {
  try {
    const chain = getChainConfig(chainId);
    // Etherscan v2 endpoint: supports chainid parameter
    const url = `https://api.etherscan.io/v2/api?chainid=${chainId}&module=account&action=txlist&address=${address}&startblock=0&endblock=99999999&sort=desc&apikey=${apiKey}`;
    const resp = await axios.get(url, {timeout: 15_000});

    if (resp.data?.status !== '1') return [];

    return resp.data.result.map((tx: any) =>
      parseEvmTransaction(tx, address, chain.symbol),
    );
  } catch {
    return null;
  }
}

function parseEvmTransaction(tx: any, walletAddress: string, symbol: string): TransactionRecord {
  const isReceived = tx.to?.toLowerCase() === walletAddress.toLowerCase();
  const direction = isReceived ? 'in' : 'out';
  const valueWei = BigInt(tx.value ?? '0');

  return {
    hash: tx.hash,
    direction,
    amount: Number(formatWeiToEth(valueWei)),
    fee: Number(formatWeiToEth(BigInt(tx.gasUsed ?? 0) * BigInt(tx.gasPrice ?? 0))),
    currency: symbol,
    timestamp: Number(tx.timeStamp ?? 0),
    confirmations: Number(tx.confirmations ?? 0),
    counterpartyAddress: isReceived ? tx.from : tx.to,
    blockNumber: Number(tx.blockNumber ?? 0),
  };
}

// --- CoinGecko Price API ---

/**
 * CoinGecko ID map (matching Kotlin EvmManager.COINGECKO_IDS).
 */
export const COINGECKO_IDS: Record<string, string> = {
  BTC: 'bitcoin',
  ETH: 'ethereum',
  BNB: 'binancecoin',
  MATIC: 'matic-network',
};

/**
 * Fetches USD prices from CoinGecko free API.
 * Port of Kotlin EvmManager.fetchFiatPrices().
 *
 * @param symbols array of currency symbols, e.g. ['BTC', 'ETH']
 * @returns map of symbol → USD price, or null on error
 */
export async function fetchCoinGeckoPrices(
  symbols: string[],
): Promise<Record<string, number> | null> {
  try {
    const ids = symbols
      .map(s => COINGECKO_IDS[s.toUpperCase()])
      .filter(Boolean)
      .join(',');

    if (!ids) return {};

    const url = `https://api.coingecko.com/api/v3/simple/price?ids=${ids}&vs_currencies=usd`;
    const resp = await axios.get(url, {timeout: 10_000});

    const result: Record<string, number> = {};
    for (const symbol of symbols) {
      const coinId = COINGECKO_IDS[symbol.toUpperCase()];
      if (coinId && resp.data[coinId]?.usd) {
        result[symbol] = resp.data[coinId].usd;
      }
    }
    return result;
  } catch {
    return null;
  }
}

// --- Helpers ---

function formatWeiToEth(wei: bigint): string {
  const whole = wei / WEI_PER_ETH;
  const frac = wei % WEI_PER_ETH;
  const fracStr = frac.toString().padStart(18, '0').replace(/0+$/, '');
  if (!fracStr) return whole.toString();
  return `${whole}.${fracStr}`;
}
