package com.rentmanager.modules.rentledger.api.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ApplyAdjustmentRequest(
        @NotNull BigDecimal delta,
        LocalDateTime occurredAt
) {}