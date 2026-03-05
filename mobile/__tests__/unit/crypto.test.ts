/**
 * Unit tests for BIP39/BIP44 crypto module.
 * Tests match Kotlin CryptoManagerTest behavior for cross-platform compatibility.
 * @jest-environment node
 */

// Mock polyfills for test environment
jest.mock('react-native-get-random-values', () => ({}));
jest.mock('react-native-quick-crypto', () => ({
  createHash: jest.fn(() => ({
    update: jest.fn().mockReturnThis(),
    digest: jest.fn(() => Buffer.alloc(32)),
  })),
}));

import {generateMnemonic, validateMnemonic, normalizeMnemonic} from '../../src/crypto/mnemonic';

describe('Mnemonic', () => {
  describe('generateMnemonic', () => {
    it('generates a 12-word mnemonic', () => {
      const mnemonic = generateMnemonic();
      const words = mnemonic.split(' ');
      expect(words).toHaveLength(12);
    });

    it('generates different mnemonics each time', () => {
      const m1 = generateMnemonic();
      const m2 = generateMnemonic();
      expect(m1).not.toBe(m2);
    });

    it('returns only lowercase BIP39 words', () => {
      const words = generateMnemonic().split(' ');
      for (const word of words) {
        expect(word).toMatch(/^[a-z]+$/);
      }
    });
  });

  describe('normalizeMnemonic', () => {
    it('trims whitespace', () => {
      const words = normalizeMnemonic('  word1 word2  ');
      expect(words[0]).toBe('word1');
      expect(words[words.length - 1]).toBe('word2');
    });

    it('collapses multiple spaces', () => {
      const words = normalizeMnemonic('word1  word2   word3');
      expect(words).toHaveLength(3);
    });

    it('lowercases all words', () => {
      const words = normalizeMnemonic('WORD1 WORD2');
      expect(words[0]).toBe('word1');
    });
  });

  describe('validateMnemonic', () => {
    const VALID_MNEMONIC =
      'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';

    it('accepts a valid 12-word mnemonic', () => {
      expect(validateMnemonic(VALID_MNEMONIC)).toBe(true);
    });

    it('throws on wrong word count', () => {
      expect(() => validateMnemonic('one two three')).toThrow(
        'Invalid word count: expected 12, got 3',
      );
    });

    it('throws on word not in BIP39 wordlist', () => {
      const bad =
        'notaword abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';
      expect(() => validateMnemonic(bad)).toThrow("Word 'notaword' not in BIP39 wordlist");
    });

    it('throws on invalid checksum', () => {
      // Valid words but wrong checksum
      const badChecksum =
        'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon';
      expect(() => validateMnemonic(badChecksum)).toThrow('Invalid mnemonic checksum');
    });

    it('normalizes input before validation', () => {
      const withExtraSpaces = '  ' + VALID_MNEMONIC.toUpperCase() + '  ';
      expect(validateMnemonic(withExtraSpaces)).toBe(true);
    });
  });
});
