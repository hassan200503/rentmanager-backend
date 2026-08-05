package com.rentmanager.modules.platformadmin.infrastructure.persistence.projection;

import java.util.UUID;

/**
 * Read-model projection for counts grouped by landlord (tenant) org id.
 */
public record TenantIdCount(UUID tenantId, long count) {
}
