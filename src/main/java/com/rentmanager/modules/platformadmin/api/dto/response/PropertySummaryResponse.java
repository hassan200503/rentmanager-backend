package com.rentmanager.modules.platformadmin.api.dto.response;

import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;

import java.time.Instant;
import java.util.UUID;

/**
 * Row in {@code GET /api/v1/admin/properties}. Platform-wide property search
 * with landlord attribution. {@code unitsCount} is the total units in the
 * property; {@code occupiedUnitsCount} is the count of units with
 * {@code occupancyStatus = OCCUPIED}.
 */
public record PropertySummaryResponse(
        UUID id,
        String referenceCode,
        String name,
        PropertyStatus status,
        PropertyType propertyType,
        PremisesType premisesType,
        long unitsCount,
        long occupiedUnitsCount,
        Instant createdAt,
        UUID landlordId,
        String landlordName,
        String landlordSlug
) {}
