import * as bip32 from 'bip32';
import {ethers} from 'ethers';
import {mnemonicToSeed, normalizeMnemonic} from './mnemonic';

// BIP44 path constants — matching Kotlin CryptoManager
const PURPOSE = 44;
const BTC_COIN_TYPE = 0;
const ETH_COIN_TYPE = 60;
const ACCOUNT = 0;
const CHANGE = 0;

export interface BtcKeyResult {
  privateKeyWIF: string;
  address: string;
}

export interface EthKeyResult {
  privateKeyHex: string; // 0x-prefixed hex
  address: string;       // EIP-55 checksummed
}

export interface WalletKeys {
  mnemonic: string[];
  btcPrivateKeyWIF: string;
  btcAddress: string;
  ethPrivateKeyHex: string;
  ethAddress: string;
}

/**
 * Derives BTC key at m/44'/0'/0'/0/{addressIndex}.
 * Produces P2PKH address (starts with '1' on mainnet) and WIF private key.
 * Matches Kotlin CryptoManager.deriveBtcKeyFromSeed() behavior.
 *
 * @param mnemonic - BIP39 mnemonic string
 * @param addressIndex - BIP44 address index (default 0)
 */
export async function deriveBtcKey(
  mnemonic: string,
  addressIndex: number = 0,
): Promise<BtcKeyResult> {
  const seed = await mnemonicToSeed(mnemonic);
  const root = bip32.BIP32Factory(require('tiny-secp256k1')).fromSeed(seed);

  const path = `m/${PURPOSE}'/${BTC_COIN_TYPE}'/${ACCOUNT}'/${CHANGE}/${addressIndex}`;
  const child = root.derivePath(path);

  if (!child.privateKey) {
    throw new Error('BTC key derivation failed: no private key');
  }

  // WIF encoding for mainnet (prefix 0x80)
  const wif = privateKeyToWIF(child.privateKey);

  // P2PKH address from compressed public key
  const address = publicKeyToP2PKHAddress(child.publicKey);

  return {privateKeyWIF: wif, address};
}

/**
 * Derives ETH key at m/44'/60'/0'/0/{addressIndex}.
 * Produces EIP-55 checksummed address and 0x-prefixed hex private key.
 * Matches Kotlin CryptoManager.deriveEthKeyFromSeed() behavior.
 *
 * Same key is used across all EVM chains (ETH, BSC, Polygon, etc.)
 *
 * @param mnemonic - BIP39 mnemonic string
 * @param addressIndex - BIP44 address index (default 0)
 */
export async function deriveEthKey(
  mnemonic: string,
  addressIndex: number = 0,
): Promise<EthKeyResult> {
  const words = normalizeMnemonic(mnemonic);
  const hdNode = ethers.utils.HDNode.fromMnemonic(words.join(' '));

  const path = `m/${PURPOSE}'/${ETH_COIN_TYPE}'/${ACCOUNT}'/${CHANGE}/${addressIndex}`;
  const derived = hdNode.derivePath(path);

  return {
    privateKeyHex: derived.privateKey, // already 0x-prefixed by ethers
    address: derived.address,           // already EIP-55 checksummed
  };
}

/**
 * Derives all wallet keys from a BIP39 mnemonic.
 * Computes BTC seed once and reuses for ETH derivation.
 *
 * @param mnemonic - BIP39 mnemonic string
 * @returns WalletKeys with BTC and ETH keys
 */
export async function deriveKeys(mnemonic: string): Promise<WalletKeys> {
  const words = normalizeMnemonic(mnemonic);

  // Derive both keys in parallel for performance
  const [btcKey, ethKey] = await Promise.all([
    deriveBtcKey(mnemonic, 0),
    deriveEthKey(mnemonic, 0),
  ]);

  return {
    mnemonic: words,
    btcPrivateKeyWIF: btcKey.privateKeyWIF,
    btcAddress: btcKey.address,
    ethPrivateKeyHex: ethKey.privateKeyHex,
    ethAddress: ethKey.address,
  };
}

// --- Bitcoin encoding helpers ---

/**
 * Encodes a private key as WIF (Wallet Import Format) for BTC mainnet.
 * Mainnet prefix: 0x80, compressed: append 0x01
 */
function privateKeyToWIF(privateKey: Buffer): string {
  // WIF = Base58Check(0x80 || private_key || 0x01)
  const bs58check = require('bs58check');
  const payload = Buffer.alloc(34);
  payload[0] = 0x80; // mainnet prefix
  privateKey.copy(payload, 1);
  payload[33] = 0x01; // compressed flag
  return bs58check.encode(payload);
}

/**
 * Derives P2PKH Bitcoin address from compressed public key.
 * Address = Base58Check(0x00 || RIPEMD160(SHA256(pubkey)))
 */
function publicKeyToP2PKHAddress(publicKey: Buffer): string {
  const crypto = require('crypto');
  const bs58check = require('bs58check');

  // SHA256 then RIPEMD160
  const sha256 = crypto.createHash('sha256').update(publicKey).digest();
  const ripemd160 = crypto.createHash('ripemd160').update(sha256).digest();

  // P2PKH: version byte 0x00 + hash
  const payload = Buffer.alloc(21);
  payload[0] = 0x00; // mainnet P2PKH version
  ripemd160.copy(payload, 1);

  return bs58check.encode(payload);
}
