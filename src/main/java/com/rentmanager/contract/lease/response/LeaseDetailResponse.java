package com.rentmanager.contract.lease.response;

import com.rentmanager.contract.lease.dto.BillingCycleDTO;
import com.rentmanager.contract.lease.dto.LeaseStatusDTO;
import com.rentmanager.contract.lease.dto.LeaseTypeDTO;

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