package com.rentmanager.modules.lease.application.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateLeaseRequest(

        @NotNull(message = "startDate is required")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        LocalDate endDate,

        @NotNull(message = "rentAmount is required")
        @Positive(message = "rentAmount must be > 0")
        BigDecimal rentAmount,

        @NotNull(message = "securityDeposit is required")
        @PositiveOrZero(message = "securityDeposit must be >= 0")
        BigDecimal securityDeposit,

        @NotNull(message = "lateFeeAmount is required")
        @PositiveOrZero(message = "lateFeeAmount must be >= 0")
        BigDecimal lateFeeAmount,

        @NotNull(message = "gracePeriodDays is required")
        @Min(value = 0, message = "gracePeriodDays cannot be negative")
        Integer gracePeriodDays,

        @NotNull(message = "autoRenew is required")
        Boolean autoRenew
) {}