package com.mcw.wallet.ui.settings

/**
 * Supported EVM networks for the network selector.
 *
 * Tech-spec Decision 9: wallet_addEthereumChain restricted to chain IDs 1, 56, 137.
 * Matches web config evmNetworks.js (ETH, BNB, MATIC) for the MVP scope.
 */
enum class SupportedNetwork(
  val chainId: Long,
  val displayName: String,
  val currencySymbol: String,
) {
  ETH_MAINNET(1L, "Ethereum Mainnet", "ETH"),
  BSC(56L, "BNB Smart Chain", "BNB"),
  POLYGON(137L, "Polygon", "MATIC");

  companion object {
    /**
     * Look up a supported network by chain ID.
     * @return the network, or null if chain ID is not supported
     */
    fun fromChainId(chainId: Long): SupportedNetwork? {
      return entries.find { it.chainId == chainId }
    }
  }
}
