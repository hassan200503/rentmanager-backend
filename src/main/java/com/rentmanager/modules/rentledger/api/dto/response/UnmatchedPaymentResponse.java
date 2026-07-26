package com.rentmanager.modules.rentledger.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UnmatchedPaymentResponse(
        UUID id,
        String transactionId,
        BigDecimal amount,
        String phoneNumber,
        String accountReference,
        LocalDateTime occurredAt,
        String matchConfidence,
        List<SuggestedUnit> suggestedUnits
) {
    public record SuggestedUnit(
            UUID unitId,
            String unitNumber,
            String tenantName,
            BigDecimal rentAmount,
            String matchReason
    ) {}
}
