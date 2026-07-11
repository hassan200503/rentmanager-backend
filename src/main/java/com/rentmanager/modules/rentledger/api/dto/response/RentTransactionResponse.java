package com.rentmanager.modules.rentledger.api.dto.response;

import com.rentmanager.modules.rentledger.domain.model.RentTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record RentTransactionResponse(
        UUID id,
        UUID ledgerEntryId,
        UUID leaseId,
        String type,
        BigDecimal amount,
        String externalReference,
        String source,
        String recordedBy,
        LocalDateTime occurredAt,
        Instant createdAt,
        Long version
) {
    public static RentTransactionResponse from(RentTransaction transaction) {
        return new RentTransactionResponse(
                transaction.getId(),
                transaction.getLedgerEntryId(),
                transaction.getLeaseId(),
                transaction.getType().name(),
                transaction.getAmount(),
                transaction.getExternalReference(),
                transaction.getSource().name(),
                transaction.getRecordedBy(),
                transaction.getOccurredAt(),
                transaction.getCreatedAt(),
                transaction.getVersion()
        );
    }
}