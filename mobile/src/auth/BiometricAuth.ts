import * as Keychain from 'react-native-keychain';

export type BiometricAvailability =
  | 'available'
  | 'not_enrolled'
  | 'not_supported'
  | 'unavailable';

/**
 * Checks whether biometric authentication is available on this device.
 * Uses react-native-keychain's getSupportedBiometryType().
 *
 * Matches Kotlin BiometricChecker.checkAvailability() behavior.
 */
export async function checkBiometricAvailability(): Promise<BiometricAvailability> {
  try {
    const biometryType = await Keychain.getSupportedBiometryType();
    if (biometryType === null) {
      // Supported but not enrolled, OR not supported at all
      return 'not_enrolled';
    }
    return 'available';
  } catch {
    return 'unavailable';
  }
}

/**
 * Prompts biometric authentication (Face ID / Touch ID / Fingerprint).
 * Uses react-native-keychain to retrieve a secret via biometric prompt,
 * confirming the user is authenticated.
 *
 * @returns true if biometric auth succeeded, false if cancelled/failed
 */
export async function authenticateWithBiometric(
  promptMessage: string = 'Verify your identity',
): Promise<boolean> {
  try {
    const result = await Keychain.getGenericPassword({
      service: 'mcw.mnemonic',
      authenticationPrompt: {
        title: promptMessage,
        subtitle: 'Use your biometric to unlock MCW Wallet',
        cancel: 'Cancel',
      },
      accessControl: Keychain.ACCESS_CONTROL.BIOMETRY_ANY_OR_DEVICE_PASSCODE,
    });
    return result !== false;
  } catch {
    return false;
  }
}
