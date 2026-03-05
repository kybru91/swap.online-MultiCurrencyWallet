module.exports = {
  presets: ['module:@react-native/babel-preset'],
  plugins: [
    ['module-resolver', {
      root: ['./src'],
      extensions: ['.ios.js', '.android.js', '.js', '.ts', '.tsx', '.json'],
      alias: {
        '@crypto': './src/crypto',
        '@storage': './src/storage',
        '@auth': './src/auth',
        '@network': './src/network',
        '@state': './src/state',
        '@navigation': './src/navigation',
        '@screens': './src/screens',
        '@components': './src/components',
      },
    }],
    'nativewind/babel',
  ],
};
