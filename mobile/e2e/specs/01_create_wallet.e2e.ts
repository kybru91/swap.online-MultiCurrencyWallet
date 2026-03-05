/**
 * E2E Test: Create Wallet Flow
 * Welcome → Create Wallet → Confirm Mnemonic → Main Wallet
 */
describe('Create Wallet', () => {
  beforeAll(async () => {
    await device.launchApp({newInstance: true, delete: true});
  });

  it('shows Welcome screen on first launch', async () => {
    await expect(element(by.text('MCW Wallet'))).toBeVisible();
    await expect(element(by.text('Create New Wallet'))).toBeVisible();
    await expect(element(by.text('Import Wallet'))).toBeVisible();
  });

  it('navigates to Create Wallet screen', async () => {
    await element(by.text('Create New Wallet')).tap();
    await expect(element(by.text('Your Seed Phrase'))).toBeVisible();
  });

  it('displays 12 seed words', async () => {
    // Word boxes 1-12 should all be visible
    await expect(element(by.text('1'))).toBeVisible();
    await expect(element(by.text('12'))).toBeVisible();
  });

  it('requires checkbox confirmation before continuing', async () => {
    // Try to proceed without checking the box
    await element(by.text('Continue to Verification')).tap();
    await expect(element(by.text('Please confirm'))).toBeVisible();
    await element(by.text('OK')).tap();
  });

  it('proceeds to confirmation after checking backup box', async () => {
    // Check the backup confirmation checkbox
    await element(by.text('I have written down my seed phrase in a safe place')).tap();
    await element(by.text('Continue to Verification')).tap();
    await expect(element(by.text('Verify Backup'))).toBeVisible();
  });

  it('shows error for wrong verification words', async () => {
    // Get the input fields and fill with wrong words
    const inputs = await element(by.type('RCTTextInput')).getAttributes();
    // Fill first input with a wrong word
    await element(by.type('RCTTextInput')).atIndex(0).typeText('wrongword');
    await element(by.text('Verify & Create Wallet')).tap();
    await expect(element(by.text('Incorrect'))).toBeVisible();
    await element(by.text('OK')).tap();
  });
});
