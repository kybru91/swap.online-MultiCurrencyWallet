package com.mcw.wallet.ui.send

import java.math.BigDecimal

/**
 * UI state for the Send Transaction screen.
 *
 * State machine: Idle -> Building -> Confirming -> Submitting -> Success | Error
 * - Idle: initial state, user entering address/amount
 * - Building: fee estimation in progress
 * - Confirming: fee options shown, user reviews and confirms
 * - Submitting: transaction being signed and broadcast (button disabled)
 * - Success: tx hash displayed
 * - Error: error message displayed, can retry
 */
data class SendUiState(
  val state: SendState = SendState.Idle,
  val recipientAddress: String = "",
  val amount: String = "",
  val addressValidation: AddressValidation? = null,
  val amountValidation: AmountValidation? = null,
  val feeOptions: List<FeeTierOption>? = null,
  val selectedFeeTierIndex: Int = 0,
  val txHash: String? = null,
  val error: String? = null,
) {
  /**
   * Whether the submit button should be disabled.
   * True when state is Submitting (duplicate submission prevention).
   */
  val isSubmitting: Boolean
    get() = state == SendState.Submitting
}

/**
 * Send transaction state machine states.
 *
 * Tech-spec: Idle -> Building -> Confirming -> Submitting -> Success/Error
 */
enum class SendState {
  /** Initial state: user entering address and amount */
  Idle,
  /** Fee estimation in progress */
  Building,
  /** Fee options shown, awaiting user confirmation + biometric */
  Confirming,
  /** Transaction being signed and broadcast; Send button disabled */
  Submitting,
  /** Transaction broadcast successfully; tx hash available */
  Success,
  /** Error occurred; can retry */
  Error,
}

/**
 * Address validation result.
 *
 * - Valid: address format is correct (BTC P2PKH or checksummed EVM)
 * - ChecksumWarning: EVM address is valid hex but not EIP-55 checksummed
 *   (user-spec: "Address checksum invalid, proceed anyway?")
 * - Invalid: address format is wrong for the selected currency
 */
sealed class AddressValidation {
  /** Address is valid */
  object Valid : AddressValidation()

  /** EVM address is valid but not EIP-55 checksummed (lowercase or wrong mixed case) */
  data class ChecksumWarning(
    val message: String = "Address checksum invalid, proceed anyway?"
  ) : AddressValidation()

  /** Address format is invalid */
  data class Invalid(val message: String) : AddressValidation()
}

/**
 * Amount validation result.
 *
 * - Valid: amount is > 0 and <= balance
 * - Invalid: amount is zero, negative, exceeds balance, or not a number
 */
sealed class AmountValidation {
  /** Amount is valid */
  object Valid : AmountValidation()

  /** Amount is invalid with reason */
  data class Invalid(val message: String) : AmountValidation()
}

/**
 * Fee tier option displayed in the fee selector.
 *
 * For BTC: calculated from sat/KB fee rates.
 * For EVM: calculated from gas price multipliers.
 *
 * @param label display name (e.g., "Fast", "Normal", "Slow")
 * @param estimatedTime estimated confirmation time (e.g., "~10 min")
 * @param feeAmount fee in native currency (e.g., "0.00003400 BTC")
 * @param feeNative fee as BigDecimal for calculations
 * @param feeRateSatPerKb BTC fee rate (only for BTC, 0 for EVM)
 * @param gasPriceWei EVM gas price (only for EVM, null for BTC)
 * @param gasLimit EVM gas limit (only for EVM, null for BTC)
 */
data class FeeTierOption(
  val label: String,
  val estimatedTime: String,
  val feeAmount: String,
  val feeNative: BigDecimal,
  val feeRateSatPerKb: Long = 0L,
  val gasPriceWei: java.math.BigInteger? = null,
  val gasLimit: java.math.BigInteger? = null,
)
