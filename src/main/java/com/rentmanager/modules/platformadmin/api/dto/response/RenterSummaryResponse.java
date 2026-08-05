package com.rentmanager.modules.platformadmin.api.dto.response;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;

import java.util.UUID;

/**
 * Row in {@code GET /api/v1/admin/renters}. Platform-wide renter search
 * with landlord attribution and active lease status. {@code activeLeaseId}
 * and {@code activeLeaseStatus} are populated only if the renter has an
 * active lease; otherwise both are {@code null}.
 */
public record RenterSummaryResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String nationalId,
        UUID landlordId,
        String landlordName,
        String landlordSlug,
        UUID activeLeaseId,
        LeaseStatus activeLeaseStatus
) {}
