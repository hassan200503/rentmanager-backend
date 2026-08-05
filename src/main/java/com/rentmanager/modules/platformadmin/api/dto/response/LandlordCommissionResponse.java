package com.rentmanager.modules.platformadmin.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Commission state for one landlord. {@code source} is {@code OVERRIDE} when
 * a landlord-specific policy is active (set via the admin commission
 * endpoint) and {@code DEFAULT} when the platform-wide default applies.
 * Fields are null when no active policy exists at all.
 */
public record LandlordCommissionResponse(
        UUID landlordOrgId,
        BigDecimal ratePercent,
        String source,
        Instant effectiveFrom,
        Instant updatedAt
) {}