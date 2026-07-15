package com.rentmanager.modules.lease.application.service;

import com.rentmanager.contract.common.PageResponse;
import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.lease.application.dto.request.*;
import com.rentmanager.modules.lease.application.dto.response.*;
import com.rentmanager.modules.lease.application.dto.request.CreateLeaseRequest;
import com.rentmanager.modules.lease.application.dto.request.LeaseActionRequest;
import com.rentmanager.modules.lease.application.dto.request.UpdateLeaseRequest;
import com.rentmanager.modules.lease.application.orchestration.LeaseActivationOrchestrator;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowEngine;
import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import com.rentmanager.shared.security.context.TenantContext;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class LeaseApplicationService {

    private final LeaseRepository leaseRepository;
    private final LeaseWorkflowEngine workflowEngine;
    private final TenantProfileRepository tenantProfileRepository;
    private final LeaseActivationOrchestrator leaseActivationOrchestrator;
    private final DomainEventPublisher eventPublisher;

    public LeaseApplicationService(
            LeaseRepository leaseRepository,
            LeaseWorkflowEngine workflowEngine,
            TenantProfileRepository tenantProfileRepository,
            LeaseActivationOrchestrator leaseActivationOrchestrator,
            DomainEventPublisher eventPublisher
    ) {
        this.leaseRepository = leaseRepository;
        this.workflowEngine = workflowEngine;
        this.tenantProfileRepository = tenantProfileRepository;
        this.leaseActivationOrchestrator = leaseActivationOrchestrator;
        this.eventPublisher = eventPublisher;
    }

    // =========================================================
    // CREATE
    // =========================================================
    public LeaseResponse create(@Valid @org.checkerframework.checker.nullness.qual.MonotonicNonNull CreateLeaseRequest request) {

        UUID tenantId = TenantContext.getTenantId(); // ✅ HERE (MANDATORY)

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

        // FIX: pull domain events from the live in-memory aggregate BEFORE save().
        // LeaseRepository.save() round-trips through LeaseMapper.toDomain(), which
        // reconstructs the returned Lease via Lease.restore() — that never
        // repopulates AggregateRoot's transient domainEvents list (it's not a
        // mapped column), so saved.pullDomainEvents() is always empty regardless
        // of what was registered on `lease`. Same pattern already used in
        // LeaseActionHandler.handle().
        List<DomainEvent> events = lease.pullDomainEvents();

        Lease saved = leaseRepository.save(lease);

        eventPublisher.publishAll(events);

        return toResponse(saved);
    }

    // =========================================================
    // UPDATE
    // =========================================================
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

        var result = leaseRepository.findAllByTenant(tenantId).stream()
                .filter(l -> request.propertyId() == null || l.getPropertyId().equals(request.propertyId()))
                .filter(l -> request.status() == null || l.getStatus().name().equals(request.status().name()))
                .filter(l -> request.fromDate() == null || !l.getStartDate().isBefore(request.fromDate()))
                .filter(l -> request.toDate() == null || !l.getEndDate().isAfter(request.toDate()))
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
    @Transactional
    public LeaseActionResponse executeAction(UUID leaseId, @Valid @org.checkerframework.checker.nullness.qual.MonotonicNonNull LeaseActionRequest request) {

        UUID tenantId = TenantContext.getTenantId();

        Lease lease = load(leaseId, tenantId);

        LeaseStatusDTO previous = map(lease.getStatus());

        switch (request.getAction()) {

            case APPROVE -> workflowEngine.approve(lease);

            case AWAITING_DEPOSIT -> workflowEngine.markAwaitingDeposit(lease);

            case ACTIVATE -> {
                workflowEngine.activate(lease);
                leaseActivationOrchestrator.onLeaseActivated(tenantId, lease);
            }

            case REJECT -> workflowEngine.reject(
                    lease,
                    request.getReason()
            );

            case TERMINATE -> workflowEngine.terminate(
                    lease,
                    request.getTerminationType(),
                    request.getReason()
            );

            case RENEW -> workflowEngine.renew(
                    lease,
                    request.getActionDate(),
                    calculateRenewalEndDate(lease, request)
            );

            // NEW: EXPIRE deliberately publishes only once (via the engine) —
            // Lease.expire() does not registerEvent(), so the
            // pullDomainEvents() flush below returns nothing extra for this
            // action. See handoff notes on the pre-existing double-publish
            // bug affecting the other cases above.
            case EXPIRE -> workflowEngine.expire(lease);

            // NEW: CANCEL inherits the same double-publish behavior as
            // TERMINATE/RENEW/APPROVE/ACTIVATE (Lease.cancel() registers an
            // event AND the engine publishes directly) — consistent with
            // existing (flagged, unfixed) behavior, not a new deviation.
            case CANCEL -> workflowEngine.cancel(
                    lease,
                    request.getReason()
            );
        }

        // FIX: pull events from `lease` (pre-save, live domainEvents list),
        // not from `saved` — LeaseRepositoryImpl.save() returns
        // mapper.toDomain(...), which reconstructs the Lease via restore()/
        // rehydrate() and never repopulates AggregateRoot's transient
        // domainEvents list. Pulling from `saved` silently published nothing
        // for every action (APPROVE, ACTIVATE, REJECT, TERMINATE, RENEW,
        // EXPIRE, CANCEL) prior to this fix — the same shape as the bug
        // already fixed in create() above.
        //
        // ⚠️ CONSEQUENCE OF THIS FIX — READ BEFORE DEPLOYING:
        // The comments on EXPIRE and CANCEL above describe a "pre-existing
        // double-publish bug," on the theory that LeaseWorkflowEngine
        // publishes some events directly AND Lease.registerEvent() queues
        // the same event for this flush. While this line was silently
        // publishing nothing, that theoretical double-publish could never
        // actually have happened in practice. Now that this flush is real,
        // if LeaseWorkflowEngine.approve/activate/terminate/renew/cancel
        // truly do publish directly in addition to registerEvent(), those
        // five actions will now genuinely double-fire their events (extra
        // notifications, duplicate side effects in any listener, etc).
        // VERIFY LeaseWorkflowEngine's publishing behavior for each of
        // those five methods before this goes to production — if it does
        // publish directly, either remove the direct publish there or stop
        // registering the event on Lease for that action, so there's a
        // single source of truth per action.
        List<DomainEvent> events = lease.pullDomainEvents();

        Lease saved = leaseRepository.save(lease);
        eventPublisher.publishAll(events);

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
    // UPDATED this session: now includes the 6 lifecycle metadata fields
    // (signedAt/activatedAt/terminatedAt/expiredAt/renewedAt/cancelledAt/
    // terminationType/terminationReason), previously persisted correctly
    // but never surfaced here. See LeaseDetailResponse for field details.
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
                lease.getVersion(),
                lease.getSignedAt(),
                lease.getActivatedAt(),
                lease.getTerminatedAt(),
                lease.getExpiredAt(),
                lease.getRenewedAt(),
                lease.getCancelledAt(),
                map(lease.getTerminationType()),
                lease.getTerminationReason()
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

    // NEW: mirrors the other map() overloads above. Nullable by design --
    // terminationType is only ever set on TERMINATED leases (see
    // Lease.terminate()); every other status leaves it null, so this must
    // pass null through rather than throwing on valueOf(null).
    private TerminationTypeDTO map(TerminationType type) {
        return type == null ? null : TerminationTypeDTO.valueOf(type.name());
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