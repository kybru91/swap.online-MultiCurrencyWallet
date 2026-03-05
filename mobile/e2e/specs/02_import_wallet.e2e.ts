/**
 * E2E Test: Import Wallet Flow
 * Welcome → Import Wallet → paste mnemonic → Main Wallet
 *
 * Uses the standard BIP39 test vector mnemonic (all "abandon"):
 * ETH address: 0x9858EfFD232B4033E47d90003D41EC34EcaEda94
 * BTC address: 1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA
 */

const TEST_MNEMONIC =
  'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';

describe('Import Wallet', () => {
  beforeAll(async () => {
    await device.launchApp({newInstance: true, delete: true});
  });

  it('shows Welcome screen', async () => {
    await expect(element(by.text('MCW Wallet'))).toBeVisible();
  });

  it('navigates to Import Wallet screen', async () => {
    await element(by.text('Import Wallet')).tap();
    await expect(element(by.text('Import Wallet'))).toBeVisible();
    await expect(element(by.text('Paste All'))).toBeVisible();
  });

  it('accepts a valid 12-word mnemonic by paste', async () => {
    // Default mode is "Paste All"
    await element(by.placeholder('Paste your 12-word seed phrase here...')).typeText(TEST_MNEMONIC);
    await element(by.text('Import Wallet')).tap();
  });

  it('navigates to main wallet after successful import', async () => {
    await waitFor(element(by.text('Wallet'))).toBeVisible().withTimeout(10_000);
    await expect(element(by.text('Wallet'))).toBeVisible();
  });

  it('shows ETH balance card after import', async () => {
    await waitFor(element(by.text('Ethereum'))).toBeVisible().withTimeout(15_000);
    await expect(element(by.text('Ethereum'))).toBeVisible();
  });

  it('shows BTC balance card after import', async () => {
    await expect(element(by.text('Bitcoin'))).toBeVisible();
  });

  it('rejects invalid mnemonic with error message', async () => {
    // Go back to import screen with invalid mnemonic
    await device.launchApp({newInstance: true, delete: true});
    await element(by.text('Import Wallet')).tap();
    await element(by.placeholder('Paste your 12-word seed phrase here...')).typeText(
      'invalid mnemonic phrase that is not valid bip39',
    );
    await element(by.text('Import Wallet')).tap();
    await expect(element(by.text('Invalid Seed Phrase'))).toBeVisible();
    await element(by.text('OK')).tap();
  });
});
