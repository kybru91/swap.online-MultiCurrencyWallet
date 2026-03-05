import axios from 'axios';
import {BTC_CONFIG} from './RpcConfig';

// Constants ported from Kotlin BtcManager
export const DUST_SAT = 546;
export const P2PKH_IN_SIZE = 148;
export const P2PKH_OUT_SIZE = 34;
export const TX_SIZE = 15;
export const DEFAULT_FEE_SLOW = 5000;
export const DEFAULT_FEE_NORMAL = 15000;
export const DEFAULT_FEE_FAST = 30000;

const SATOSHI_PER_BTC = 100_000_000;

export interface BtcBalance {
  balance: number;         // confirmed balance in BTC
  unconfirmed: number;     // unconfirmed balance in BTC
}

export interface UnspentOutput {
  txid: string;
  vout: number;
  amount: number;          // in BTC
  satoshis: number;
  address: string;
  scriptPubKey: string;
}

export interface FeeRates {
  slow: number;    // sat/byte
  normal: number;
  fast: number;
}

export interface TransactionRecord {
  hash: string;
  direction: 'in' | 'out' | 'self';
  amount: number;   // in BTC or ETH depending on currency
  fee: number;
  currency: string;
  timestamp: number;        // epoch seconds
  confirmations: number;
  counterpartyAddress: string;
  blockNumber: number;
}

/**
 * Fetches BTC balance for an address via Bitpay API.
 * Port of Kotlin BtcManager.fetchBalance().
 *
 * @returns BtcBalance or null on network error (offline mode)
 */
export async function fetchBtcBalance(address: string): Promise<BtcBalance | null> {
  try {
    const url = `${BTC_CONFIG.bitpayBaseUrl}/address/${address}/balance`;
    const resp = await axios.get(url, {timeout: 10_000});
    const data = resp.data;
    return {
      balance: (data.balance ?? 0) / SATOSHI_PER_BTC,
      unconfirmed: (data.unconfirmed ?? 0) / SATOSHI_PER_BTC,
    };
  } catch {
    return null; // offline mode
  }
}

/**
 * Fetches unspent outputs (UTXOs) for an address via Bitpay API.
 * Port of Kotlin BtcManager.fetchUnspents().
 *
 * @returns array of UTXOs or empty array on error
 */
export async function fetchUtxos(address: string): Promise<UnspentOutput[]> {
  try {
    const url = `${BTC_CONFIG.bitpayBaseUrl}/address/${address}?unspent=true`;
    const resp = await axios.get(url, {timeout: 10_000});
    const utxos = Array.isArray(resp.data) ? resp.data : [];
    return utxos.map((u: any) => ({
      txid: u.mintTxid,
      vout: u.mintIndex,
      amount: (u.value ?? 0) / SATOSHI_PER_BTC,
      satoshis: u.value ?? 0,
      address: u.address ?? address,
      scriptPubKey: u.script ?? '',
    }));
  } catch {
    return [];
  }
}

/**
 * Fetches current BTC fee rates from Blockcypher.
 * Falls back to hardcoded defaults if API unavailable.
 * Port of Kotlin BtcManager.fetchFeeRates().
 */
export async function fetchFeeRates(): Promise<FeeRates> {
  try {
    const url = `${BTC_CONFIG.blockcypherBaseUrl}`;
    const resp = await axios.get(url, {timeout: 8_000});
    const data = resp.data;
    // Blockcypher returns low/medium/high fee per KB → convert to sat/byte
    const slow = Math.round((data.low_fee_per_kb ?? DEFAULT_FEE_SLOW) / 1000);
    const normal = Math.round((data.medium_fee_per_kb ?? DEFAULT_FEE_NORMAL) / 1000);
    const fast = Math.round((data.high_fee_per_kb ?? DEFAULT_FEE_FAST) / 1000);
    return {slow, normal, fast};
  } catch {
    // Fallback defaults (sat/byte)
    return {slow: 5, normal: 15, fast: 30};
  }
}

/**
 * Estimates transaction fee in BTC.
 * Port of Kotlin BtcManager fee estimation logic.
 *
 * Uses P2PKH size constants (matching web wallet's TRANSACTION.ts):
 * TX_SIZE + (inputs * P2PKH_IN_SIZE) + (outputs * P2PKH_OUT_SIZE)
 */
export function estimateFee(inputCount: number, outputCount: number, feeRateSatPerByte: number): number {
  const txSizeBytes = TX_SIZE + (inputCount * P2PKH_IN_SIZE) + (outputCount * P2PKH_OUT_SIZE);
  const feeSatoshis = txSizeBytes * feeRateSatPerByte;
  return feeSatoshis / SATOSHI_PER_BTC;
}

/**
 * Selects UTXOs for a transaction (coin selection algorithm).
 * Uses greedy selection: sort by value desc, take until amount + fee covered.
 * Port of Kotlin BtcManager UTXO selection logic.
 *
 * @param utxos available unspent outputs
 * @param targetSatoshis amount to send in satoshis
 * @param feeRateSatPerByte fee rate in sat/byte
 * @returns selected UTXOs, or null if insufficient funds
 */
