/**
 * Determines the blockchain network for a wallet item.
 *
 * Native coins: currency itself is the network (ETH, BNB, MATIC, etc.)
 * Tokens: extracted from tokenKey format {BLOCKCHAIN}TOKEN (e.g. {ETH}USDT → ETH)
 * BTC variants: BTC (Multisig), BTC (SMS-protected), etc. → BTC
 */
export const getWalletNetwork = (wallet: IUniversalObj): string => {
  const { currency, isToken, tokenKey } = wallet

  // Tokens: extract blockchain from {BLOCKCHAIN}TOKEN format
  if (isToken && tokenKey) {
    const match = tokenKey.match(/^\{([^}]+)\}/)
    if (match) return match[1].toUpperCase()
  }

  // BTC multisig variants
  const upper = (currency || '').toUpperCase()
  if (upper.startsWith('BTC')) return 'BTC'

  return upper
}

/**
 * Returns sorted unique network identifiers from a list of wallets.
 * Order: BTC first, then EVM chains alphabetically.
 */
export const getAvailableNetworks = (wallets: IUniversalObj[]): string[] => {
  const set = new Set<string>()
  wallets.forEach((w) => {
    const net = getWalletNetwork(w)
    if (net) set.add(net)
  })

  const networks = Array.from(set)
  // BTC first, then alphabetical
  networks.sort((a, b) => {
    if (a === 'BTC') return -1
    if (b === 'BTC') return 1
    return a.localeCompare(b)
  })

  return networks
}

/**
 * Filters wallets by selected network.
 * If network is null — returns all wallets (All Networks mode).
 */
export const filterWalletsByNetwork = (
  wallets: IUniversalObj[],
  network: string | null
): IUniversalObj[] => {
  if (!network) return wallets
  return wallets.filter((w) => getWalletNetwork(w) === network)
}
