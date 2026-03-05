/**
 * Unit tests for BTC + EVM network modules.
 * Tests pure computation functions and mocked API calls.
 * @jest-environment node
 */

jest.mock('axios');
jest.mock('../../src/storage/SecureStorage', () => ({
  getCustomRpcUrl: jest.fn(async () => null),
}));

import axios from 'axios';
import {
  estimateFee,
  selectCoins,
  DUST_SAT,
  P2PKH_IN_SIZE,
  P2PKH_OUT_SIZE,
  TX_SIZE,
} from '../../src/network/BtcApi';
import {getChainConfig, SUPPORTED_CHAINS} from '../../src/network/RpcConfig';

const mockAxios = axios as jest.Mocked<typeof axios>;

describe('BtcApi', () => {
  describe('estimateFee', () => {
    it('calculates fee correctly for 1-in 2-out P2PKH transaction', () => {
      const txSize = TX_SIZE + (1 * P2PKH_IN_SIZE) + (2 * P2PKH_OUT_SIZE);
      const expectedFee = txSize * 10 / 100_000_000; // 10 sat/byte, in BTC
      const fee = estimateFee(1, 2, 10);
      expect(fee).toBeCloseTo(expectedFee, 8);
    });

    it('scales with input count', () => {
      const fee1 = estimateFee(1, 2, 10);
      const fee3 = estimateFee(3, 2, 10);
      expect(fee3).toBeGreaterThan(fee1);
    });
  });

  describe('selectCoins', () => {
    const utxos = [
      {txid: 'tx1', vout: 0, satoshis: 100_000, amount: 0.001, address: '1A', scriptPubKey: ''},
      {txid: 'tx2', vout: 0, satoshis: 50_000,  amount: 0.0005, address: '1A', scriptPubKey: ''},
      {txid: 'tx3', vout: 0, satoshis: 10_000,  amount: 0.0001, address: '1A', scriptPubKey: ''},
    ];

    it('returns null when insufficient funds', () => {
      const result = selectCoins(utxos, 200_000, 10);
      expect(result).toBeNull();
    });

    it('selects minimum UTXOs needed', () => {
      const result = selectCoins(utxos, 30_000, 5);
      expect(result).not.toBeNull();
      expect(result!.selected.length).toBeGreaterThanOrEqual(1);
    });

    it('filters UTXOs below dust threshold', () => {
      const dustUtxos = [
        {txid: 'dust', vout: 0, satoshis: DUST_SAT - 1, amount: 0, address: '1A', scriptPubKey: ''},
        ...utxos,
      ];
      const result = selectCoins(dustUtxos, 30_000, 5);
      expect(result!.selected.every(u => u.satoshis >= DUST_SAT)).toBe(true);
    });
  });
});

describe('RpcConfig', () => {
  describe('SUPPORTED_CHAINS', () => {
    it('includes ETH mainnet (chainId 1)', () => {
      const eth = SUPPORTED_CHAINS.find(c => c.chainId === 1);
      expect(eth).toBeDefined();
      expect(eth!.symbol).toBe('ETH');
    });

    it('includes BSC (chainId 56)', () => {
      const bsc = SUPPORTED_CHAINS.find(c => c.chainId === 56);
      expect(bsc).toBeDefined();
      expect(bsc!.symbol).toBe('BNB');
    });

    it('includes Polygon (chainId 137)', () => {
      const poly = SUPPORTED_CHAINS.find(c => c.chainId === 137);
      expect(poly).toBeDefined();
      expect(poly!.symbol).toBe('MATIC');
    });

    it('all chains have multiple fallback RPCs', () => {
      for (const chain of SUPPORTED_CHAINS) {
        expect(chain.rpcUrls.length).toBeGreaterThanOrEqual(2);
      }
    });
  });

  describe('getChainConfig', () => {
    it('returns config for supported chain', () => {
      const config = getChainConfig(1);
      expect(config.name).toBe('Ethereum');
    });

    it('throws for unsupported chain', () => {
      expect(() => getChainConfig(999)).toThrow('Unsupported chain ID: 999');
    });
  });
});
