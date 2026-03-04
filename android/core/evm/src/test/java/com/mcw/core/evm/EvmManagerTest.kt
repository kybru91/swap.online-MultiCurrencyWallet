package com.mcw.core.evm

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthGetBalance
import org.web3j.protocol.core.methods.response.EthEstimateGas
import org.web3j.protocol.core.methods.response.EthGasPrice
import org.web3j.protocol.core.methods.response.EthSendTransaction
import org.web3j.protocol.core.methods.response.EthCall
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Unit tests for EvmManager — EVM balance, gas, fiat, signing, broadcast.
 *
 * Tests cover:
 * - Balance parsing (wei -> ETH conversion)
 * - ERC20 token balance parsing with different decimals (6, 8, 18)
 * - CoinGecko fiat price parsing
 * - Gas estimation (native vs token with 1.05x buffer)
 * - Offline mode (null balance on error, no crash)
 * - Fiat calculation precision
 * - Transaction building and signing
 */
class EvmManagerTest {

  private lateinit var evmManager: EvmManager

  @Before
  fun setUp() {
    evmManager = EvmManager()
  }

  // ===== Balance parsing: wei -> ETH =====

  @Test
  fun testParseBalance_1Ether() {
    // 1 ETH = 1e18 wei = 1000000000000000000 wei
    val weiHex = "0x" + BigInteger("1000000000000000000").toString(16)
    val result = EvmManager.parseBalanceFromWei(weiHex)
    assertEquals(
      "1 ETH in wei should parse to BigDecimal 1.0",
      BigDecimal("1.000000000000000000"),
      result
    )
  }

  @Test
  fun testParseBalance_0Wei() {
    val result = EvmManager.parseBalanceFromWei("0x0")
    assertEquals(
      "0 wei should parse to BigDecimal 0",
      BigDecimal.ZERO.setScale(18),
      result
    )
  }

  @Test
  fun testParseBalance_1Wei() {
    // 1 wei = 0.000000000000000001 ETH
    val result = EvmManager.parseBalanceFromWei("0x1")
    assertEquals(
      "1 wei should parse to 0.000000000000000001 ETH",
      BigDecimal("0.000000000000000001"),
      result
    )
  }

  @Test
  fun testParseBalance_largeAmount() {
    // 100.5 ETH = 100500000000000000000 wei
    val weiValue = BigInteger("100500000000000000000")
    val weiHex = "0x" + weiValue.toString(16)
    val result = EvmManager.parseBalanceFromWei(weiHex)
    assertEquals(
      "100.5 ETH in wei should parse correctly",
      BigDecimal("100.500000000000000000"),
      result
    )
  }

  @Test
  fun testParseBalance_decimalHex() {
    // 0.123456789 ETH = 123456789000000000 wei
    val weiValue = BigInteger("123456789000000000")
    val weiHex = "0x" + weiValue.toString(16)
    val result = EvmManager.parseBalanceFromWei(weiHex)
    assertEquals(
      "0.123456789 ETH in wei should parse correctly",
      BigDecimal("0.123456789000000000"),
      result
    )
  }

  // ===== ERC20 token balance parsing with different decimals =====

  @Test
  fun testParseTokenBalance_usdt6Decimals() {
    // 1 USDT = 1_000_000 raw (6 decimals)
    val rawBalance = BigInteger("1000000")
    val result = EvmManager.parseTokenBalance(rawBalance, 6)
    assertEquals(
      "1 USDT (6 decimals) raw = 1.000000",
      BigDecimal("1.000000"),
      result
    )
  }

  @Test
  fun testParseTokenBalance_wbtc8Decimals() {
    // 1 WBTC = 100_000_000 raw (8 decimals)
    val rawBalance = BigInteger("100000000")
    val result = EvmManager.parseTokenBalance(rawBalance, 8)
    assertEquals(
      "1 WBTC (8 decimals) raw = 1.00000000",
      BigDecimal("1.00000000"),
      result
    )
  }

  @Test
  fun testParseTokenBalance_standard18Decimals() {
    // 1 token with 18 decimals = 1e18 raw
    val rawBalance = BigInteger("1000000000000000000")
    val result = EvmManager.parseTokenBalance(rawBalance, 18)
    assertEquals(
      "1 token (18 decimals) raw = 1.000000000000000000",
      BigDecimal("1.000000000000000000"),
      result
    )
  }

  @Test
  fun testParseTokenBalance_fractionalUsdt() {
    // 0.5 USDT = 500_000 raw (6 decimals)
    val rawBalance = BigInteger("500000")
    val result = EvmManager.parseTokenBalance(rawBalance, 6)
    assertEquals(
      "0.5 USDT (6 decimals)",
      BigDecimal("0.500000"),
      result
    )
  }

