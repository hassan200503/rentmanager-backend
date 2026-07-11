package com.rentmanager.modules.rentledger.api.dto.request;

import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ResolveOverpaymentRefundRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal refundAmount,
        String externalReference,
        @NotNull RentTransactionSource source,
        LocalDateTime occurredAt
) {}