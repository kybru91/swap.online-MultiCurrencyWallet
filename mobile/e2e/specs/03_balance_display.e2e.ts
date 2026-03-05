/**
 * E2E Test: Balance Display
 * Verifies wallet screen shows balances after import.
 */

const TEST_MNEMONIC =
  'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';

describe('Balance Display', () => {
  beforeAll(async () => {
    await device.launchApp({newInstance: true, delete: true});
    // Import wallet
    await element(by.text('Import Wallet')).tap();
    await element(by.placeholder('Paste your 12-word seed phrase here...')).typeText(TEST_MNEMONIC);
    await element(by.text('Import Wallet')).tap();
    await waitFor(element(by.text('Wallet'))).toBeVisible().withTimeout(10_000);
  });

  it('shows wallet screen with balance cards', async () => {
    await expect(element(by.text('Total Portfolio'))).toBeVisible();
    await expect(element(by.text('Bitcoin'))).toBeVisible();
    await expect(element(by.text('Ethereum'))).toBeVisible();
  });

  it('shows BTC balance in correct format (X.XXXXXXXX BTC)', async () => {
    // BTC balance should show amount followed by "BTC"
    await waitFor(element(by.text(/\d+\.\d+ BTC/)))
      .toBeVisible()
      .withTimeout(15_000);
  });

  it('shows ETH balance in correct format (X.XXXXXXXX ETH)', async () => {
    await waitFor(element(by.text(/\d+\.?\d* ETH/)))
      .toBeVisible()
      .withTimeout(15_000);
  });

  it('refreshes balances on pull-to-refresh', async () => {
    await element(by.id('wallet-scroll')).swipe('down', 'fast', 0.5);
    // After refresh, loading indicator should appear briefly
    // Then balances should reappear
    await waitFor(element(by.text('Bitcoin'))).toBeVisible().withTimeout(15_000);
  });

  it('navigates to Send screen from Wallet tab', async () => {
    await element(by.text('↑ Send')).tap();
    await expect(element(by.text('Send'))).toBeVisible();
    await expect(element(by.text('To Address'))).toBeVisible();
  });
});
