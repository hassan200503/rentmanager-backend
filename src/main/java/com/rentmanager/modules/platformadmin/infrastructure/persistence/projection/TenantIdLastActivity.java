package com.rentmanager.modules.platformadmin.infrastructure.persistence.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-model projection for the most recent activity timestamp
 * (last rent transaction) grouped by landlord (tenant) org id.
 */
public record TenantIdLastActivity(UUID tenantId, LocalDateTime lastActivityAt) {
}
