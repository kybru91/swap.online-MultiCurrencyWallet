/**
 * WalletChip — Safe.global-style wallet button in the header.
 *
 * Shows: [coin-icon  ChainName · 0x12...3456 ▾]
 * Click: opens Reown AppKit modal (connect / account / network switch)
 *
 * Uses AppKit hooks — no Redux metamaskData needed.
 */

import React from 'react'
import { useAppKitAccount, useAppKitNetwork, useAppKit } from '@reown/appkit/react'
import cssModules from 'react-css-modules'
import Coin from 'components/Coin/Coin'
import Address from 'components/ui/Address/Address'
import { AddressFormat } from 'domain/address'
import { FormattedMessage } from 'react-intl'
import styles from './index.scss'

function WalletChip() {
  const { address, isConnected } = useAppKitAccount()
  const { caipNetwork } = useAppKitNetwork()
  const { open } = useAppKit()

  // Derive chain name / coin ticker from AppKit network
  const chainName = caipNetwork?.name ?? 'Ethereum'
  // First word of chainName is typically the coin ticker (e.g. "Binance" → BNB handled below)
  const coinTicker = caipNetwork?.nativeCurrency?.symbol?.toLowerCase() ?? 'eth'

  const handleClick = () => {
    open()
  }

  return (
    <div styleName="chip" onClick={handleClick} id="wallet-chip">
      <Coin size={18} name={coinTicker} />

      <span styleName="chainName">{chainName}</span>

      {isConnected && address ? (
        <>
          <span styleName="dot">·</span>
          <span styleName="address">
            <Address address={address} format={AddressFormat.Short} />
          </span>
        </>
      ) : (
        <span styleName="connectText">
          <FormattedMessage id="Exchange_ConnectAddressOption" defaultMessage="Connect Wallet" />
        </span>
      )}

      <span styleName="caret">▾</span>
    </div>
  )
}

export default cssModules(WalletChip, styles, { allowMultiple: true })
