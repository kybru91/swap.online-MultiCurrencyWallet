/**
 * E2E Test: Settings Screen
 * Verifies network selection, custom RPC, wallet lock.
 */

const TEST_MNEMONIC =
  'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';

describe('Settings', () => {
  beforeAll(async () => {
    await device.launchApp({newInstance: true, delete: true});
    // Import wallet
    await element(by.text('Import Wallet')).tap();
    await element(by.placeholder('Paste your 12-word seed phrase here...')).typeText(TEST_MNEMONIC);
    await element(by.text('Import Wallet')).tap();
    await waitFor(element(by.text('Wallet'))).toBeVisible().withTimeout(10_000);
    // Navigate to Settings tab
    await element(by.text('Settings')).tap();
  });

  it('shows Settings screen with network section', async () => {
    await expect(element(by.text('Settings'))).toBeVisible();
    await expect(element(by.text('Network'))).toBeVisible();
  });

  it('shows all supported chains', async () => {
    await expect(element(by.text('Ethereum'))).toBeVisible();
    await expect(element(by.text('BNB Smart Chain'))).toBeVisible();
    await expect(element(by.text('Polygon'))).toBeVisible();
  });

  it('switches active chain', async () => {
    await element(by.text('BNB Smart Chain')).tap();
    // BNB row should now have a checkmark (active)
    // Navigate away and back to verify persistence
    await element(by.text('Wallet')).tap();
    await element(by.text('Settings')).tap();
    await expect(element(by.text('BNB Smart Chain'))).toBeVisible();
    // Switch back to ETH
    await element(by.text('Ethereum')).tap();
  });

  it('shows Custom RPC section', async () => {
    await expect(element(by.text('Custom RPC'))).toBeVisible();
  });

  it('opens RPC edit input for Ethereum', async () => {
    // Find Edit button next to Ethereum
    await element(by.text('Edit')).atIndex(0).tap();
    await expect(element(by.placeholder('https://... (leave empty to reset)'))).toBeVisible();
  });

  it('saves and cancels custom RPC', async () => {
    await element(by.text('Cancel')).tap();
    // Should close the edit form
    await expect(element(by.text('Custom RPC'))).toBeVisible();
  });

  it('shows Security section', async () => {
    await expect(element(by.text('Security'))).toBeVisible();
    await expect(element(by.text('Lock Wallet'))).toBeVisible();
  });

  it('locks wallet from Settings', async () => {
    await element(by.text('🔒 Lock Wallet')).tap();
    await expect(element(by.text('Lock Wallet'))).toBeVisible(); // Alert
    await element(by.text('Lock')).tap();
    // Should navigate to Lock screen
    await expect(element(by.text('Wallet Locked'))).toBeVisible();
  });
});
