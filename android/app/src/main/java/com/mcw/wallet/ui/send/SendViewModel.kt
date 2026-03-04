package com.mcw.wallet.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mcw.core.btc.BtcManager
import com.mcw.core.btc.InsufficientFundsException
import com.mcw.core.evm.EvmManager
import com.mcw.core.evm.GasEstimate
import com.mcw.core.storage.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Abstraction for EVM send operations.
 * Decouples the SendViewModel from web3j dependency (which lives in :core:evm).
 * Production implementation wraps EvmManager + chain-specific Web3j instances.
 */
interface EvmSendHelper {
  /**
   * Estimates gas for a native EVM transfer.
   * @return GasEstimate or null on error
   */
  suspend fun estimateGas(from: String, to: String, valueWei: BigInteger): GasEstimate?

  /**
   * Builds, signs, and broadcasts an EVM transaction.
   * @return transaction hash or null on error
   */
  suspend fun sendTransaction(
    to: String,
    valueWei: BigInteger,
    gasPrice: BigInteger,
    gasLimit: BigInteger,
    privateKeyHex: String,
    chainId: Long,
  ): String?
}

/**
 * Provides EIP-55 address checksum computation.
 * Decouples the SendViewModel from web3j Keys import.
 * Production implementation calls Keys.toChecksumAddress().
 */
interface AddressChecksumProvider {
  /**
   * Converts an EVM address to EIP-55 checksummed format.
   * @param address the raw 0x-prefixed hex address
   * @return the checksummed address
   */
  fun toChecksumAddress(address: String): String
}

/**
 * Default AddressChecksumProvider using Keccak-256 hashing (EIP-55).
 *
 * Implements EIP-55 without direct web3j dependency by using BouncyCastle's
 * Keccak-256 via java.security.MessageDigest (BouncyCastle is registered as a
 * security provider by bitcoinj which is on the classpath).
 *
 * Algorithm:
 * 1. Take the lowercase hex address (without 0x prefix)
 * 2. Compute Keccak-256 hash of the lowercase address string
 * 3. For each hex character in the address: if the corresponding hash nibble >= 8, uppercase it
 */
class DefaultAddressChecksumProvider : AddressChecksumProvider {
  override fun toChecksumAddress(address: String): String {
    val cleanAddress = address.removePrefix("0x").removePrefix("0X").lowercase()
    if (cleanAddress.length != 40) return address

    val hash = try {
      keccak256(cleanAddress.toByteArray(Charsets.US_ASCII))
    } catch (e: Exception) {
      // If Keccak-256 is not available, return original address unchanged
      return address
    }

    val hashHex = hash.joinToString("") { "%02x".format(it) }

    val checksummed = StringBuilder("0x")
    for (i in cleanAddress.indices) {
      val c = cleanAddress[i]
      if (c in '0'..'9') {
        checksummed.append(c)
      } else {
        val hashNibble = hashHex[i].digitToIntOrNull(16) ?: 0
        checksummed.append(if (hashNibble >= 8) c.uppercaseChar() else c)
      }
    }
    return checksummed.toString()
  }

  /**
   * Computes Keccak-256 hash using BouncyCastle's digest implementation.
   * BouncyCastle is available transitively via bitcoinj dependency.
   */
  private fun keccak256(input: ByteArray): ByteArray {
    // Try BouncyCastle's Keccak digest directly
    val digest = org.bouncycastle.crypto.digests.KeccakDigest(256)
    digest.update(input, 0, input.size)
    val output = ByteArray(32)
    digest.doFinal(output, 0)
    return output
  }
}

