package com.rentmanager.modules.platformadmin.api.dto.response;

import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Row in {@code GET /api/v1/admin/landlords}. Counts are scoped to the
 * landlord's own org id. Platform revenue model is subscription-only
 * (V89+): all rent settles directly into the landlord's own M-Pesa under
 * DIRECT collection mode, so {@code commissionAmount} is always zero in
 * production and is retained here for audit/migration purposes only.
 * {@code subscriptionStatus} drives the subscription lifecycle badge in the UI.
 */
public record LandlordSummaryResponse(
        UUID id,
        String name,
        String slug,
        String email,
        BillingMode billingMode,
        SubscriptionStatus subscriptionStatus,
        TenantStatus status,
        Instant createdAt,
        long propertiesCount,
        long unitsCount,
        long activeLeasesCount,
        long rentersCount,
        BigDecimal gmvAmount,
        BigDecimal commissionAmount,
        LocalDateTime lastActivityAt,
        BigDecimal effectiveCommissionRate
) {}