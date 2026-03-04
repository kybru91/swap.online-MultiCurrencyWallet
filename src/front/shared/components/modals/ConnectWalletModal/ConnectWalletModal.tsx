/**
 * ConnectWalletModal — replaced by Reown AppKit built-in modal.
 *
 * Opening this modal now just triggers AppKit's own UI which handles:
 * - Browser wallets (MetaMask, Brave, etc.)
 * - WalletConnect QR
 * - Coinbase Wallet
 * - Network auto-detection (no manual network selection step)
 *
 * The component renders null because AppKit renders its modal as a web component
 * outside the React tree. It resolves via the watchAccount listener in metamask.ts.
 */

import React, { useEffect } from 'react'
import { modal } from 'lib/appkit'

// Accept any props (e.g. noCloseButton) so callers don't need changes
function ConnectWalletModal(_props: Record<string, any> = {}) {
  useEffect(() => {
    modal.open()
  }, [])

  return null
}

export default ConnectWalletModal
