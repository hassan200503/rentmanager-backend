package com.rentmanager.modules.lease.application.service;

import com.rentmanager.contract.common.PageResponse;
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
    // FIX (this session): Lease.create() registers LeaseCreatedEvent, but
    // nothing previously called pullDomainEvents()/publishAll() after the
    // save — confirmed by cross-referencing LeaseActionScheduler.activateOne(),
    // which DOES call eventPublisher.publishAll(lease.pullDomainEvents())
    // after its save, making this method's prior omission an inconsistency
    // rather than a deliberate design choice. Every lease created via this
    // path had its LeaseCreatedEvent silently discarded.
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

        Lease saved = leaseRepository.save(lease);
        eventPublisher.publishAll(saved.pullDomainEvents());

        return toResponse(saved);
    }

    // =========================================================
    // UPDATE
    // =========================================================
    // NOTE: updateContractTerms() registers no domain event today (verified
    // against Lease.java — the method body has no registerEvent() call), so
    // no publishAll() is added here. Nothing to fix, flagging only so this
    // isn't mistaken for an oversight later.
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
    /**
     * @Transactional added (earlier session): the ACTIVATE branch calls
     * LeaseActivationOrchestrator.onLeaseActivated(...), which posts the
     * lease's opening rent charge via RentLedgerApplicationService.
     * postCharge(). postCharge()'s own @Transactional uses Spring's default
     * REQUIRED propagation, so it joins this method's transaction.
     *
     * FIX (this session): every branch (APPROVE, AWAITING_DEPOSIT, ACTIVATE,
     * REJECT, TERMINATE, RENEW) registers its own domain event on the Lease
     * aggregate, but this method never pulled or published any of them —
     * confirmed by comparing against LeaseActionScheduler.activateOne(),
     * which does call eventPublisher.publishAll(lease.pullDomainEvents())
     * after its save. Added the same call here, after save, inside the
     * same transaction boundary, so this REST-triggered path has the same
     * event-publishing guarantee as the scheduler path instead of silently
     * discarding LeaseApprovedEvent / LeaseActivatedEvent / LeaseRejected
     * (via reject(), which registers none today — see note below) /
     * LeaseTerminatedEvent / LeaseRenewedEvent / LeaseCancelledEvent.
     *
     * NOTE: reject() in Lease.java registers no event currently (verified —
     * no registerEvent() call in that method body), so REJECT will publish
     * an empty list via this call; not a bug, just confirming the fix is
     * complete rather than partial.
     */
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
        }

        Lease saved = leaseRepository.save(lease);
        eventPublisher.publishAll(saved.pullDomainEvents());

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