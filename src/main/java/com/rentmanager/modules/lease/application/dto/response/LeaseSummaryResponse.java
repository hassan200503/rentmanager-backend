package com.rentmanager.modules.lease.application.dto.response;

import com.rentmanager.modules.lease.application.dto.request.LeaseStatusDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeaseSummaryResponse(
        UUID id,
        String leaseNumber,
        LeaseStatusDTO status,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal rentAmount,
        UUID tenantProfileId,
        String tenantFullName,
        String tenantPhone,
        UUID propertyId,
        // Null if the property was deleted out from under an old lease — the
        // frontend must not assume every lease resolves to a live property.
        String propertyName,
        UUID unitId,
        // Unit's display label: its human-entered label if set, else its
        // unit number — same fallback UnitTable already uses on the frontend.
        String unitLabel
) {}