package com.rentmanager.modules.lease.application.dto.response;

import com.rentmanager.modules.lease.application.dto.request.BillingCycleDTO;

import com.rentmanager.modules.lease.application.dto.request.LeaseStatusDTO;
import com.rentmanager.modules.lease.application.dto.request.LeaseTypeDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeaseDetailResponse(
        UUID id,
        String leaseNumber,
        UUID tenantId,
        UUID propertyId,
        UUID unitId,
        LeaseTypeDTO leaseType,
        BillingCycleDTO billingCycle,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal rentAmount,
        BigDecimal securityDeposit,
        LeaseStatusDTO status,
        Integer gracePeriodDays,
        Boolean autoRenew,
        LocalDate createdAt,
        LocalDate updatedAt,
        Long version
) {}