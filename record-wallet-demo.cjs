#!/usr/bin/env node
/**
 * record-wallet-demo.cjs
 *
 * YouTube demo: MultiCurrency Crypto Wallet — Product Landing + Live Preview
 * Shows: Landing page features, reviews, live wallet demo
 *
 * Run: node record-wallet-demo.cjs
 * Result: ./demo-video/wallet-demo.mp4
 */

const { recordDemo } = require('/root/tools/e2e-video-recorder/recorder-base.cjs')

const LANDING_URL = 'https://onout.org/wallet/'
const WALLET_URL  = 'https://appsource.github.io/wallet/'

const SCENES = [

  // -- Scene 1: Landing hero --
  {
    name:     'Landing — hero',
    goto:     LANDING_URL,
    waitMs:   3000,
    subtitle: 'Multicurrency Crypto Wallet — white-label solution',
    duration: 6000,
    actions: [
      { type: 'mouse', points: [[640, 300], [620, 320], [660, 310], [640, 290]] },
      { type: 'sleep', ms: 3000 },
    ],
  },

  // -- Scene 2: Features — multicurrency storage --
  {
    name:     'Landing — features',
    goto:     null,
    waitMs:   0,
    subtitle: 'Multicurrency storage with P2P atomic swap exchange',
    duration: 7000,
    actions: [
      { type: 'scroll', y: 700, durationMs: 2000 },
      { type: 'sleep', ms: 2000 },
      { type: 'mouse', points: [[640, 400], [500, 380], [780, 390]] },
      { type: 'sleep', ms: 2000 },
    ],
  },

  // -- Scene 3: Features — custom tokens & commissions --
  {
    name:     'Landing — tokens and commissions',
    goto:     null,
    waitMs:   0,
    subtitle: 'Custom token lists and built-in commission system',
    duration: 7000,
    actions: [
      { type: 'scroll', y: 1500, durationMs: 2000 },
      { type: 'sleep', ms: 2000 },
      { type: 'mouse', points: [[640, 350], [500, 400], [780, 380]] },
      { type: 'sleep', ms: 2000 },
    ],
  },

  // -- Scene 4: Design customization & card processing --
  {
    name:     'Landing — design and cards',
    goto:     null,
    waitMs:   0,
    subtitle: 'Customizable design and Visa/Mastercard integration',
    duration: 6000,
    actions: [
      { type: 'scroll', y: 2400, durationMs: 2000 },
      { type: 'sleep', ms: 1500 },
      { type: 'mouse', points: [[640, 400], [600, 420], [680, 380]] },
      { type: 'sleep', ms: 1500 },
    ],
  },

  // -- Scene 5: Customer reviews --
  {
    name:     'Landing — reviews',
    goto:     null,
    waitMs:   0,
    subtitle: 'Trusted by customers worldwide',
    duration: 6000,
    actions: [
      { type: 'scroll', y: 3200, durationMs: 2000 },
      { type: 'sleep', ms: 1500 },
      { type: 'mouse', points: [[400, 400], [640, 380], [880, 400]] },
      { type: 'sleep', ms: 1500 },
    ],
  },

  // -- Scene 6: AI integration --
  {
    name:     'Landing — AI integration',
    goto:     null,
    waitMs:   0,
    subtitle: 'Works with Claude, ChatGPT and AI Agents',
    duration: 5000,
    actions: [
      { type: 'scroll', y: 5000, durationMs: 2000 },
      { type: 'sleep', ms: 2000 },
    ],
  },

  // -- Scene 7: Live wallet preview --
  {
    name:     'Wallet — live preview',
    goto:     WALLET_URL,
    waitMs:   5000,
    subtitle: 'Live wallet preview — ready to use',
    duration: 7000,
    actions: [
      // Skip the starter modal if present
      { type: 'sleep', ms: 2000 },
      { type: 'mouse', points: [[640, 400], [640, 350], [640, 450]] },
      { type: 'sleep', ms: 2000 },
    ],
  },

  // -- Scene 8: Wallet interface scroll --
  {
    name:     'Wallet — interface',
    goto:     null,
    waitMs:   0,
    subtitle: 'BTC, ETH, BNB, MATIC and hundreds of tokens',
    duration: 7000,
    actions: [
      { type: 'scroll', y: 400, durationMs: 1500 },
      { type: 'sleep', ms: 2000 },
      { type: 'scroll', y: 800, durationMs: 1500 },
      { type: 'sleep', ms: 2000 },
    ],
  },

  // -- Scene 9: Outro --
  {
    name:     'Outro — CTA',
    goto:     LANDING_URL,
    waitMs:   3000,
    subtitle: 'Get your wallet at onout.org/wallet — $999',
    duration: 5000,
    actions: [
      { type: 'mouse', points: [[640, 350], [620, 340], [660, 360], [640, 350]] },
      { type: 'sleep', ms: 3000 },
    ],
  },
]

recordDemo({
  name:    'wallet-demo',
  scenes:  SCENES,
  baseUrl: LANDING_URL,
  outDir:  './demo-video',
  lang:    'en',
  pageSetup: async (page) => {
    // Force English locale
    await page.evaluateOnNewDocument(() => {
      Object.defineProperty(navigator, 'language', { get: () => 'en-US' })
      Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] })
    })
  },
})
  .then(outPath => console.log('Done:', outPath))
  .catch(err => { console.error('FAIL:', err.message); process.exit(1) })
