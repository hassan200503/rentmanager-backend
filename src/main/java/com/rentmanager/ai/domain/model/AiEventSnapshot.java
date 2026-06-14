package com.rentmanager.ai.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable tenant-safe snapshot sent to LLM.
 * NEVER contains raw entities directly.
 */
public record AiEventSnapshot(
        UUID tenantId,
        String eventType,
        UUID entityId,
        Map<String, Object> payload,
        Instant timestamp
) {}