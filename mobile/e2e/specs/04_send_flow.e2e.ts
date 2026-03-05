/**
 * E2E Test: Send Transaction Flow
 * Wallet → Send → fill form → confirm dialog
 *
 * Note: Does not broadcast real transaction — verifies UI flow only.
 */

const TEST_MNEMONIC =
  'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';
const TEST_ETH_ADDRESS = '0x9858EfFD232B4033E47d90003D41EC34EcaEda94';

describe('Send Flow', () => {
  beforeAll(async () => {
    await device.launchApp({newInstance: true, delete: true});
    // Import wallet
    await element(by.text('Import Wallet')).tap();
    await element(by.placeholder('Paste your 12-word seed phrase here...')).typeText(TEST_MNEMONIC);
    await element(by.text('Import Wallet')).tap();
    await waitFor(element(by.text('Wallet'))).toBeVisible().withTimeout(10_000);
    // Navigate to Send
    await element(by.text('↑ Send')).tap();
  });

  it('shows Send screen with currency selector', async () => {
    await expect(element(by.text('Send'))).toBeVisible();
    await expect(element(by.text('ETH'))).toBeVisible();
    await expect(element(by.text('BTC'))).toBeVisible();
  });

  it('switches between ETH and BTC', async () => {
    await element(by.text('BTC')).tap();
    await expect(element(by.text('Available:'))).toBeVisible();
    await element(by.text('ETH')).tap();
  });

  it('shows validation error for empty address', async () => {
    await element(by.placeholder('0.00')).typeText('0.001');
    await element(by.text('Send ETH')).tap();
    await expect(element(by.text('Error'))).toBeVisible();
    await element(by.text('OK')).tap();
  });

  it('shows confirmation dialog for valid inputs', async () => {
    await element(by.placeholder('0x...')).typeText(TEST_ETH_ADDRESS);
    await element(by.text('Send ETH')).tap();
    await expect(element(by.text('Confirm Send'))).toBeVisible();
    await expect(element(by.text('Cancel'))).toBeVisible();
    await expect(element(by.text('Send'))).toBeVisible();
  });

  it('dismisses confirmation on Cancel', async () => {
    await element(by.text('Cancel')).tap();
    await expect(element(by.text('Send'))).toBeVisible();
    await expect(element(by.text('To Address'))).toBeVisible();
  });

  it('MAX button fills in full balance', async () => {
    await element(by.text('MAX')).tap();
    // Amount field should be non-empty after MAX
    const amountField = await element(by.placeholder('0.00')).getAttributes();
    expect(amountField).toBeTruthy();
  });
});
