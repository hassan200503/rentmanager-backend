package com.rentmanager.contract.lease.response;

import com.rentmanager.contract.lease.dto.LeaseTypeDTO;
import com.rentmanager.contract.lease.dto.BillingCycleDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LeaseResponse(

        UUID id,
        String leaseNumber,

        UUID propertyId,
        UUID unitId,
        UUID tenantProfileId,

        LeaseTypeDTO leaseType,
        BillingCycleDTO billingCycle,

        LocalDate startDate,
        LocalDate endDate,

        BigDecimal rentAmount,
        BigDecimal securityDeposit,
        BigDecimal lateFeeAmount,

        Integer gracePeriodDays,
        boolean autoRenew,

        String status,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt

) {}