package com.rentmanager.modules.deposit.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InitiateDepositRefundRequest(

        // The landlord's personal M-Pesa number — receives the STK push authorisation prompt.
        @NotBlank(message = "Landlord M-Pesa phone is required")
        String landlordPhone,

        // Amount to deduct before refunding (0 for a full refund).
        @NotNull(message = "Deduction amount is required (use 0 for a full refund)")
        @DecimalMin(value = "0", message = "Deduction cannot be negative")
        BigDecimal deductionAmount,

        // Required when deductionAmount > 0.
        String deductionReason,

        // Optional landlord notes attached to the refund record.
        String remarks
) {}
