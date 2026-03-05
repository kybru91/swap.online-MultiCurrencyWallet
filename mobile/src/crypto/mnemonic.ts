import * as bip39 from 'bip39';

const EXPECTED_WORD_COUNT = 12;

/**
 * Normalizes mnemonic input: trim, lowercase, collapse multiple spaces.
 * Matches Kotlin CryptoManager.normalizeMnemonic() behavior.
 */
export function normalizeMnemonic(mnemonic: string): string[] {
  return mnemonic
    .trim()
    .toLowerCase()
    .split(/\s+/)
    .filter(w => w.length > 0);
}

/**
 * Generates a new 12-word BIP39 mnemonic using cryptographically secure entropy.
 * Uses react-native-get-random-values polyfill for secure randomness on mobile.
 *
 * @returns space-separated string of 12 BIP39 English words
 */
export function generateMnemonic(): string {
  // 128 bits = 12 words
  return bip39.generateMnemonic(128);
}

/**
 * Validates a BIP39 mnemonic string.
 *
 * Checks:
 * 1. Word count must be exactly 12
 * 2. All words must be in BIP39 English wordlist
 * 3. BIP39 checksum must be valid
 *
 * @param mnemonic - raw mnemonic string (may have extra spaces, uppercase)
 * @throws Error with descriptive message on failure
 * @returns true if valid
 */
export function validateMnemonic(mnemonic: string): boolean {
  const words = normalizeMnemonic(mnemonic);

  if (words.length !== EXPECTED_WORD_COUNT) {
    throw new Error(
      `Invalid word count: expected ${EXPECTED_WORD_COUNT}, got ${words.length}`,
    );
  }

  const wordlist = bip39.wordlists.english;
  for (const word of words) {
    if (!wordlist.includes(word)) {
      throw new Error(`Word '${word}' not in BIP39 wordlist`);
    }
  }

  const normalized = words.join(' ');
  if (!bip39.validateMnemonic(normalized)) {
    throw new Error('Invalid mnemonic checksum');
  }

  return true;
}

/**
 * Converts a mnemonic to BIP39 seed bytes.
 * passphrase is empty string (matching Kotlin MnemonicCode.toSeed(words, ""))
 */
export async function mnemonicToSeed(mnemonic: string): Promise<Buffer> {
  const words = normalizeMnemonic(mnemonic);
  return bip39.mnemonicToSeed(words.join(' '), '');
}
