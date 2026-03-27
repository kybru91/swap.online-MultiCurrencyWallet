// Mock for @wagmi/core, @reown/appkit and related ESM-only packages
// These packages distribute ESM code that jest cannot process without full config.
// BTC/coin tests don't need real wagmi functionality.
module.exports = new Proxy(
  {},
  {
    get(_, prop) {
      if (prop === '__esModule') return true
      if (prop === 'default') return {}
      return jest.fn ? jest.fn() : () => {}
    },
  }
)
