package com.rentmanager.modules.rentledger.api.dto.request;

import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecordRentTransactionRequest(
        @NotNull RentTransactionType type,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        String externalReference,
        @NotNull RentTransactionSource source,
        LocalDateTime occurredAt
) {}