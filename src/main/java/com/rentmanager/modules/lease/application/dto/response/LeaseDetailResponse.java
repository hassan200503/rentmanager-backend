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
        String terminationReason,

        /** Actions a landlord may take on this lease now (see LeaseActionPolicy). Clients render controls from this. */
        java.util.List<com.rentmanager.modules.lease.application.dto.request.LeaseActionType> allowedActions
) {
    /** Pre-allowedActions arity, kept for existing callers; reports no actions. */
    public LeaseDetailResponse(
            UUID id, String leaseNumber, UUID tenantId, UUID propertyId, UUID unitId,
            LeaseTypeDTO leaseType, BillingCycleDTO billingCycle, LocalDate startDate, LocalDate endDate,
            BigDecimal rentAmount, BigDecimal securityDeposit, LeaseStatusDTO status, Integer gracePeriodDays,
            Boolean autoRenew, LocalDate createdAt, LocalDate updatedAt, Long version,
            String tenantFullName, String tenantPhone,
            LocalDateTime signedAt, LocalDateTime activatedAt, LocalDateTime terminatedAt, LocalDateTime expiredAt,
            LocalDateTime renewedAt, LocalDateTime cancelledAt, TerminationTypeDTO terminationType, String terminationReason
    ) {
        this(id, leaseNumber, tenantId, propertyId, unitId, leaseType, billingCycle, startDate, endDate,
                rentAmount, securityDeposit, status, gracePeriodDays, autoRenew, createdAt, updatedAt, version,
                tenantFullName, tenantPhone, signedAt, activatedAt, terminatedAt, expiredAt, renewedAt, cancelledAt,
                terminationType, terminationReason, java.util.List.of());
    }
}