  @Test
  fun testParseTokenBalance_zeroBalance() {
    val rawBalance = BigInteger.ZERO
    val result = EvmManager.parseTokenBalance(rawBalance, 18)
    assertEquals(
      "Zero token balance should be 0",
      BigDecimal.ZERO.setScale(18),
      result
    )
  }

  @Test
  fun testParseTokenBalance_smallestUnit() {
    // 1 raw unit of 18-decimal token = 0.000000000000000001
    val rawBalance = BigInteger.ONE
    val result = EvmManager.parseTokenBalance(rawBalance, 18)
    assertEquals(
      "1 raw unit of 18-decimal token",
      BigDecimal("0.000000000000000001"),
      result
    )
  }

  // ===== CoinGecko price parsing =====

  @Test
  fun testParseCoinGeckoPrices_allCoins() {
    // Simulates CoinGecko API response for bitcoin, ethereum, binancecoin, matic-network
    val apiResponse: Map<String, Map<String, Double>> = mapOf(
      "bitcoin" to mapOf("usd" to 50000.0),
      "ethereum" to mapOf("usd" to 3000.0),
      "binancecoin" to mapOf("usd" to 400.0),
      "matic-network" to mapOf("usd" to 1.5)
    )

    val result = EvmManager.parseCoinGeckoPrices(apiResponse)

    assertEquals("BTC price", BigDecimal("50000.0"), result["BTC"])
    assertEquals("ETH price", BigDecimal("3000.0"), result["ETH"])
    assertEquals("BNB price", BigDecimal("400.0"), result["BNB"])
    assertEquals("MATIC price", BigDecimal("1.5"), result["MATIC"])
  }

  @Test
  fun testParseCoinGeckoPrices_partialData() {
    // Only BTC and ETH returned, BNB and MATIC missing
    val apiResponse: Map<String, Map<String, Double>> = mapOf(
      "bitcoin" to mapOf("usd" to 50000.0),
      "ethereum" to mapOf("usd" to 3000.0),
    )

    val result = EvmManager.parseCoinGeckoPrices(apiResponse)

    assertEquals("BTC price present", BigDecimal("50000.0"), result["BTC"])
    assertEquals("ETH price present", BigDecimal("3000.0"), result["ETH"])
    assertNull("BNB price missing should be null", result["BNB"])
    assertNull("MATIC price missing should be null", result["MATIC"])
  }

  @Test
  fun testParseCoinGeckoPrices_emptyResponse() {
    val apiResponse: Map<String, Map<String, Double>> = emptyMap()
    val result = EvmManager.parseCoinGeckoPrices(apiResponse)
    assertTrue("Empty response should return empty map", result.isEmpty())
  }

  // ===== Gas estimation: native vs token buffer =====

  @Test
  fun testGasEstimationNative_noBuffer() {
    // Native transfer: gas estimate = 21000, no buffer applied
    val estimate = BigInteger.valueOf(21000)
    val result = EvmManager.applyGasBuffer(estimate, isTokenTransfer = false)
    assertEquals(
      "Native transfer gas limit should equal estimate (no buffer)",
      BigInteger.valueOf(21000),
      result
    )
  }

  @Test
  fun testGasEstimationToken_1_05xBuffer() {
    // Token transfer: gas estimate = 50000, 1.05x buffer
    val estimate = BigInteger.valueOf(50000)
    val result = EvmManager.applyGasBuffer(estimate, isTokenTransfer = true)
    assertEquals(
      "Token transfer gas limit should be estimate * 1.05 = 52500",
      BigInteger.valueOf(52500),
      result
    )
  }

  @Test
  fun testGasEstimationToken_roundingBehavior() {
    // Token transfer: gas estimate = 33333, 1.05x = 34999.65 -> should round up to 35000
    val estimate = BigInteger.valueOf(33333)
    val result = EvmManager.applyGasBuffer(estimate, isTokenTransfer = true)
    // 33333 * 1.05 = 34999.65 -> ceiling -> 35000
    assertEquals(
      "Token gas buffer should round up (ceiling)",
      BigInteger.valueOf(35000),
      result
    )
  }

  @Test
  fun testGasEstimationNative_largeGas() {
    // Large gas estimate for a complex native call (but not token)
    val estimate = BigInteger.valueOf(100000)
    val result = EvmManager.applyGasBuffer(estimate, isTokenTransfer = false)
    assertEquals(
      "Non-token transfer gas limit should have no buffer regardless of size",
      BigInteger.valueOf(100000),
      result
    )
  }