/**
 * ViewModel for the Send Transaction screen.
 *
 * Manages the send transaction flow with state machine:
 * Idle -> Building -> Confirming -> Submitting -> Success | Error
 *
 * Supports both BTC (via BtcManager) and EVM chains (via EvmSendHelper).
 * Address validation, amount validation, fee estimation, transaction building,
 * signing, and broadcast are all handled here.
 *
 * Duplicate submission prevention: Send button is disabled when state = Submitting.
 *
 * Note: This ViewModel is not @HiltViewModel because it requires runtime parameters
 * (currency, balance, addresses) that come from the navigation arguments.
 * In production, use a ViewModelProvider.Factory or assisted injection.
 *
 * @param currency the currency being sent (BTC, ETH, BNB, MATIC)
 * @param balance the sender's current balance in native units
 * @param senderAddress the sender's address (BTC or EVM)
 * @param btcManager BTC operations manager
 * @param evmManager EVM operations manager (for static companion methods)
 * @param secureStorage encrypted storage for private keys
 * @param evmSendHelper EVM transaction helper (null for BTC)
 * @param chainId EVM chain ID (0 for BTC)
 * @param checksumProvider EIP-55 address checksum provider
 */
class SendViewModel(
  private val currency: String,
  private val balance: BigDecimal,
  private val senderAddress: String,
  private val btcManager: BtcManager,
  @Suppress("UNUSED_PARAMETER") evmManager: EvmManager, // kept for API consistency; EVM ops via evmSendHelper
  private val secureStorage: SecureStorage,
  private val evmSendHelper: EvmSendHelper? = null,
  private val chainId: Long = 0L,
  private val checksumProvider: AddressChecksumProvider = DefaultAddressChecksumProvider(),
) : ViewModel() {

  companion object {
    /** BTC address regex: alphanumeric, 26-35 characters (from web's typeforce.ts) */
    private val BTC_ADDRESS_REGEX = Regex("^[A-Za-z0-9]{26,35}$")

    /** EVM address regex: 0x followed by 40 hex characters */
    private val EVM_ADDRESS_REGEX = Regex("^0x[A-Fa-f0-9]{40}$")

    /** Satoshis per BTC */
    private val SATOSHI_DIVISOR = BigDecimal("100000000")

    /** Wei per ETH */
    private val WEI_PER_ETH = BigDecimal.TEN.pow(18)

    /** EVM fee tier multipliers: Fast (1.5x), Normal (1.0x), Slow (0.8x) */
    private val EVM_FEE_MULTIPLIERS = listOf(
      Triple("Fast", BigDecimal("1.5"), "~30 sec"),
      Triple("Normal", BigDecimal("1.0"), "~2 min"),
      Triple("Slow", BigDecimal("0.8"), "~5 min"),
    )
  }

  private val isBtc: Boolean = currency == "BTC"

  private val _uiState = MutableStateFlow(SendUiState())
  val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

  /**
   * Validates a recipient address for the current currency.
   *
   * BTC: checks P2PKH format (alphanumeric, 26-35 chars).
   * EVM: checks 0x + 40 hex chars format. If valid hex but not EIP-55 checksummed,
   * returns ChecksumWarning per user-spec: "Address checksum invalid, proceed anyway?"
   *
   * @param address the recipient address to validate
   * @return validation result
   */
  fun validateAddress(address: String): AddressValidation {
    if (address.isBlank()) {
      return AddressValidation.Invalid("Address is required")
    }

    // Prevent sending to own address
    if (address.equals(senderAddress, ignoreCase = true)) {
      return AddressValidation.Invalid("Cannot send to your own address")
    }

    return if (isBtc) {
      validateBtcAddress(address)
    } else {
      validateEvmAddress(address)
    }
  }

  private fun validateBtcAddress(address: String): AddressValidation {
    if (!BTC_ADDRESS_REGEX.matches(address)) {
      return AddressValidation.Invalid("Invalid BTC address format")
    }
    return AddressValidation.Valid
  }

  private fun validateEvmAddress(address: String): AddressValidation {
    if (!EVM_ADDRESS_REGEX.matches(address)) {
      return AddressValidation.Invalid("Invalid ${currency} address format")
    }

    // Check EIP-55 checksum: compute the correct checksummed form and compare
    val checksumAddress = checksumProvider.toChecksumAddress(address)
    if (address == checksumAddress) {
      return AddressValidation.Valid
    }

    // Address is valid hex but not properly checksummed — warn user
    return AddressValidation.ChecksumWarning("Address checksum invalid, proceed anyway?")
  }

  /**
   * Validates the send amount against balance.
   *
   * - Empty or non-numeric: error
   * - Zero or negative: error "Amount must be greater than zero"
   * - Exceeds balance: error "Insufficient balance"
   * - Valid: amount > 0 and <= balance
   *
   * @param amountStr the amount string entered by the user
   * @return validation result
   */
  fun validateAmount(amountStr: String): AmountValidation {
    if (amountStr.isBlank()) {
      return AmountValidation.Invalid("Amount is required")
    }

    val amount = try {
      BigDecimal(amountStr)
    } catch (e: NumberFormatException) {
      return AmountValidation.Invalid("Invalid amount format")
    }

    if (amount <= BigDecimal.ZERO) {
      return AmountValidation.Invalid("Amount must be greater than zero")
    }

    if (amount > balance) {
      return AmountValidation.Invalid("Insufficient balance")
    }

    return AmountValidation.Valid
  }

  /**
   * Updates the recipient address in UI state and runs validation.
   */
  fun onAddressChanged(address: String) {
    val validation = if (address.isNotBlank()) validateAddress(address) else null
    _uiState.update { it.copy(recipientAddress = address, addressValidation = validation) }
  }

  /**
   * Updates the amount in UI state and runs validation.
   */
  fun onAmountChanged(amount: String) {
    val validation = if (amount.isNotBlank()) validateAmount(amount) else null
    _uiState.update { it.copy(amount = amount, amountValidation = validation) }
  }

  /**
   * Selects a fee tier from the available options.
   *
   * @param index the index of the selected fee tier
   */
  fun selectFeeTier(index: Int) {
    val feeCount = _uiState.value.feeOptions?.size ?: return
    if (index < 0 || index >= feeCount) return
    _uiState.update { it.copy(selectedFeeTierIndex = index) }
  }

  /**
   * Resets the state machine back to Idle.
   * Used after error or success to allow a new transaction.
   */
  fun resetState() {
    _uiState.update {
      it.copy(
        state = SendState.Idle,
        error = null,
        txHash = null,
        feeOptions = null,
        selectedFeeTierIndex = 0,
      )
    }
  }

  /**
   * Initiates the send flow: validates inputs, estimates fees, transitions to Confirming.
   *
   * State transition: Idle -> Building -> Confirming (or Error on failure).
   *
   * For BTC: fetches fee rates from Blockcypher, calculates fees for each tier.
   * For EVM: calls eth_gasPrice + eth_estimateGas, creates multiplied tiers.
   */
  fun prepareSend() {
    val currentState = _uiState.value

    // Validate address
    val addressResult = validateAddress(currentState.recipientAddress)
    if (addressResult is AddressValidation.Invalid) {
      _uiState.update {
        it.copy(
          state = SendState.Error,
          error = addressResult.message,
        )
      }
      return
    }

    // Validate amount
    val amountResult = validateAmount(currentState.amount)
    if (amountResult is AmountValidation.Invalid) {
      _uiState.update {
        it.copy(
          state = SendState.Error,
          error = amountResult.message,
        )
      }
      return
    }

    // Transition to Building
    _uiState.update { it.copy(state = SendState.Building, error = null) }

    viewModelScope.launch {
      try {
        val feeOptions = if (isBtc) {
          buildBtcFeeOptions()
        } else {
          buildEvmFeeOptions()
        }

        _uiState.update {
          it.copy(
            state = SendState.Confirming,
            feeOptions = feeOptions,
            selectedFeeTierIndex = 0,
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            state = SendState.Error,
            error = "Fee estimation failed: ${e.message}",
          )
        }
      }
    }
  }

  /**
   * Builds BTC fee tier options.
   *
   * Fetches fee rates from Blockcypher, fetches UTXOs, calculates tx size,
   * and computes fee for each tier (Fast/Normal/Slow).
   */
  private suspend fun buildBtcFeeOptions(): List<FeeTierOption> {
    val feeRates = btcManager.estimateFees()
    val amountSat = BigDecimal(_uiState.value.amount)
      .multiply(SATOSHI_DIVISOR)
      .setScale(0, RoundingMode.FLOOR)
      .toLong()

    // Fetch UTXOs and estimate transaction size
    val utxos = btcManager.fetchUnspentOutputs(senderAddress)
    val numInputs = try {
      btcManager.selectUtxos(utxos, amountSat).size
    } catch (e: InsufficientFundsException) {
      // If we can't select UTXOs, still show fee estimates with 1 input
      1
    }
    // 2 outputs: recipient + change
    val txSize = btcManager.calculateTxSize(numInputs, 2)

    data class Tier(val label: String, val rate: Long, val time: String)
    val tiers = listOf(
      Tier("Fast", feeRates.fast, "~10 min"),
      Tier("Normal", feeRates.normal, "~30 min"),
      Tier("Slow", feeRates.slow, "~60 min"),
    )

    return tiers.map { tier ->
      val feeSat = btcManager.calculateFee(tier.rate, txSize)
      val feeBtc = BigDecimal(feeSat).divide(SATOSHI_DIVISOR, 8, RoundingMode.HALF_UP)
      FeeTierOption(
        label = tier.label,
        estimatedTime = tier.time,
        feeAmount = "${feeBtc.toPlainString()} BTC",
        feeNative = feeBtc,
        feeRateSatPerKb = tier.rate,
      )
    }
  }

  /**
   * Builds EVM fee tier options.
   *
   * Uses EvmSendHelper to call eth_gasPrice + eth_estimateGas, then creates 3 tiers
   * with multiplied gas prices (Fast: 1.5x, Normal: 1.0x, Slow: 0.8x).
   */
  private suspend fun buildEvmFeeOptions(): List<FeeTierOption> {
    val helper = evmSendHelper
      ?: throw IllegalStateException("EvmSendHelper required for EVM transactions")

    val amountWei = BigDecimal(_uiState.value.amount)
      .multiply(WEI_PER_ETH)
      .setScale(0, RoundingMode.FLOOR)
      .toBigInteger()

    val gasEstimate = helper.estimateGas(
      from = senderAddress,
      to = _uiState.value.recipientAddress,
      valueWei = amountWei,
    ) ?: throw RuntimeException("Gas estimation failed: RPC error")

    return EVM_FEE_MULTIPLIERS.map { (label, multiplier, time) ->
      val adjustedGasPrice = BigDecimal(gasEstimate.gasPrice)
        .multiply(multiplier)
        .setScale(0, RoundingMode.CEILING)
        .toBigInteger()

      val totalFeeWei = adjustedGasPrice.multiply(gasEstimate.gasLimit)
      val totalFeeNative = BigDecimal(totalFeeWei).divide(WEI_PER_ETH, 18, RoundingMode.HALF_UP)

      FeeTierOption(
        label = label,
        estimatedTime = time,
        feeAmount = "${totalFeeNative.stripTrailingZeros().toPlainString()} $currency",
        feeNative = totalFeeNative,
        gasPriceWei = adjustedGasPrice,
        gasLimit = gasEstimate.gasLimit,
      )
    }
  }

  /**
   * Confirms and broadcasts the transaction.
   *
   * State transition: Confirming -> Submitting -> Success | Error
   *
   * This method should be called after the biometric prompt succeeds.
   * The Send button is disabled (isSubmitting = true) during broadcast
   * to prevent duplicate submissions.
   */
  fun confirmSend() {
    val currentState = _uiState.value
    if (currentState.state != SendState.Confirming) return
    val feeOptions = currentState.feeOptions ?: return
    val selectedFee = feeOptions.getOrNull(currentState.selectedFeeTierIndex) ?: return

    // Transition to Submitting — disables Send button
    _uiState.update { it.copy(state = SendState.Submitting) }

    viewModelScope.launch {
      try {
        val txHash = if (isBtc) {
          broadcastBtcTransaction(selectedFee)
        } else {
          broadcastEvmTransaction(selectedFee)
        }

        _uiState.update {
          it.copy(
            state = SendState.Success,
            txHash = txHash,
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            state = SendState.Error,
            error = "Transaction failed: ${e.message}",
          )
        }
      }
    }
  }

  /**
   * Builds, signs, and broadcasts a BTC transaction.
   *
   * Tech-spec Send transaction (BTC):
   * 1. Fetch unspents
   * 2. Select UTXOs
   * 3. Build transaction with fee and change
   * 4. Sign with BTC private key
   * 5. Broadcast via Bitpay
   *
   * @param selectedFee the selected fee tier
   * @return transaction hash
   */
  private suspend fun broadcastBtcTransaction(selectedFee: FeeTierOption): String {
    val keys = secureStorage.getPrivateKeys()
      ?: throw IllegalStateException("Private keys not found in secure storage")
    val btcWIF = keys.first

    val amountSat = BigDecimal(_uiState.value.amount)
      .multiply(SATOSHI_DIVISOR)
      .setScale(0, RoundingMode.FLOOR)
      .toLong()

    val utxos = btcManager.fetchUnspentOutputs(senderAddress)
    val selectedUtxos = btcManager.selectUtxos(utxos, amountSat)

    val numOutputs = 2 // recipient + change
    val txSize = btcManager.calculateTxSize(selectedUtxos.size, numOutputs)
    val feeSat = btcManager.calculateFee(selectedFee.feeRateSatPerKb, txSize)

    val totalInput = selectedUtxos.sumOf { it.valueSatoshis }
    val changeResult = btcManager.calculateChange(totalInput, amountSat, feeSat)

    val signedTxHex = btcManager.buildTransaction(
      utxos = selectedUtxos,
      recipientAddress = _uiState.value.recipientAddress,
      amountSatoshis = amountSat,
      feeSatoshis = changeResult.effectiveFeeSatoshis,
      changeAddress = senderAddress,
      privateKeyWIF = btcWIF,
    )

    return btcManager.broadcastTransaction(signedTxHex)
  }

  /**
   * Builds, signs, and broadcasts an EVM transaction.
   *
   * Tech-spec Send transaction (EVM):
   * 1. Get gas price and gas limit from selected fee tier
   * 2. Build, sign, and broadcast via EvmSendHelper
   *
   * @param selectedFee the selected fee tier
   * @return transaction hash
   */
  private suspend fun broadcastEvmTransaction(selectedFee: FeeTierOption): String {
    val keys = secureStorage.getPrivateKeys()
      ?: throw IllegalStateException("Private keys not found in secure storage")
    val ethHex = keys.second

    val helper = evmSendHelper
      ?: throw IllegalStateException("EvmSendHelper required for EVM transactions")

    val gasPrice = selectedFee.gasPriceWei
      ?: throw IllegalStateException("Gas price not available for EVM fee tier")
    val gasLimit = selectedFee.gasLimit
      ?: throw IllegalStateException("Gas limit not available for EVM fee tier")

    val amountWei = BigDecimal(_uiState.value.amount)
      .multiply(WEI_PER_ETH)
      .setScale(0, RoundingMode.FLOOR)
      .toBigInteger()

    val txHash = helper.sendTransaction(
      to = _uiState.value.recipientAddress,
      valueWei = amountWei,
      gasPrice = gasPrice,
      gasLimit = gasLimit,
      privateKeyHex = ethHex,
      chainId = chainId,
    ) ?: throw RuntimeException("Broadcast failed: RPC returned no tx hash")

    return txHash
  }
}
