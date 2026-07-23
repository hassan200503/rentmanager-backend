package com.rentmanager.modules.lease.application.dto.response;

import com.rentmanager.modules.lease.application.dto.request.BillingCycleDTO;

import com.rentmanager.modules.lease.application.dto.request.LeaseStatusDTO;
import com.rentmanager.modules.lease.application.dto.request.LeaseTypeDTO;
import com.rentmanager.modules.lease.application.dto.request.TerminationTypeDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        Long version,
        String tenantFullName,
        String tenantPhone,

        // NEW this session: lifecycle metadata, previously persisted
        // correctly (as of the V34/LeaseEntity/LeaseMapper fix) but never
        // exposed to any response DTO. Full LocalDateTime precision kept
        // deliberately, unlike createdAt/updatedAt above which are
        // (pre-existing, unrelated) truncated to LocalDate.
        LocalDateTime signedAt,
        LocalDateTime activatedAt,
        LocalDateTime terminatedAt,
        LocalDateTime expiredAt,
        LocalDateTime renewedAt,
        LocalDateTime cancelledAt,
        TerminationTypeDTO terminationType,
        String terminationReason
) {}