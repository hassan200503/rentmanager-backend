package com.rentmanager.modules.deposit.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RefundDepositRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal refundAmount
) {}
