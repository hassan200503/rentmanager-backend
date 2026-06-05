package com.rentmanager.modules.auth.domain.event;

import java.time.Instant;
import java.util.UUID;

public record AuthUserCreatedEvent(
        UUID userId,
        UUID tenantId,
        String email,
        Instant occurredAt
) {
}