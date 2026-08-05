package com.rentmanager.modules.platformadmin.api.dto.response;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full property view for {@code GET /api/v1/admin/properties/{id}}: property
 * details, units, landlord info, active and past leases. Used by admin
 * property detail page.
 */
public record PropertyDetailResponse(
        UUID id,
        String referenceCode,
        String name,
        String description,
        PropertyStatus status,
        PropertyType propertyType,
        PremisesType premisesType,
        String premisesTypeOverrideReason,
        OccupancyStatus occupancyStatus,
        Instant createdAt,
        Instant updatedAt,
        AddressInfo address,
        LandlordInfo landlord,
        long totalUnits,
        long occupiedUnits,
        List<UnitSummary> units,
        List<LeaseSummary> activeLeases,
        List<LeaseSummary> pastLeases
) {

    public record AddressInfo(
            String street,
            String city,
            String state,
            String postalCode,
            String country
    ) {}

    public record LandlordInfo(
            UUID id,
            String name,
            String slug,
            String email,
            TenantStatus status,
            BillingMode billingMode
    ) {}

    public record UnitSummary(
            UUID id,
            String unitNumber,
            String label,
            UnitStatus status,
            UnitOccupancyStatus occupancyStatus,
            BigDecimal rentAmount,
            BigDecimal depositAmount,
            String floor
    ) {}

    public record LeaseSummary(
            UUID id,
            String leaseNumber,
            LeaseStatus status,
            UUID unitId,
            String unitNumber,
            UUID tenantProfileId,
            String renterName,
            String renterEmail,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal rentAmount,
            Instant createdAt
    ) {}
}
