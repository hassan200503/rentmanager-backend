package com.rentmanager.modules.tenant.application.dto.event;

import java.time.Instant;
import java.util.UUID;

public abstract class BaseTenantEventDto {

    private UUID eventId;
    private UUID tenantId;        // actor tenant (SaaS isolation)
    private UUID targetTenantId;  // affected tenant
    private Instant occurredAt;

    protected BaseTenantEventDto(UUID tenantId, UUID targetTenantId) {
        this.eventId = UUID.randomUUID();
        this.tenantId = tenantId;
        this.targetTenantId = targetTenantId;
        this.occurredAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getTargetTenantId() {
        return targetTenantId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}