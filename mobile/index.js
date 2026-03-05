/**
 * MCW Mobile — React Native Entry Point
 *
 * CRITICAL: polyfill import order matters!
 * 1. react-native-get-random-values patches globalThis.crypto.getRandomValues
 * 2. react-native-quick-crypto provides Buffer + full crypto API (JSI-native)
 * These MUST be imported before any crypto libraries (bip39, ethers, etc.)
 */
import 'react-native-get-random-values';
import 'react-native-quick-crypto';

// Install global crash/error handlers BEFORE anything else
// Catches: unhandled promise rejections, uncaught JS exceptions
import { setupGlobalErrorHandlers } from './src/utils/crashLogger';
setupGlobalErrorHandlers();

import { AppRegistry } from 'react-native';
import App from './App';
import { name as appName } from './app.json';

AppRegistry.registerComponent(appName, () => App);
