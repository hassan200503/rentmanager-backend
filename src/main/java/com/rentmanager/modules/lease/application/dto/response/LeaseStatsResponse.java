package com.rentmanager.modules.lease.application.dto.response;

import java.math.BigDecimal;

/**
 * Portfolio-wide stat-card figures for the Tenants page. Deliberately not
 * paginated or filtered — see {@code LeaseController#stats} for why.
 */
public record LeaseStatsResponse(
        long totalTenants,
        long activeCount,
        long expiringSoonCount,
        BigDecimal monthlyRent
) {
}