export interface CoinSelectionResult {
  selected: UnspentOutput[];
  feeSatoshis: number;
  changeSatoshis: number;
}

export function selectCoins(
  utxos: UnspentOutput[],
  targetSatoshis: number,
  feeRateSatPerByte: number,
): CoinSelectionResult | null {
  // Filter dust
  const spendable = utxos.filter(u => u.satoshis >= DUST_SAT);
  // Sort descending by value
  const sorted = [...spendable].sort((a, b) => b.satoshis - a.satoshis);

  const selected: UnspentOutput[] = [];
  let accumulated = 0;

  for (const utxo of sorted) {
    selected.push(utxo);
    accumulated += utxo.satoshis;

    // Calculate fee with current selection (2 outputs: recipient + change)
    const feeEstimate = TX_SIZE + (selected.length * P2PKH_IN_SIZE) + (2 * P2PKH_OUT_SIZE);
    const feeSatoshis = feeEstimate * feeRateSatPerByte;
    const totalNeeded = targetSatoshis + feeSatoshis;

    if (accumulated >= totalNeeded) {
      const changeSatoshis = accumulated - totalNeeded;
      // If change is dust, no change output (add to fee)
      if (changeSatoshis < DUST_SAT && changeSatoshis > 0) {
        // Recalculate without change output
        const feeNoChange = (TX_SIZE + (selected.length * P2PKH_IN_SIZE) + P2PKH_OUT_SIZE) * feeRateSatPerByte;
        return {selected, feeSatoshis: accumulated - targetSatoshis, changeSatoshis: 0};
      }
      return {selected, feeSatoshis, changeSatoshis};
    }
  }

  return null; // insufficient funds
}

/**
 * Broadcasts a signed BTC transaction via Bitpay API.
 * Port of Kotlin BtcManager.broadcastTransaction().
 *
 * @param rawTxHex hex-encoded signed transaction
 * @returns transaction hash, or null on error
 */
export async function broadcastBtcTransaction(rawTxHex: string): Promise<string | null> {
  try {
    const url = `${BTC_CONFIG.bitpayBaseUrl}/tx/send`;
    const resp = await axios.post(url, {rawTx: rawTxHex}, {timeout: 15_000});
    return resp.data?.txid ?? null;
  } catch {
    return null;
  }
}

/**
 * Fetches BTC transaction history via Blockcypher API.
 * Port of Kotlin BtcManager.fetchTransactionHistory().
 *
 * @returns sorted transaction records (newest first), or null on error
 */
export async function fetchBtcHistory(address: string): Promise<TransactionRecord[] | null> {
  try {
    const url = `${BTC_CONFIG.blockcypherBaseUrl}/addrs/${address}?limit=50`;
    const resp = await axios.get(url, {timeout: 10_000});
    const data = resp.data;

    const confirmedRefs = data.txrefs ?? [];
    const unconfirmedRefs = data.unconfirmed_txrefs ?? [];
    const allRefs = [...confirmedRefs, ...unconfirmedRefs];

    if (allRefs.length === 0) return [];

    // Group by tx_hash (a tx can appear multiple times for same address)
    const grouped: Record<string, any[]> = {};
    for (const ref of allRefs) {
      const hash = ref.tx_hash;
      if (!grouped[hash]) grouped[hash] = [];
      grouped[hash].push(ref);
    }

    const records = Object.entries(grouped).map(([hash, refs]) =>
      parseBtcTxRefs(hash, refs, address),
    );

    return records.sort((a, b) => b.timestamp - a.timestamp);
  } catch {
    return null;
  }
}

function parseBtcTxRefs(hash: string, refs: any[], walletAddress: string): TransactionRecord {
  const receivedRefs = refs.filter((r: any) => r.tx_input_n === -1);
  const spentRefs = refs.filter((r: any) => r.tx_input_n >= 0);

  const hasReceived = receivedRefs.length > 0;
  const hasSpent = spentRefs.length > 0;

  let direction: 'in' | 'out' | 'self';
  let amountSatoshis: number;

  if (hasReceived && hasSpent) {
    direction = 'self';
    amountSatoshis = receivedRefs.reduce((sum: number, r: any) => sum + (r.value ?? 0), 0);
  } else if (hasReceived) {
    direction = 'in';
    amountSatoshis = receivedRefs.reduce((sum: number, r: any) => sum + (r.value ?? 0), 0);
  } else {
    direction = 'out';
    amountSatoshis = spentRefs.reduce((sum: number, r: any) => sum + (r.value ?? 0), 0);
  }

  const firstRef = refs[0];
  const timestamp = parseBlockcypherTimestamp(firstRef.confirmed);

  return {
    hash,
    direction,
    amount: amountSatoshis / SATOSHI_PER_BTC,
    fee: 0,
    currency: 'BTC',
    timestamp,
    confirmations: firstRef.confirmations ?? 0,
    counterpartyAddress: walletAddress,
    blockNumber: firstRef.block_height ?? 0,
  };
}

function parseBlockcypherTimestamp(ts: string | null): number {
  if (!ts) return 0;
  try {
    return Math.floor(new Date(ts).getTime() / 1000);
  } catch {
    return 0;
  }
}