  // ===== Offline mode: null balance on error =====

  @Test
  fun testOfflineMode_fetchBalanceReturnsNullOnError() = runTest {
    // Create a mock Web3j that throws on eth_getBalance
    val mockWeb3j = mock<Web3j>()
    val mockRequest = mock<Request<*, EthGetBalance>>()
    whenever(mockWeb3j.ethGetBalance(any(), any())).thenReturn(mockRequest)
    whenever(mockRequest.send()).thenThrow(RuntimeException("Network error"))

    val result = evmManager.fetchBalance("0x1234567890abcdef1234567890abcdef12345678", mockWeb3j)
    assertNull(
      "Balance should be null when RPC call fails (offline mode)",
      result
    )
  }

  @Test
  fun testOfflineMode_fetchTokenBalanceReturnsNullOnError() = runTest {
    // Create a mock Web3j that throws on ethCall (used by balanceOf)
    val mockWeb3j = mock<Web3j>()
    val mockRequest = mock<Request<*, EthCall>>()
    whenever(mockWeb3j.ethCall(any(), any())).thenReturn(mockRequest)
    whenever(mockRequest.send()).thenThrow(RuntimeException("Network error"))

    val result = evmManager.fetchTokenBalance(
      address = "0x1234567890abcdef1234567890abcdef12345678",
      contractAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
      decimals = 6,
      web3j = mockWeb3j,
    )
    assertNull(
      "Token balance should be null when RPC call fails (offline mode)",
      result
    )
  }

  @Test
  fun testOfflineMode_estimateGasReturnsNullOnError() = runTest {
    val mockWeb3j = mock<Web3j>()
    val mockGasPriceReq = mock<Request<*, EthGasPrice>>()
    whenever(mockWeb3j.ethGasPrice()).thenReturn(mockGasPriceReq)
    whenever(mockGasPriceReq.send()).thenThrow(RuntimeException("Network error"))

    val result = evmManager.estimateGas(
      from = "0x1234567890abcdef1234567890abcdef12345678",
      to = "0xabcdef1234567890abcdef1234567890abcdef12",
      value = BigInteger.ZERO,
      data = null,
      isTokenTransfer = false,
      web3j = mockWeb3j,
    )
    assertNull(
      "Gas estimation should be null when RPC call fails (offline mode)",
      result
    )
  }

  // ===== Fiat calculation =====

  @Test
  fun testFiatCalculation_btcPrice() {
    // 1.5 BTC * $50000 = $75000.00
    val balance = BigDecimal("1.5")
    val price = BigDecimal("50000")
    val result = EvmManager.calculateFiatValue(balance, price)
    assertEquals(
      "1.5 BTC * $50000 should be $75000.00",
      BigDecimal("75000.00"),
      result
    )
  }

  @Test
  fun testFiatCalculation_ethPrice() {
    // 2.0 ETH * $3000 = $6000.00
    val balance = BigDecimal("2.0")
    val price = BigDecimal("3000")
    val result = EvmManager.calculateFiatValue(balance, price)
    assertEquals(
      "2.0 ETH * $3000 should be $6000.00",
      BigDecimal("6000.00"),
      result
    )
  }

  @Test
  fun testFiatCalculation_smallAmount() {
    // 0.001 ETH * $3000 = $3.00
    val balance = BigDecimal("0.001")
    val price = BigDecimal("3000")
    val result = EvmManager.calculateFiatValue(balance, price)
    assertEquals(
      "0.001 ETH * $3000 should be $3.00",
      BigDecimal("3.00"),
      result
    )
  }

  @Test
  fun testFiatCalculation_zeroBalance() {
    val balance = BigDecimal.ZERO
    val price = BigDecimal("50000")
    val result = EvmManager.calculateFiatValue(balance, price)
    assertEquals(
      "Zero balance should be $0.00",
      BigDecimal("0.00"),
      result
    )
  }

  @Test
  fun testFiatCalculation_zeroPrice() {
    val balance = BigDecimal("1.5")
    val price = BigDecimal.ZERO
    val result = EvmManager.calculateFiatValue(balance, price)
    assertEquals(
      "Zero price should be $0.00",
      BigDecimal("0.00"),
      result
    )
  }

  // ===== Transaction building =====

