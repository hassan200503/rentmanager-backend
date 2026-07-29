package com.rentmanager.modules.rentledger.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SetCommissionRateRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal ratePercent
) {}
