package com.rentmanager.modules.rentledger.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record RentTransactionSummaryResponse(
        UUID id,
        UUID ledgerEntryId,
        UUID leaseId,
        String type,
        BigDecimal amount,
        String externalReference,
        String source,
        String recordedBy,
        LocalDateTime occurredAt,
        String tenantFullName,
        String tenantPhone,
        String leaseNumber,
        String leaseStatus
) {}