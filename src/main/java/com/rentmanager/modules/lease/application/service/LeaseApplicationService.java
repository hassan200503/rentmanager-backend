package com.rentmanager.modules.lease.application.service;

import com.rentmanager.contract.common.PageResponse;
import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.deposit.domain.enums.DepositStatus;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
import com.rentmanager.modules.lease.application.dto.request.*;
import com.rentmanager.modules.lease.application.dto.response.*;
import com.rentmanager.modules.lease.application.dto.request.CreateLeaseRequest;
import com.rentmanager.modules.lease.application.dto.request.LeaseActionRequest;
import com.rentmanager.modules.lease.application.dto.request.UpdateLeaseRequest;
import com.rentmanager.modules.lease.application.orchestration.LeaseActivationOrchestrator;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowEngine;
import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.security.context.TenantContext;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class LeaseApplicationService {

    private final LeaseRepository leaseRepository;
    private final LeaseWorkflowEngine workflowEngine;
    private final TenantProfileRepository tenantProfileRepository;
    private final PropertyRepository propertyRepository;
    private final UnitRepository unitRepository;
    private final LeaseActivationOrchestrator leaseActivationOrchestrator;
    private final DomainEventPublisher eventPublisher;
    private final DepositRepository depositRepository;

    public LeaseApplicationService(
            LeaseRepository leaseRepository,
            LeaseWorkflowEngine workflowEngine,
            TenantProfileRepository tenantProfileRepository,
            PropertyRepository propertyRepository,
            UnitRepository unitRepository,
            LeaseActivationOrchestrator leaseActivationOrchestrator,
            DomainEventPublisher eventPublisher,
            DepositRepository depositRepository
    ) {
        this.leaseRepository = leaseRepository;
        this.workflowEngine = workflowEngine;
        this.tenantProfileRepository = tenantProfileRepository;
        this.propertyRepository = propertyRepository;
        this.unitRepository = unitRepository;
        this.leaseActivationOrchestrator = leaseActivationOrchestrator;
        this.eventPublisher = eventPublisher;
        this.depositRepository = depositRepository;
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
        Lease lease = load(leaseId, tenantId);
        TenantProfile profile = lease.getTenantProfileId() != null
                ? tenantProfileRepository.findById(lease.getTenantProfileId()).orElse(null)
                : null;
        return toDetailResponse(lease, profile);
    }
    // =========================================================



    public PageResponse<LeaseSummaryResponse> search(LeaseSearchRequest request) {

        UUID tenantId = TenantContext.getTenantId();

        LeaseStatus statusFilter = request.status() != null ? LeaseStatus.valueOf(request.status().name()) : null;

        String keyword = request.keyword();
        List<UUID> matchingProfileIds = (keyword != null && !keyword.isBlank())
                ? tenantProfileRepository.searchByNameOrPhone(tenantId, keyword).stream()
                        .map(TenantProfile::getId)
                        .toList()
                : List.of();

        Page<Lease> leasePage = leaseRepository.search(
                tenantId,
                request.propertyId(),
                statusFilter,
                request.fromDate(),
                request.toDate(),
                keyword,
                matchingProfileIds,
                PageRequest.of(request.page(), request.size())
        );

        List<Lease> leases = leasePage.getContent();
        Map<UUID, TenantProfile> profileMap = loadProfiles(leases);
        Map<UUID, Property> propertyMap = loadProperties(tenantId, leases);
        Map<UUID, Unit> unitMap = loadUnits(tenantId, leases);

        List<LeaseSummaryResponse> result = leases.stream()
                .map(l -> toSummary(
                        l,
                        profileMap.get(l.getTenantProfileId()),
                        propertyMap.get(l.getPropertyId()),
                        unitMap.get(l.getUnitId())
                ))
                .toList();

        return new PageResponse<>(
                result,
                leasePage.getNumber(),
                leasePage.getSize(),
                leasePage.getTotalElements(),
                leasePage.getTotalPages(),
                leasePage.isFirst(),
                leasePage.isLast()
        );
    }

    private Map<UUID, TenantProfile> loadProfiles(List<Lease> leases) {
        Set<UUID> profileIds = leases.stream()
                .map(Lease::getTenantProfileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (profileIds.isEmpty()) return Collections.emptyMap();
        return tenantProfileRepository.findAllById(profileIds)
                .stream()
                .collect(Collectors.toMap(TenantProfile::getId, p -> p));
    }

    private Map<UUID, Property> loadProperties(UUID tenantId, List<Lease> leases) {
        List<UUID> propertyIds = leases.stream()
                .map(Lease::getPropertyId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (propertyIds.isEmpty()) return Collections.emptyMap();
        return propertyRepository.findAllByTenantIdAndIdIn(tenantId, propertyIds)
                .stream()
                .collect(Collectors.toMap(Property::getId, p -> p));
    }

    private Map<UUID, Unit> loadUnits(UUID tenantId, List<Lease> leases) {
        List<UUID> unitIds = leases.stream()
                .map(Lease::getUnitId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (unitIds.isEmpty()) return Collections.emptyMap();
        return unitRepository.findAllByTenantIdAndIdIn(tenantId, unitIds)
                .stream()
                .collect(Collectors.toMap(Unit::getId, u -> u));
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
                // Guard: if a non-zero security deposit was required, it must
                // be in HELD status before the lease can go live. A zero-deposit
                // lease (where the landlord has waived it) is allowed through.
                //
                // We check here (application layer) rather than inside Lease.activate()
                // to keep the domain model decoupled from the deposit module.
                if (lease.getSecurityDeposit() != null
                        && lease.getSecurityDeposit().compareTo(BigDecimal.ZERO) > 0) {

                    boolean depositHeld = depositRepository
                            .findByLeaseIdAndTenantId(lease.getId(), tenantId)
                            .map(d -> d.getStatus() == DepositStatus.HELD)
                            .orElse(false);

                    if (!depositHeld) {
                        throw new BusinessException(
                                "Security deposit has not been collected. " +
                                        "The deposit must be received before the lease can be activated.",
                                ErrorCode.DEPOSIT_NOT_HELD
                        );
                    }
                }
                workflowEngine.activate(lease);
                leaseActivationOrchestrator.onLeaseActivated(tenantId, lease);
            }

            case REJECT -> workflowEngine.reject(
                    lease,
                    request.getReason()
            );

            // request.getActor() is set by LeaseController from the verified
            // principal and overwrites anything the client sent.
            case TERMINATE -> workflowEngine.terminate(
                    lease,
                    request.getTerminationType(),
                    request.getReason(),
                    request.getActor()
            );

            case RENEW -> workflowEngine.renew(
                    lease,
                    request.getActionDate(),
                    calculateRenewalEndDate(lease, request),
                    request.getActor()
            );

            case EXPIRE -> workflowEngine.expire(lease);

            case CANCEL -> workflowEngine.cancel(
                    lease,
                    request.getReason()
            );
        }

        // Pull from `lease` (original in-memory aggregate) before save() —
        // LeaseRepositoryImpl.save() round-trips through mapper.toDomain(),
        // which reconstructs via restore()/rehydrate() and never repopulates
        // the transient domainEvents list. LeaseWorkflowEngine no longer
        // publishes directly; every event goes through registerEvent() on the
        // domain object and this publishAll() call.
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

        // Pull any registered domain events before the aggregate is removed
        // so they are not silently discarded (same pattern as create/executeAction).
        List<DomainEvent> events = lease.pullDomainEvents();

        leaseRepository.delete(lease.getId());

        eventPublisher.publishAll(events);
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
    private LeaseDetailResponse toDetailResponse(Lease lease, TenantProfile profile) {
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
                profile != null ? profile.getFullName() : null,
                profile != null ? profile.getPhone() : null,
                lease.getSignedAt(),
                lease.getActivatedAt(),
                lease.getTerminatedAt(),
                lease.getExpiredAt(),
                lease.getRenewedAt(),
                lease.getCancelledAt(),
                map(lease.getTerminationType()),
                lease.getTerminationReason(),
                LeaseActionPolicy.allowedActions(lease.getStatus())
        );
    }

    /**
     * Portfolio-wide stat-card figures — see {@code LeaseController#stats}
     * for why these are never paginated or filtered. "Expiring soon" mirrors
     * the frontend's isExpiringSoon(status, endDate, 30) exactly: ACTIVE or
     * RENEWED, with 0-30 days remaining inclusive (today and up to a month
     * out; already-expired dates and drafts/pending leases don't count).
     */
    public LeaseStatsResponse getStats() {
        UUID tenantId = TenantContext.getTenantId();
        List<Lease> leases = leaseRepository.findAllByTenant(tenantId);
        LocalDate today = LocalDate.now();
        LocalDate expiringCutoff = today.plusDays(30);

        long activeCount = 0;
        long expiringSoonCount = 0;
        BigDecimal monthlyRent = BigDecimal.ZERO;

        for (Lease lease : leases) {
            boolean isLive = lease.getStatus() == LeaseStatus.ACTIVE || lease.getStatus() == LeaseStatus.RENEWED;
            if (!isLive) continue;

            activeCount++;
            monthlyRent = monthlyRent.add(lease.getRentAmount());

            LocalDate endDate = lease.getEndDate();
            if (endDate != null && !endDate.isBefore(today) && !endDate.isAfter(expiringCutoff)) {
                expiringSoonCount++;
            }
        }

        return new LeaseStatsResponse(leases.size(), activeCount, expiringSoonCount, monthlyRent);
    }

    // =========================================================
    private LeaseSummaryResponse toSummary(Lease lease, TenantProfile profile, Property property, Unit unit) {
        return new LeaseSummaryResponse(
                lease.getId(),
                lease.getLeaseNumber(),
                map(lease.getStatus()),
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getRentAmount(),
                profile != null ? profile.getId() : null,
                profile != null ? profile.getFullName() : null,
                profile != null ? profile.getPhone() : null,
                lease.getPropertyId(),
                property != null ? property.getName() : null,
                lease.getUnitId(),
                unit != null ? (unit.getLabel() != null ? unit.getLabel() : unit.getUnitNumber()) : null
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