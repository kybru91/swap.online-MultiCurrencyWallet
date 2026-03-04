/**
 * WalletChip — Safe.global-style wallet button in the header.
 *
 * Not connected: [coin-icon  Connect Wallet ▾]  → click opens AppKit modal
 * Connected:     [coin-icon  ChainName · 0x12...3456 ▾]  → click opens chain dropdown
 *   Dropdown lists all available chains; click switches network via wagmi switchChain.
 *   Clicking the address portion opens AppKit Account view.
 */

import React, { useState, useRef, useEffect } from 'react'
import { useAppKitAccount, useAppKitNetwork, useAppKit } from '@reown/appkit/react'
import { switchChain } from '@wagmi/core'
import cssModules from 'react-css-modules'
import Coin from 'components/Coin/Coin'
import Address from 'components/ui/Address/Address'
import { AddressFormat } from 'domain/address'
import { FormattedMessage } from 'react-intl'
import externalConfig from 'helpers/externalConfig'
import { wagmiConfig } from 'lib/appkit'
import styles from './index.scss'

const CHAINS = Object.values(externalConfig.evmNetworks) as Array<{
  currency: string
  chainName: string
  networkVersion: number
}>

function WalletChip() {
  const { address, isConnected } = useAppKitAccount()
  const { caipNetwork } = useAppKitNetwork()
  const { open } = useAppKit()
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const wrapperRef = useRef<HTMLDivElement>(null)

  const coinTicker = caipNetwork?.nativeCurrency?.symbol?.toLowerCase() ?? 'eth'
  const chainName = caipNetwork?.name ?? 'Ethereum'

  useEffect(() => {
    if (!dropdownOpen) return
    const handler = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [dropdownOpen])

  const handleChipClick = () => {
    if (isConnected) {
      setDropdownOpen((prev) => !prev)
    } else {
      open()
    }
  }

  const handleAddressClick = (e: React.MouseEvent) => {
    e.stopPropagation()
    setDropdownOpen(false)
    open()
  }

  const handleSwitchChain = async (e: React.MouseEvent, chainId: number) => {
    e.stopPropagation()
    setDropdownOpen(false)
    try {
      await switchChain(wagmiConfig, { chainId })
    } catch (err) {
      console.error('[WalletChip] switchChain failed', err)
    }
  }

  return (
    <div styleName="wrapper" ref={wrapperRef}>
      <div styleName="chip" onClick={handleChipClick} id="wallet-chip">
        <Coin size={18} name={coinTicker} />

        <span styleName="chainName">{chainName}</span>

        {isConnected && address ? (
          <>
            <span styleName="dot">·</span>
            <span styleName="address" onClick={handleAddressClick}>
              <Address address={address} format={AddressFormat.Short} />
            </span>
          </>
        ) : (
          <span styleName="connectText">
            <FormattedMessage id="Exchange_ConnectAddressOption" defaultMessage="Connect Wallet" />
          </span>
        )}

        <span styleName="caret">{dropdownOpen ? '▴' : '▾'}</span>
      </div>

      {dropdownOpen && isConnected && (
        <div styleName="dropdown">
          {CHAINS.map((chain) => (
            <div
              key={chain.networkVersion}
              styleName="dropdownItem"
              onClick={(e) => handleSwitchChain(e, chain.networkVersion)}
            >
              <Coin size={16} name={chain.currency.toLowerCase()} />
              <span>{chain.chainName}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default cssModules(WalletChip, styles, { allowMultiple: true })
