import { useState, useRef, useEffect } from 'react'
import CSSModules from 'react-css-modules'
import { FormattedMessage } from 'react-intl'
import config from 'helpers/externalConfig'
import styles from './NetworkFilter.scss'

// Human-readable names for networks
const NETWORK_NAMES: Record<string, string> = {
  BTC: 'Bitcoin',
  ETH: 'Ethereum',
  BNB: 'BSC',
  MATIC: 'Polygon',
  ARBETH: 'Arbitrum',
  AURETH: 'Aurora',
  XDAI: 'Gnosis',
  FTM: 'Fantom',
  AVAX: 'Avalanche',
  MOVR: 'Moonriver',
  ONE: 'Harmony',
  AME: 'AME',
  GHOST: 'Ghost',
  NEXT: 'Next',
}

type NetworkFilterProps = {
  networks: string[]
  selectedNetwork: string | null
  onSelect: (network: string | null) => void
}

function NetworkFilter({ networks, selectedNetwork, onSelect }: NetworkFilterProps) {
  const [isOpen, setIsOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const getNetworkName = (key: string) => {
    // Check evmNetworks config for chainName
    const evmInfo = config?.evmNetworks?.[key]
    if (evmInfo?.chainName) return evmInfo.chainName
    return NETWORK_NAMES[key] || key
  }

  const selectedLabel = selectedNetwork ? getNetworkName(selectedNetwork) : null

  return (
    <div styleName="networkFilter" ref={ref}>
      <button
        styleName={`filterButton ${isOpen ? 'open' : ''}`}
        onClick={() => setIsOpen(!isOpen)}
        type="button"
      >
        {selectedNetwork ? (
          <span styleName="selectedLabel">
            <span styleName="networkDot" data-network={selectedNetwork.toLowerCase()} />
            {selectedLabel}
          </span>
        ) : (
          <FormattedMessage id="NetworkFilter_AllNetworks" defaultMessage="All networks" />
        )}
        <span styleName="arrow" />
      </button>

      {isOpen && (
        <div styleName="dropdown">
          <div
            styleName={`dropdownItem ${selectedNetwork === null ? 'active' : ''}`}
            onClick={() => {
              onSelect(null)
              setIsOpen(false)
            }}
          >
            <FormattedMessage id="NetworkFilter_AllNetworks" defaultMessage="All networks" />
          </div>
          {networks.map((net) => (
            <div
              key={net}
              styleName={`dropdownItem ${selectedNetwork === net ? 'active' : ''}`}
              onClick={() => {
                onSelect(net)
                setIsOpen(false)
              }}
            >
              <span styleName="networkDot" data-network={net.toLowerCase()} />
              {getNetworkName(net)}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default CSSModules(NetworkFilter, styles, { allowMultiple: true })
