package com.rentmanager.modules.lease.domain.workflow;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.TerminationType;
import com.rentmanager.modules.lease.domain.model.Lease;
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
 * - Validates + orchestrates state transitions only
 *
 * FIX (this session): this class previously called
 * eventPublisher.publish(...) directly inside approve/activate/reject/
 * terminate/renew/cancel, IN ADDITION TO Lease itself registering the same
 * event via registerEvent() on every one of those transitions. Before
 * LeaseApplicationService.executeAction() correctly pulled events off the
 * aggregate (separate fix, same session), the registerEvent() side was
 * dead — this class's direct publish was the only live path, so it looked
 * correct. Once pullDomainEvents() started working, every one of those six
 * transitions double-fired its event. LeaseEventPublisher and its direct
 * publish() calls are removed here; Lease's own registerEvent() plus the
 * application layer's pullDomainEvents()/publishAll() is now the single
 * source of truth, consistent with Unit, Deposit, and PaymentIntent
 * elsewhere in this codebase. expire() is no longer a special case either
 * — see Lease.expire(), which now registers its event again now that this
 * class no longer double-publishes it.
 */
@Component
public class LeaseWorkflowEngine {

    private final LeaseWorkflowValidator validator;
    private final UnitOccupancyService unitOccupancyService;
    private final LeaseRepository leaseRepository;
    private final LeaseDomainService leaseDomainService;




    public LeaseWorkflowEngine(
            LeaseWorkflowValidator validator,
            LeaseRepository leaseRepository,
            LeaseDomainService leaseDomainService,
            UnitOccupancyService unitOccupancyService
    ) {
        this.validator = validator;
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
    }

    // =========================================================
    // ACTIVATION FLOW
    // =========================================================

    public void activate(Lease lease) {

        validator.validateActivation(lease);
        unitOccupancyService.validateUnitAvailability(lease.getUnitId());
        lease.activate();
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
    }

    // =========================================================
    // TERMINATION FLOW
    // =========================================================

    public void terminate(
            Lease lease,
            TerminationType type,
            String reason
    ) {
        terminate(lease, type, reason, "SYSTEM");
    }

    /** Records who ended the lease; the API passes the authenticated user, never a client value. */
    public void terminate(Lease lease, TerminationType type, String reason, String actor) {

        validator.validateTermination(lease);

        lease.terminate(
                type,
                reason,
                actor == null || actor.isBlank() ? "SYSTEM" : actor,
                lease.getTenantId()
        );
    }

    // =========================================================
    // EXPIRY FLOW
    // =========================================================

    public void expire(Lease lease) {

        validator.validateExpiry(lease);

        lease.expire();
    }

    // =========================================================
    // CANCELLATION FLOW
    // =========================================================

    public void cancel(Lease lease, String reason) {

        validator.validateCancellation(lease);

        lease.cancel(reason);
    }

    // =========================================================
    // RENEWAL FLOW
    // =========================================================

    public void renew(
            Lease lease,
            LocalDate newStart,
            LocalDate newEnd
    ) {

        renew(lease, newStart, newEnd, "SYSTEM");
    }

    public void renew(Lease lease, LocalDate newStart, LocalDate newEnd, String actor) {

        validator.validateRenewal(lease);

        lease.renew(newStart, newEnd, lease.getTenantId(), actor == null || actor.isBlank() ? "SYSTEM" : actor);
    }




    public void markAwaitingDeposit(Lease lease) {

        lease.markAwaitingDeposit();
    }
}