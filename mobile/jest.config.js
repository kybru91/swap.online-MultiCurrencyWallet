module.exports = {
  preset: 'react-native',
  setupFilesAfterFramework: ['@testing-library/jest-native/extend-expect'],
  moduleNameMapper: {
    '^@crypto/(.*)$': '<rootDir>/src/crypto/$1',
    '^@storage/(.*)$': '<rootDir>/src/storage/$1',
    '^@auth/(.*)$': '<rootDir>/src/auth/$1',
    '^@network/(.*)$': '<rootDir>/src/network/$1',
    '^@state/(.*)$': '<rootDir>/src/state/$1',
    '^@navigation/(.*)$': '<rootDir>/src/navigation/$1',
    '^@screens/(.*)$': '<rootDir>/src/screens/$1',
    '^@components/(.*)$': '<rootDir>/src/components/$1',
  },
  transformIgnorePatterns: [
    'node_modules/(?!(react-native|@react-native|react-native-get-random-values|react-native-quick-crypto|react-native-keychain|@react-navigation|react-native-screens|react-native-safe-area-context|react-native-gesture-handler|react-native-webview|nativewind)/)',
  ],
  testEnvironment: 'node',
  collectCoverageFrom: [
    'src/**/*.{ts,tsx}',
    '!src/**/*.d.ts',
  ],
};
