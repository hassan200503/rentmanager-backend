package com.rentmanager.modules.rentledger.api.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record ResolveOverpaymentCreditRequest(
        @NotNull UUID targetLedgerEntryId,
        LocalDateTime occurredAt
) {}