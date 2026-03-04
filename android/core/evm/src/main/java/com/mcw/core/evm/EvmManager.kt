package com.mcw.core.evm

import com.mcw.core.network.api.CoinGeckoApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

/**
 * EVM operations: balance fetching, ERC20 token balances, gas estimation,
 * transaction building, signing, and broadcast.
 *
 * All public methods that interact with the network return null on failure
 * (offline mode) instead of throwing exceptions. This allows the UI layer
 * to display "N/A" when the network is unavailable.
 *
 * Gas estimation applies a 1.05x buffer for token transfers to account for
 * state changes that may increase actual gas usage, matching the web wallet's
 * behavior in ethLikeAction.ts.
 *
 * Fiat prices are fetched from CoinGecko free API and parsed via
 * [parseCoinGeckoPrices].
 */
class EvmManager @Inject constructor() {

  companion object {
    /** Wei per ETH (10^18) */
    private val WEI_PER_ETH = BigDecimal.TEN.pow(18)

    /** Gas buffer multiplier for token transfers (1.05x) */
    private val TOKEN_GAS_BUFFER = BigDecimal("1.05")

    /** EVM native transfer decimals */
    private const val ETH_DECIMALS = 18

    /**
     * Mapping from currency symbol to CoinGecko API coin ID.
     * Used to build the 'ids' query parameter for CoinGecko simple/price endpoint.
     */
    val COINGECKO_IDS: Map<String, String> = mapOf(
      "BTC" to "bitcoin",
      "ETH" to "ethereum",
      "BNB" to "binancecoin",
      "MATIC" to "matic-network",
    )

    /** Reverse mapping: CoinGecko ID -> currency symbol */
    private val COINGECKO_REVERSE: Map<String, String> =
      COINGECKO_IDS.entries.associate { (symbol, id) -> id to symbol }

    /**
     * Parses a hex-encoded wei balance from eth_getBalance into a BigDecimal
     * in native units (ETH/BNB/MATIC).
     *
     * @param weiHex hex-encoded balance string (e.g. "0xde0b6b3a7640000" for 1 ETH)
     * @return balance in native units with 18 decimal places
     */
    fun parseBalanceFromWei(weiHex: String): BigDecimal {
      val wei = Numeric.decodeQuantity(weiHex)
      return BigDecimal(wei).setScale(ETH_DECIMALS).divide(WEI_PER_ETH, ETH_DECIMALS, RoundingMode.UNNECESSARY)
    }

    /**
     * Parses a raw token balance (from balanceOf) into human-readable units
     * by dividing by 10^decimals.
     *
     * @param rawBalance the raw token balance as BigInteger
     * @param decimals the token's decimal places (e.g. 6 for USDT, 8 for WBTC, 18 for standard)
     * @return balance in human-readable units
     */
    fun parseTokenBalance(rawBalance: BigInteger, decimals: Int): BigDecimal {
      val divisor = BigDecimal.TEN.pow(decimals)
      return BigDecimal(rawBalance).setScale(decimals).divide(divisor, decimals, RoundingMode.UNNECESSARY)
    }

    /**
     * Parses a CoinGecko simple/price API response into a Map<Symbol, Price>.
     *
     * API response format:
     * ```json
     * {"bitcoin":{"usd":50000.0},"ethereum":{"usd":3000.0},...}
     * ```
     *
     * Only coins present in [COINGECKO_REVERSE] are mapped.
     * Missing coins result in absent map entries (callers check via get()).
     *
     * @param apiResponse the raw CoinGecko API response
     * @return Map of currency symbol to USD price as BigDecimal
     */
    fun parseCoinGeckoPrices(
      apiResponse: Map<String, Map<String, Double>>
    ): Map<String, BigDecimal> {
      val result = mutableMapOf<String, BigDecimal>()
      for ((coinId, prices) in apiResponse) {
        val symbol = COINGECKO_REVERSE[coinId] ?: continue
        val usdPrice = prices["usd"] ?: continue
        result[symbol] = BigDecimal.valueOf(usdPrice)
      }
      return result
    }

    /**
     * Applies a gas buffer for token transfers.
     *
     * Native transfers: no buffer (exact estimate).
     * Token transfers: 1.05x buffer (ceiling rounding).
     *
     * Matches web wallet's `multiplierForGasReserve = 1.05` in ethLikeAction.ts.
     *
     * @param estimate the eth_estimateGas result
     * @param isTokenTransfer whether this is an ERC20 token transfer
     * @return adjusted gas limit
     */
    fun applyGasBuffer(estimate: BigInteger, isTokenTransfer: Boolean): BigInteger {
      if (!isTokenTransfer) {
        return estimate
      }
      // 1.05x buffer, round up (ceiling) to ensure enough gas
      return BigDecimal(estimate)
        .multiply(TOKEN_GAS_BUFFER)
        .setScale(0, RoundingMode.CEILING)
        .toBigInteger()
    }

    /**
     * Calculates fiat value for a given balance and price.
     *
     * @param balance the crypto balance
     * @param price the fiat price per unit
     * @return fiat value with 2 decimal places (HALF_UP rounding)
     */
    fun calculateFiatValue(balance: BigDecimal, price: BigDecimal): BigDecimal {
      return balance.multiply(price).setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Builds a RawTransaction for signing.
     *
     * @param nonce the sender's transaction count
     * @param to the recipient address
     * @param value the transfer amount in wei (0 for ERC20 transfers)
     * @param gasPrice the gas price in wei
     * @param gasLimit the gas limit
     * @param data optional contract call data (hex-encoded)
     * @param chainId the EVM chain ID (used for EIP-155 signing)
     * @return a RawTransaction ready for signing
     */
    @Suppress("UNUSED_PARAMETER") // chainId kept in signature for API clarity; applied during signing
    fun buildTransaction(
      nonce: BigInteger,
      to: String,
      value: BigInteger,
      gasPrice: BigInteger,
      gasLimit: BigInteger,
      data: String?,
      chainId: Long,
    ): RawTransaction {
      return RawTransaction.createTransaction(
        nonce,
        gasPrice,
        gasLimit,
        to,
        value,
        data ?: "",
      )
    }

    /**
     * Signs a RawTransaction with a private key using EIP-155.
     *
     * @param tx the raw transaction to sign
     * @param privateKeyHex 0x-prefixed hex private key
     * @param chainId the EVM chain ID for EIP-155 replay protection
     * @return 0x-prefixed hex-encoded signed transaction, or null on error
     */
    fun signTransaction(
      tx: RawTransaction,
      privateKeyHex: String,
      chainId: Long,
    ): String? {
      return try {
        val credentials = Credentials.create(privateKeyHex)
        val signedBytes = TransactionEncoder.signMessage(tx, chainId, credentials)
        Numeric.toHexString(signedBytes)
      } catch (e: Exception) {
        // Signing failure (invalid key, etc.) — return null
        null
      }
    }
  }

  /**
   * Fetches the native balance (ETH/BNB/MATIC) for an address via eth_getBalance.
   *
   * @param address the EVM address to query
   * @param web3j the Web3j instance connected to the correct chain RPC
   * @return balance in native units (e.g. ETH), or null on error (offline mode)
   */
  suspend fun fetchBalance(
    address: String,
    web3j: Web3j,
  ): BigDecimal? {
    return try {
      withContext(Dispatchers.IO) {
        val response = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send()
        val wei = response.balance
        BigDecimal(wei).setScale(ETH_DECIMALS).divide(WEI_PER_ETH, ETH_DECIMALS, RoundingMode.UNNECESSARY)
      }
    } catch (e: Exception) {
      // Offline mode: return null, UI shows "N/A"
      null
    }
  }

  /**
   * Fetches an ERC20 token balance by calling the balanceOf(address) function
   * on the token contract.
   *
   * @param address the wallet address to query
   * @param contractAddress the ERC20 token contract address
   * @param decimals the token's decimal places
   * @param web3j the Web3j instance connected to the correct chain RPC
   * @return token balance in human-readable units, or null on error
   */
  suspend fun fetchTokenBalance(
    address: String,
    contractAddress: String,
    decimals: Int,
    web3j: Web3j,
  ): BigDecimal? {
    return try {
      withContext(Dispatchers.IO) {
        // Encode the balanceOf(address) function call
        val function = Function(
          "balanceOf",
          listOf(Address(address)),
          listOf(object : TypeReference<Uint256>() {}),
        )
        val encodedFunction = FunctionEncoder.encode(function)

        val ethCall = web3j.ethCall(
          Transaction.createEthCallTransaction(address, contractAddress, encodedFunction),
          DefaultBlockParameterName.LATEST,
        ).send()

        val result = FunctionReturnDecoder.decode(
          ethCall.value,
          function.outputParameters,
        )

        if (result.isEmpty()) {
          return@withContext null
        }

        val rawBalance = result[0].value as BigInteger
        parseTokenBalance(rawBalance, decimals)
      }
    } catch (e: Exception) {
      // Offline mode: return null, UI shows "N/A"
      null
    }
  }

  /**
   * Fetches fiat prices from CoinGecko API.
   *
   * @param coinGeckoApi the CoinGecko Retrofit API interface
   * @param symbols the currency symbols to fetch prices for (e.g. ["BTC", "ETH"])
   * @return Map of symbol to USD price, or null on error
   */
  suspend fun fetchFiatPrices(
    coinGeckoApi: CoinGeckoApi,
    symbols: List<String>,
  ): Map<String, BigDecimal>? {
    return try {
      withContext(Dispatchers.IO) {
        val ids = symbols.mapNotNull { COINGECKO_IDS[it] }.joinToString(",")
        if (ids.isEmpty()) return@withContext emptyMap()
        val response = coinGeckoApi.getSimplePrice(ids = ids)
        parseCoinGeckoPrices(response)
      }
    } catch (e: Exception) {
      // Offline mode: return null, UI shows "N/A"
      null
    }
  }

  /**
   * Estimates gas for a transaction (gas price + gas limit).
   *
   * Applies a 1.05x buffer for token transfers, no buffer for native transfers.
   *
   * @param from sender address
   * @param to recipient address (or contract address for token transfers)
   * @param value transfer value in wei
   * @param data optional hex-encoded contract call data
   * @param isTokenTransfer whether this is an ERC20 token transfer
   * @param web3j the Web3j instance connected to the correct chain RPC
   * @return GasEstimate with gasPrice, gasLimit, and totalFeeWei, or null on error
   */
  suspend fun estimateGas(
    from: String,
    to: String,
    value: BigInteger,
    data: String?,
    isTokenTransfer: Boolean,
    web3j: Web3j,
  ): GasEstimate? {
    return try {
      withContext(Dispatchers.IO) {
        val gasPrice = web3j.ethGasPrice().send().gasPrice

        val tx = Transaction.createFunctionCallTransaction(
          from,
          null, // nonce — null lets the node determine it
          gasPrice,
          null, // gasLimit — null for estimation
          to,
          value,
          data ?: "",
        )

        val estimateResponse = web3j.ethEstimateGas(tx).send()
        if (estimateResponse.hasError()) {
          return@withContext null
        }

        val rawEstimate = estimateResponse.amountUsed
        val gasLimit = applyGasBuffer(rawEstimate, isTokenTransfer)
        val totalFeeWei = gasPrice.multiply(gasLimit)
        val totalFeeNative = BigDecimal(totalFeeWei).divide(WEI_PER_ETH, ETH_DECIMALS, RoundingMode.HALF_UP)

        GasEstimate(
          gasPrice = gasPrice,
          gasLimit = gasLimit,
          totalFeeWei = totalFeeWei,
          totalFeeNative = totalFeeNative,
        )
      }
    } catch (e: Exception) {
      // Offline mode: return null
      null
    }
  }

  /**
   * Broadcasts a signed transaction to the network.
   *
   * @param signedTxHex 0x-prefixed hex-encoded signed transaction
   * @param web3j the Web3j instance connected to the correct chain RPC
   * @return transaction hash, or null on error
   */
  suspend fun broadcastTransaction(
    signedTxHex: String,
    web3j: Web3j,
  ): String? {
    // Basic validation: must be 0x-prefixed hex string with even length
    if (!signedTxHex.startsWith("0x") || signedTxHex.length < 4 || signedTxHex.length % 2 != 0) {
      return null
    }
    return try {
      withContext(Dispatchers.IO) {
        val response = web3j.ethSendRawTransaction(signedTxHex).send()
        if (response.hasError()) {
          return@withContext null
        }
        response.transactionHash
      }
    } catch (e: Exception) {
      // Offline mode: return null
      null
    }
  }
}

/**
 * Gas estimation result returned by [EvmManager.estimateGas].
 *
 * @param gasPrice the current gas price in wei
 * @param gasLimit the estimated gas limit (with buffer for tokens)
 * @param totalFeeWei total fee in wei (gasPrice * gasLimit)
 * @param totalFeeNative total fee in native units (ETH/BNB/MATIC)
 */
data class GasEstimate(
  val gasPrice: BigInteger,
  val gasLimit: BigInteger,
  val totalFeeWei: BigInteger,
  val totalFeeNative: BigDecimal,
)
