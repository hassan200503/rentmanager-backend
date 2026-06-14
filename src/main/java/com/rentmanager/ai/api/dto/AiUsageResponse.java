package com.rentmanager.ai.api.dto;

import java.time.Instant;

public record AiUsageResponse(
        String tenantId,
        long totalRequests,
        long totalTokens,
        double estimatedCost,
        Instant periodStart,
        Instant periodEnd
) {
    public static AiUsageResponse from(Object usage) {
        return new AiUsageResponse(
                "TENANT",
                0,
                0,
                0.0,
                Instant.now(),
                Instant.now()
        );
    }
}