  @Test
  fun testBuildTransaction_nativeTransfer() {
    val tx = EvmManager.buildTransaction(
      nonce = BigInteger.ZERO,
      to = "0xabcdef1234567890abcdef1234567890abcdef12",
      value = BigInteger("1000000000000000000"), // 1 ETH in wei
      gasPrice = BigInteger("20000000000"), // 20 Gwei
      gasLimit = BigInteger.valueOf(21000),
      data = null,
      chainId = 1L,
    )

    assertNotNull("Built transaction should not be null", tx)
    assertEquals(
      "Transaction recipient should match",
      "0xabcdef1234567890abcdef1234567890abcdef12",
      tx.to
    )
    assertEquals(
      "Transaction value should match",
      BigInteger("1000000000000000000"),
      tx.value
    )
    assertEquals(
      "Transaction gas price should match",
      BigInteger("20000000000"),
      tx.gasPrice
    )
    assertEquals(
      "Transaction gas limit should match",
      BigInteger.valueOf(21000),
      tx.gasLimit
    )
  }

  @Test
  fun testBuildTransaction_withData() {
    // ERC20 transfer encoded data (simulated)
    val data = "0xa9059cbb000000000000000000000000abcdef12345678900000000000000000000f4240"
    val tx = EvmManager.buildTransaction(
      nonce = BigInteger.ONE,
      to = "0xdAC17F958D2ee523a2206206994597C13D831ec7", // USDT contract
      value = BigInteger.ZERO, // ERC20 transfers have 0 ETH value
      gasPrice = BigInteger("20000000000"),
      gasLimit = BigInteger.valueOf(65000),
      data = data,
      chainId = 1L,
    )

    assertNotNull("Built transaction with data should not be null", tx)
    // web3j RawTransaction stores data without 0x prefix
    val expectedData = data.removePrefix("0x")
    assertEquals(
      "Transaction data should match (without 0x prefix)",
      expectedData,
      tx.data
    )
    assertEquals(
      "Token transfer value should be 0",
      BigInteger.ZERO,
      tx.value
    )
  }

  // ===== Sign transaction =====

  @Test
  fun testSignTransaction_producesHexString() {
    val tx = EvmManager.buildTransaction(
      nonce = BigInteger.ZERO,
      to = "0xabcdef1234567890abcdef1234567890abcdef12",
      value = BigInteger("1000000000000000000"),
      gasPrice = BigInteger("20000000000"),
      gasLimit = BigInteger.valueOf(21000),
      data = null,
      chainId = 1L,
    )

    // Use known test private key (abandon mnemonic ETH key)
    val privateKeyHex = "0x1ab42cc412b618bdea3a599e3c9bae199ebf030895b039e9db1e30dafb12b727"
    val signed = EvmManager.signTransaction(tx, privateKeyHex, 1L)

    assertNotNull("Signed transaction should not be null", signed)
    assertTrue(
      "Signed transaction should be 0x-prefixed hex",
      signed!!.startsWith("0x")
    )
    assertTrue(
      "Signed transaction should be non-trivial length",
      signed.length > 10
    )
  }

  @Test
  fun testSignTransaction_differentKeysDifferentSignatures() {
    val tx = EvmManager.buildTransaction(
      nonce = BigInteger.ZERO,
      to = "0xabcdef1234567890abcdef1234567890abcdef12",
      value = BigInteger("1000000000000000000"),
      gasPrice = BigInteger("20000000000"),
      gasLimit = BigInteger.valueOf(21000),
      data = null,
      chainId = 1L,
    )

    val signed1 = EvmManager.signTransaction(
      tx,
      "0x1ab42cc412b618bdea3a599e3c9bae199ebf030895b039e9db1e30dafb12b727",
      1L,
    )
    val signed2 = EvmManager.signTransaction(
      tx,
      "0x7af65ba4dd53f23495dcb04995e96f47c243217fc279f10795871b725cd009ae",
      1L,
    )

    assertNotNull("Signed tx 1 should not be null", signed1)
    assertNotNull("Signed tx 2 should not be null", signed2)
    assertTrue(
      "Different keys should produce different signed transactions",
      signed1 != signed2
    )
  }

  // ===== CoinGecko symbol mapping =====

  @Test
  fun testCoinGeckoIdMapping() {
    assertEquals("bitcoin", EvmManager.COINGECKO_IDS["BTC"])
    assertEquals("ethereum", EvmManager.COINGECKO_IDS["ETH"])
    assertEquals("binancecoin", EvmManager.COINGECKO_IDS["BNB"])
    assertEquals("matic-network", EvmManager.COINGECKO_IDS["MATIC"])
  }

  // ===== Gas estimation with mock Web3j (integration-style unit test) =====

