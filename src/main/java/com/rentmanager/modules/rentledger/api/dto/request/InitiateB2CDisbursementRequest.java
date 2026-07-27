package com.rentmanager.modules.rentledger.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record InitiateB2CDisbursementRequest(

        @NotNull
        UUID leaseId,

        UUID ledgerEntryId,

        @NotNull @DecimalMin("0.01")
        BigDecimal amount,

        @NotBlank @Pattern(regexp = "^\\+2547\\d{8}$")
        String recipientPhone,

        @NotBlank
        String recipientName,

        String commandId,

        String remarks
) {}