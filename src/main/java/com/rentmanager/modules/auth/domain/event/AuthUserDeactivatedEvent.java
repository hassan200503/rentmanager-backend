package com.rentmanager.modules.auth.domain.event;

import java.time.Instant;
import java.util.UUID;

public record AuthUserDeactivatedEvent(
        UUID userId,
        UUID tenantId,
        Instant occurredAt
) {
}