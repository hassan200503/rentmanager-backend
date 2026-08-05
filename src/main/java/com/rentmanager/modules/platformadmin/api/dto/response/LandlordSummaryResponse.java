package com.rentmanager.modules.platformadmin.api.dto.response;

import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Row in {@code GET /api/v1/admin/landlords}. Counts are scoped to the
 * landlord's own org id. {@code gmvAmount} is gross lifetime collection,
 * {@code commissionAmount} the platform's retained commission on it, and
 * {@code effectiveCommissionRate} the active rate currently applied (override
 * if one exists, otherwise the platform default; null when no policy exists).
 */
public record LandlordSummaryResponse(
        UUID id,
        String name,
        String slug,
        String email,
        BillingMode billingMode,
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