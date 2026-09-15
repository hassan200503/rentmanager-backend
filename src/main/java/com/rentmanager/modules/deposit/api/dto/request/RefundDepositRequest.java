package com.rentmanager.modules.deposit.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request to record a security deposit refund. The landlord's M-Pesa
 * transfer to the renter is done manually; this endpoint records the
 * outcome so both parties have a permanent, traceable record.
 *
 * deductionAmount  — portion kept for damages or unpaid bills; 0 for a
 *                    full refund. Must be strictly less than amountPaid
 *                    (use the forfeit endpoint for full deductions).
 * deductionReason  — required by the service when deductionAmount > 0.
 * refundReference  — the Safaricom confirmation code the renter received;
 *                    required by the service when refundAmount > 0.
 * refundRemarks    — optional landlord notes stored with the record.
 */
public record RefundDepositRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal deductionAmount,
        String deductionReason,
        String refundReference,
        String refundRemarks
) {}
