package com.mcw.feature.dappbrowser

/**
 * Gas limit validation per tech-spec Decision 9:
 * - gasLimit <= 1,000,000 -> OK
 * - gasLimit > 1,000,000 and <= 15,000,000 -> WARNING (show in confirmation dialog)
 * - gasLimit > 15,000,000 -> REJECT (request denied with error)
 */
object GasValidator {

  /** Gas limit above which a warning is shown in the confirmation dialog */
  private const val WARNING_THRESHOLD = 1_000_000L

  /** Gas limit above which the request is rejected entirely */
  private const val REJECT_THRESHOLD = 15_000_000L

  /**
   * Validate a gas limit value.
   *
   * @param gasLimit the gas limit from the transaction request
   * @return [GasValidationResult] indicating whether to proceed, warn, or reject
   */
  fun validate(gasLimit: Long): GasValidationResult {
    return when {
      gasLimit > REJECT_THRESHOLD -> GasValidationResult.REJECT
      gasLimit > WARNING_THRESHOLD -> GasValidationResult.WARNING
      else -> GasValidationResult.OK
    }
  }
}

/**
 * Result of gas limit validation.
 */
enum class GasValidationResult {
  /** Gas limit is within normal range */
  OK,
  /** Gas limit is high — show warning in confirmation dialog */
  WARNING,
  /** Gas limit exceeds maximum — reject the request with error */
  REJECT,
}
