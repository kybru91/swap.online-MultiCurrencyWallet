import React, {useEffect, useState} from 'react';
import {createStackNavigator} from '@react-navigation/stack';
import {hasWallet} from '@storage/SecureStorage';
import {checkPersistedLockoutState} from '@auth/AuthManager';
import MainNavigator from './MainNavigator';

// Onboarding screens
import WelcomeScreen from '@screens/onboarding/WelcomeScreen';
import CreateWalletScreen from '@screens/onboarding/CreateWalletScreen';
import ConfirmMnemonicScreen from '@screens/onboarding/ConfirmMnemonicScreen';
import ImportWalletScreen from '@screens/onboarding/ImportWalletScreen';

// Auth screen
import LockScreen from '@screens/LockScreen';

export type RootStackParamList = {
  Welcome: undefined;
  CreateWallet: undefined;
  ConfirmMnemonic: {mnemonic: string[]};
  ImportWallet: undefined;
  Main: undefined;
  Lock: undefined;
};

const Stack = createStackNavigator<RootStackParamList>();

type AppState = 'loading' | 'onboarding' | 'locked' | 'unlocked';

/**
 * Root navigator: determines initial route based on wallet state.
 * - No wallet → Onboarding flow (Welcome → Create/Import)
 * - Wallet exists → Lock screen (user must authenticate)
 */
export default function RootNavigator() {
  const [appState, setAppState] = useState<AppState>('loading');

  useEffect(() => {
    async function init() {
      const walletExists = await hasWallet();
      if (!walletExists) {
        setAppState('onboarding');
        return;
      }
      // Wallet exists: check lockout state, show lock screen
      await checkPersistedLockoutState();
      setAppState('locked');
    }
    init();
  }, []);

  if (appState === 'loading') {
    return null; // Splash screen handled by native layer
  }

  return (
    <Stack.Navigator screenOptions={{headerShown: false}}>
      {appState === 'onboarding' ? (
        <>
          <Stack.Screen name="Welcome" component={WelcomeScreen} />
          <Stack.Screen name="CreateWallet" component={CreateWalletScreen} />
          <Stack.Screen name="ConfirmMnemonic" component={ConfirmMnemonicScreen} />
          <Stack.Screen name="ImportWallet" component={ImportWalletScreen} />
        </>
      ) : (
        <>
          <Stack.Screen name="Lock" component={LockScreen} />
          <Stack.Screen name="Main" component={MainNavigator} />
        </>
      )}
    </Stack.Navigator>
  );
}
