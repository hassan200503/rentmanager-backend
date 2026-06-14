package com.rentmanager.modules.lease.application.dto.response;

import com.rentmanager.modules.lease.application.dto.request.BillingCycleDTO;


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

        com.rentmanager.modules.lease.domain.enums.LeaseType leaseType,
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

) {
}