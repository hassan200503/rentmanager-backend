package com.rentmanager.ai.api.dto;

import java.util.UUID;

/**
 * Manual AI trigger request (admin/debug only)
 */
public record AiGenerateInsightRequest(
        String eventType,
        UUID entityId,
        String contextHint
) {}