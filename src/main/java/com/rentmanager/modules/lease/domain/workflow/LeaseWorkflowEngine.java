package com.rentmanager.modules.lease.domain.workflow;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.TerminationType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.event.*;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.service.LeaseDomainService;
import com.rentmanager.modules.lease.domain.service.UnitOccupancyService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Enterprise-grade Lease Workflow Engine
 *
 * RULES:
 * - NEVER directly mutate state (no setStatus usage)
 * - ALWAYS delegate state changes to Lease aggregate
 * - ONLY orchestrate + publish events
 */
@Component
public class LeaseWorkflowEngine {

    private final LeaseWorkflowValidator validator;
    private final LeaseEventPublisher eventPublisher;
    private final UnitOccupancyService unitOccupancyService;
    private final LeaseRepository leaseRepository;
    private final LeaseDomainService leaseDomainService;




    public LeaseWorkflowEngine(
            LeaseWorkflowValidator validator,
            LeaseEventPublisher eventPublisher,
            LeaseRepository leaseRepository,
            LeaseDomainService leaseDomainService,
            UnitOccupancyService unitOccupancyService
    ) {
        this.validator = validator;
        this.eventPublisher = eventPublisher;
        this.leaseRepository = leaseRepository;
        this.leaseDomainService = leaseDomainService;
        this.unitOccupancyService = unitOccupancyService;
    }

    // =========================================================
    // APPROVAL FLOW
    // =========================================================

    public void approve(Lease lease) {

        if (lease.getStatus() != LeaseStatus.DRAFT) {
            throw new IllegalStateException("Only draft leases can be approved");
        }

        lease.approve();

        eventPublisher.publish(new LeaseApprovedEvent(
                lease.getTenantId(),
                lease.getId(),
                "SYSTEM",
                lease.getPropertyId(),
                lease.getUnitId(),
                lease.getTenantProfileId()
        ));
    }

    // =========================================================
    // ACTIVATION FLOW
    // =========================================================

    public void activate(Lease lease) {

        validator.validateActivation(lease);
        unitOccupancyService.validateUnitAvailability(lease.getUnitId());
        lease.activate();

        eventPublisher.publish(new LeaseActivatedEvent(
                lease.getTenantId(),
                lease.getId(),
                "SYSTEM",
                lease.getPropertyId(),
                lease.getUnitId(),
                lease.getTenantProfileId()
        ));
    }

    // =========================================================
    // REJECTION FLOW
    // =========================================================

    public void reject(Lease lease, String reason) {

        if (lease.getStatus() != LeaseStatus.DRAFT &&
                lease.getStatus() != LeaseStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Invalid rejection state");
        }

        lease.reject(reason);

        eventPublisher.publish(new LeaseCancelledEvent(
                lease.getTenantId(),
                lease.getId(),
                "SYSTEM",
                lease.getPropertyId(),
                lease.getUnitId(),
                lease.getTenantProfileId(),
                reason
        ));
    }

    // =========================================================
    // TERMINATION FLOW
    // =========================================================

    public void terminate(
            Lease lease,
            TerminationType type,
            String reason
    ) {

        validator.validateTermination(lease);

        lease.terminate(
                type,
                reason,
                "SYSTEM",
                lease.getTenantId()
        );

        eventPublisher.publish(new LeaseTerminatedEvent(
                lease.getTenantId(),
                lease.getId(),
                "SYSTEM",
                lease.getPropertyId(),
                lease.getUnitId(),
                lease.getTenantProfileId(),
                type,
                reason
        ));
    }

    // =========================================================
    // EXPIRY FLOW
    // =========================================================

    public void expire(Lease lease) {

        // NEW: was previously unguarded — only method in the engine with no
        // precondition check, inconsistent with every sibling. Now mirrors
        // Lease.expire()'s own guard, matching the rest of this class.
        validator.validateExpiry(lease);

        lease.expire();

        eventPublisher.publish(new LeaseExpiredEvent(
                lease.getTenantId(),
                lease.getId(),
                "SYSTEM",
                lease.getPropertyId(),
                lease.getUnitId(),
                lease.getTenantProfileId()
        ));
    }

    // =========================================================
    // CANCELLATION FLOW (NEW)
    // =========================================================

    public void cancel(Lease lease, String reason) {

        validator.validateCancellation(lease);

        lease.cancel(reason);

        eventPublisher.publish(new LeaseCancelledEvent(
                lease.getTenantId(),
                lease.getId(),
                "SYSTEM",
                lease.getPropertyId(),
                lease.getUnitId(),
                lease.getTenantProfileId(),
                reason
        ));
    }

    // =========================================================
    // RENEWAL FLOW
    // =========================================================

    public void renew(
            Lease lease,
            LocalDate newStart,
            LocalDate newEnd
    ) {

        validator.validateRenewal(lease);

        lease.renew(newStart, newEnd, lease.getTenantId(),"SYSTEM");

        eventPublisher.publish(new LeaseRenewedEvent(
                lease.getTenantId(),
                lease.getId(),
                "SYSTEM",
                newStart,
                newEnd
        ));
    }




    public void markAwaitingDeposit(Lease lease) {

        lease.markAwaitingDeposit();
    }
}