package com.rentmanager.ai.infrastructure.llm;

import java.time.Instant;

public record LlmResponse(

        String content,

        String modelUsed,

        long latencyMs,

        Instant createdAt,

        boolean success,

        String error
) {

    public static LlmResponse success(
            String content,
            String modelUsed,
            long latencyMs
    ) {
        return new LlmResponse(
                content,
                modelUsed,
                latencyMs,
                Instant.now(),
                true,
                null
        );
    }

    public static LlmResponse failure(String error, String modelUsed) {
        return new LlmResponse(
                null,
                modelUsed,
                0,
                Instant.now(),
                false,
                error
        );
    }
}