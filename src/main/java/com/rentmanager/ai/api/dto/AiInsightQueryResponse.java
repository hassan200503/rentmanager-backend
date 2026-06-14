package com.rentmanager.ai.api.dto;

public record AiInsightQueryResponse(
        String type,
        String summary,
        String severity
) {}