package com.rentmanager.modules.lease.application.service;

import com.rentmanager.contract.common.PageResponse;
import com.rentmanager.modules.lease.application.dto.request.*;
import com.rentmanager.modules.lease.application.dto.response.*;
import com.rentmanager.modules.lease.application.dto.request.CreateLeaseRequest;
import com.rentmanager.modules.lease.application.dto.request.LeaseActionRequest;
import com.rentmanager.modules.lease.application.dto.request.UpdateLeaseRequest;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowEngine;
import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.shared.security.context.TenantContext;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class LeaseApplicationService {

    private final LeaseRepository leaseRepository;
    private final LeaseWorkflowEngine workflowEngine;
    private final TenantProfileRepository tenantProfileRepository;

    public LeaseApplicationService(
            LeaseRepository leaseRepository,
            LeaseWorkflowEngine workflowEngine,
            TenantProfileRepository tenantProfileRepository
    ) {
        this.leaseRepository = leaseRepository;
        this.workflowEngine = workflowEngine;
        this.tenantProfileRepository = tenantProfileRepository;
    }

    // =========================================================
    // CREATE
    // =========================================================
    public LeaseResponse create(@Valid @org.checkerframework.checker.nullness.qual.MonotonicNonNull CreateLeaseRequest request) {

        UUID tenantId = TenantContext.getTenantId(); // ✅ HERE (MANDATORY)

        // SECURITY FIX (this session): tenantProfileId is client-supplied
        // and was previously passed straight into Lease.create() with no
        // check of any kind — not even existence. Any authenticated
        // landlord could bind another landlord's renter profile (real PII:
        // name, email, phone, national ID) to a lease on their own
        // property. This loads the profile and confirms it belongs to the
        // calling landlord before proceeding. Do not remove this check or
        // replace it with an existence-only check. (NOTE: the reservation-
        // fulfillment saga's own equivalent check, CreateLeaseValidator's
        // existsById()-only usage, is now moot — that entire dead code
        // path, including CreateLeaseValidator itself, has been removed
        // from the codebase; see project handoff addendum for the
        // confirmed-dead-code deletion record.)
        TenantProfile tenantProfile = tenantProfileRepository.findById(request.tenantProfileId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Tenant profile not found: " + request.tenantProfileId(),
                                ErrorCode.LEASE_PROFILE_NOT_FOUND
                        )
                );

        if (!tenantProfile.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException(
                    "Cross-tenant access denied for tenant profile: " + request.tenantProfileId()
            );
        }

        Lease lease = Lease.create(
                tenantId,
                request.propertyId(),
                request.unitId(),
                request.tenantProfileId(),
                request.leaseNumber(),
                LeaseType.valueOf(request.leaseType().name()),
                BillingCycle.valueOf(request.billingCycle().name()),
                request.startDate(),
                request.endDate(),
                request.rentAmount(),
                request.securityDeposit(),
                request.lateFeeAmount(),
                request.gracePeriodDays(),
                request.autoRenew() != null ? request.autoRenew() : false
        );

        return toResponse(leaseRepository.save(lease));
    }

    // =========================================================
    // UPDATE
    public LeaseResponse update(UUID leaseId, @Valid @org.checkerframework.checker.nullness.qual.MonotonicNonNull UpdateLeaseRequest request) {

        Lease lease = load(leaseId, TenantContext.getTenantId());

        lease.updateContractTerms(
                request.startDate(),
                request.endDate(),
                request.rentAmount(),
                request.securityDeposit(),
                request.lateFeeAmount(),
                request.gracePeriodDays(),
                request.autoRenew() != null ? request.autoRenew() : false
        );

        return toResponse(leaseRepository.save(lease));
    }

    // =========================================================
    public LeaseDetailResponse getById(UUID leaseId) {
        UUID tenantId = TenantContext.getTenantId();
        return toDetailResponse(load(leaseId, tenantId));
    }
    // =========================================================
    public PageResponse<LeaseSummaryResponse> search(LeaseSearchRequest request) {

        UUID tenantId = TenantContext.getTenantId();

        var result = leaseRepository.findAllByTenant(tenantId)


                .stream()
                .map(this::toSummary)
                .toList();

        return new PageResponse<>(
                result,
                request.page(),
                request.size(),
                result.size(),
                1,
                true,
                true
        );
    }

    // =========================================================
    public LeaseActionResponse executeAction(UUID leaseId, @Valid @org.checkerframework.checker.nullness.qual.MonotonicNonNull LeaseActionRequest request) {

        UUID tenantId = TenantContext.getTenantId();

        Lease lease = load(leaseId, tenantId);

        LeaseStatusDTO previous = map(lease.getStatus());

        switch (request.getAction()) {

            case APPROVE -> workflowEngine.approve(lease);

            // FIX (this session): LeaseActionType already defined
            // AWAITING_DEPOSIT, and LeaseWorkflowEngine already implemented
            // markAwaitingDeposit(Lease), but this switch never called it.
            // That left AWAITING_DEPOSIT completely unreachable via the API
            // even though Lease.activate() requires the aggregate to be in
            // that state first (see LeaseWorkflowValidator.validateActivation) —
            // meaning ACTIVATE could never legitimately succeed on any lease
            // that went through APPROVE first. Do not remove this case.
            case AWAITING_DEPOSIT -> workflowEngine.markAwaitingDeposit(lease);

            case ACTIVATE -> workflowEngine.activate(lease);

            case REJECT -> workflowEngine.reject(
                    lease,
                    request.getReason()
            );

            case TERMINATE -> workflowEngine.terminate(
                    lease,
                    request.getTerminationType(),   // ✅ FIXED (no string parsing)
                    request.getReason()
            );

            case RENEW -> workflowEngine.renew(
                    lease,
                    request.getActionDate(),
                    calculateRenewalEndDate(lease, request)
            );
        }

        Lease saved = leaseRepository.save(lease);

        return new LeaseActionResponse(
                saved.getId(),
                previous,
                map(saved.getStatus()),
                request.getAction().name()
        );
    }


    // =========================================================
    public void delete(UUID leaseId) {
        UUID tenantId = TenantContext.getTenantId();

        // SECURITY (Addendum 3 §1.7 — confirmed, this session):
        // load() enforces tenant ownership (throws AccessDeniedException on
        // mismatch) before delete proceeds. Do NOT replace this with a direct
        // leaseRepository.delete(leaseId) call — that previously allowed any
        // authenticated user, from any tenant, to hard-delete any tenant's
        // lease by ID alone. No header trick or auth bypass was even
        // required; this was the single most severe finding of the session.
        Lease lease = load(leaseId, tenantId);

        leaseRepository.delete(lease.getId());
    }




    private Lease load(UUID leaseId, UUID tenantId) {

        Lease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Lease not found: " + leaseId,
                                ErrorCode.LEASE_NOT_FOUND
                        )
                );

        if (!lease.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException(
                    "Cross-tenant access denied for lease: " + leaseId
            );
        }

        return lease;
    }


    // =========================================================
    private LeaseResponse toResponse(Lease lease) {
        return new LeaseResponse(
                lease.getId(),
                lease.getLeaseNumber(),
                lease.getPropertyId(),
                lease.getUnitId(),
                lease.getTenantProfileId(),
                lease.getLeaseType(),
                map(lease.getBillingCycle()),
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getRentAmount(),
                lease.getSecurityDeposit(),
                lease.getLateFeeAmount(),
                lease.getGracePeriodDays(),
                lease.isAutoRenew(),
                lease.getStatus().name(),
                toOffset(lease.getCreatedAt()),
                toOffset(lease.getUpdatedAt())
        );
    }

    // =========================================================
    private LeaseDetailResponse toDetailResponse(Lease lease) {
        return new LeaseDetailResponse(
                lease.getId(),
                lease.getLeaseNumber(),
                lease.getTenantId(),
                lease.getPropertyId(),
                lease.getUnitId(),
                map(lease.getLeaseType()),
                map(lease.getBillingCycle()),
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getRentAmount(),
                lease.getSecurityDeposit(),
                map(lease.getStatus()),
                lease.getGracePeriodDays(),
                lease.isAutoRenew(),
                lease.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                lease.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate(),


                lease.getVersion()
        );
    }

    // =========================================================
    private LeaseSummaryResponse toSummary(Lease lease) {
        return new LeaseSummaryResponse(
                lease.getId(),
                lease.getLeaseNumber(),
                map(lease.getStatus()),
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getRentAmount()
        );
    }

    // =========================================================
    private LeaseTypeDTO map(LeaseType type) {
        return LeaseTypeDTO.valueOf(type.name());
    }

    private BillingCycleDTO map(BillingCycle cycle) {
        return BillingCycleDTO.valueOf(cycle.name());
    }

    private LeaseStatusDTO map(LeaseStatus status) {
        return LeaseStatusDTO.valueOf(status.name());
    }


    private OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }



    private LocalDate calculateRenewalEndDate(Lease lease, LeaseActionRequest request) {

        if (request.getActionDate() == null) {
            throw new IllegalArgumentException("Action date is required for renewal");
        }

        return request.getActionDate().plusMonths(12);
    }
}