import React from 'react';
import { StatusBar, useColorScheme } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import RootNavigator from '@navigation/RootNavigator';
import { ErrorBoundary } from './src/components/ErrorBoundary';

/**
 * Root application component.
 * Wraps app in required providers:
 * - ErrorBoundary: catches JS render crashes, shows debug report screen
 * - GestureHandlerRootView: required by react-navigation v6
 * - SafeAreaProvider: safe area insets for notches/home indicator
 * - NavigationContainer: React Navigation context
 */
function App(): React.JSX.Element {
  const isDarkMode = useColorScheme() === 'dark';

  return (
    <ErrorBoundary>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <SafeAreaProvider>
          <NavigationContainer>
            <StatusBar
              barStyle={isDarkMode ? 'light-content' : 'dark-content'}
              backgroundColor="transparent"
              translucent
            />
            <RootNavigator />
          </NavigationContainer>
        </SafeAreaProvider>
      </GestureHandlerRootView>
    </ErrorBoundary>
  );
}

export default App;