  @Test
  fun testEstimateGas_nativeTransfer() = runTest {
    val mockWeb3j = mock<Web3j>()

    // Mock ethGasPrice
    val gasPriceResponse = mock<EthGasPrice>()
    whenever(gasPriceResponse.gasPrice).thenReturn(BigInteger("20000000000")) // 20 Gwei
    val gasPriceRequest = mock<Request<*, EthGasPrice>>()
    whenever(gasPriceRequest.send()).thenReturn(gasPriceResponse)
    whenever(mockWeb3j.ethGasPrice()).thenReturn(gasPriceRequest)

    // Mock ethEstimateGas
    val estimateGasResponse = mock<EthEstimateGas>()
    whenever(estimateGasResponse.amountUsed).thenReturn(BigInteger.valueOf(21000))
    whenever(estimateGasResponse.hasError()).thenReturn(false)
    val estimateGasRequest = mock<Request<*, EthEstimateGas>>()
    whenever(estimateGasRequest.send()).thenReturn(estimateGasResponse)
    whenever(mockWeb3j.ethEstimateGas(any())).thenReturn(estimateGasRequest)

    val result = evmManager.estimateGas(
      from = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94",
      to = "0xabcdef1234567890abcdef1234567890abcdef12",
      value = BigInteger("1000000000000000000"),
      data = null,
      isTokenTransfer = false,
      web3j = mockWeb3j,
    )

    assertNotNull("Gas estimation should not be null", result)
    assertEquals(
      "Gas price should be 20 Gwei",
      BigInteger("20000000000"),
      result!!.gasPrice
    )
    assertEquals(
      "Gas limit for native transfer should be 21000 (no buffer)",
      BigInteger.valueOf(21000),
      result.gasLimit
    )
    assertEquals(
      "Total fee = gasPrice * gasLimit",
      BigInteger("20000000000").multiply(BigInteger.valueOf(21000)),
      result.totalFeeWei
    )
  }

  @Test
  fun testEstimateGas_tokenTransfer() = runTest {
    val mockWeb3j = mock<Web3j>()

    // Mock ethGasPrice
    val gasPriceResponse = mock<EthGasPrice>()
    whenever(gasPriceResponse.gasPrice).thenReturn(BigInteger("20000000000"))
    val gasPriceRequest = mock<Request<*, EthGasPrice>>()
    whenever(gasPriceRequest.send()).thenReturn(gasPriceResponse)
    whenever(mockWeb3j.ethGasPrice()).thenReturn(gasPriceRequest)

    // Mock ethEstimateGas
    val estimateGasResponse = mock<EthEstimateGas>()
    whenever(estimateGasResponse.amountUsed).thenReturn(BigInteger.valueOf(50000))
    whenever(estimateGasResponse.hasError()).thenReturn(false)
    val estimateGasRequest = mock<Request<*, EthEstimateGas>>()
    whenever(estimateGasRequest.send()).thenReturn(estimateGasResponse)
    whenever(mockWeb3j.ethEstimateGas(any())).thenReturn(estimateGasRequest)

    val result = evmManager.estimateGas(
      from = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94",
      to = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
      value = BigInteger.ZERO,
      data = "0xa9059cbb",
      isTokenTransfer = true,
      web3j = mockWeb3j,
    )

    assertNotNull("Gas estimation should not be null", result)
    assertEquals(
      "Gas limit for token transfer should be 50000 * 1.05 = 52500",
      BigInteger.valueOf(52500),
      result!!.gasLimit
    )
  }

  // ===== Broadcast returns null on error =====

  @Test
  fun testBroadcast_returnsNullOnError() = runTest {
    val mockWeb3j = mock<Web3j>()
    val mockRequest = mock<Request<*, EthSendTransaction>>()
    whenever(mockWeb3j.ethSendRawTransaction(any())).thenReturn(mockRequest)
    whenever(mockRequest.send()).thenThrow(RuntimeException("Network error"))

    val result = evmManager.broadcastTransaction("0xsigned_tx_hex", mockWeb3j)
    assertNull(
      "Broadcast should return null on error (offline mode)",
      result
    )
  }

  @Test
  fun testBroadcast_returnsTxHash() = runTest {
    val mockWeb3j = mock<Web3j>()
    val sendResponse = mock<EthSendTransaction>()
    whenever(sendResponse.transactionHash).thenReturn("0xabc123hash")
    whenever(sendResponse.hasError()).thenReturn(false)
    val mockRequest = mock<Request<*, EthSendTransaction>>()
    whenever(mockRequest.send()).thenReturn(sendResponse)
    whenever(mockWeb3j.ethSendRawTransaction(any())).thenReturn(mockRequest)

    val result = evmManager.broadcastTransaction("0xsigned_tx_hex", mockWeb3j)
    assertEquals(
      "Broadcast should return tx hash",
      "0xabc123hash",
      result
    )
  }
}
