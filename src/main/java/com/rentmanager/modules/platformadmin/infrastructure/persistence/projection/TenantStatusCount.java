package com.rentmanager.modules.platformadmin.infrastructure.persistence.projection;

import com.rentmanager.modules.tenant.domain.enums.TenantStatus;

/**
 * Read-model projection for the platform admin overview:
 * tenant counts grouped by status.
 */
public record TenantStatusCount(TenantStatus status, long count) {
}
