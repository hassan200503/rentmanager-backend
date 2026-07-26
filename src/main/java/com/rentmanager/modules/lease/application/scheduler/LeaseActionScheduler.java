package com.rentmanager.modules.lease.application.scheduler;

import com.rentmanager.modules.lease.application.orchestration.LeaseActivationOrchestrator;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowEngine;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.LeaseStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class LeaseActionScheduler {

    private final LeaseRepository leaseRepository;
    private final DomainEventPublisher eventPublisher;
    private final LeaseActivationOrchestrator leaseActivationOrchestrator;
    private final LeaseWorkflowEngine leaseWorkflowEngine;
    private UnitRepository unitRepository;

    // ------------------------------------------------------------------
    // SELF-INVOCATION FIX: @Transactional only takes effect when a call
    // passes through Spring's CGLIB proxy for this bean. runDaily() and
    // runDailyExpiry() previously called activateOne()/expireOne() via a
    // plain `this.` call, which bypasses the proxy entirely — so those
    // methods silently ran with NO transaction, causing
    // TransactionRequiredException inside UnitRepositoryAdapter's
    // pessimistic-lock query (findByIdForUpdate). Injecting a @Lazy
    // self-reference and calling through it forces the call back through
    // the proxy so @Transactional actually applies. @Lazy is required
    // here — a non-lazy self-autowire would try to fully construct this
    // bean while it's still being constructed. Field injection (not
    // constructor) is deliberate for the same reason. This field is only
    // used by runDaily()/runDailyExpiry(); tests that call
    // activateOne()/expireOne() directly (e.g. the 4-arg-constructor
    // expiry-only tests) never touch it and are unaffected.
    // ------------------------------------------------------------------
    @Autowired
    @Lazy
    private LeaseActionScheduler self;

    // 4-arg constructor — used by expiry-only tests that don't need UnitRepository
    public LeaseActionScheduler(
            LeaseRepository leaseRepository,
            DomainEventPublisher eventPublisher,
            LeaseActivationOrchestrator leaseActivationOrchestrator,
            LeaseWorkflowEngine leaseWorkflowEngine
    ) {
        this(leaseRepository, eventPublisher, leaseActivationOrchestrator, leaseWorkflowEngine, null);
    }

    @Autowired
    public LeaseActionScheduler(
            LeaseRepository leaseRepository,
            DomainEventPublisher eventPublisher,
            LeaseActivationOrchestrator leaseActivationOrchestrator,
            LeaseWorkflowEngine leaseWorkflowEngine,
            UnitRepository unitRepository
    ) {
        this.leaseRepository = leaseRepository;
        this.eventPublisher = eventPublisher;
        this.leaseActivationOrchestrator = leaseActivationOrchestrator;
        this.leaseWorkflowEngine = leaseWorkflowEngine;
        this.unitRepository = unitRepository;
    }

    /**
     * Runs daily — activates leases whose move-in date has arrived.
     *
     * ============================================================
     * TEMPORARY TEST OVERRIDE (revert before merging/deploying):
     * Original: @Scheduled(cron = "0 0 1 * * *") // 1:00 AM daily
     * Swapped to fixedRate so activation fires every 5 minutes
     * instead of waiting for the daily 1:00 AM window, purely to
     * make manual/local testing faster.
     * ============================================================
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 5 * 60 * 1000) // TEMP-TEST: every 5 min
    public void runDaily() {

        List<Lease> leases =
                leaseRepository.findAllByStatusAndStartDateLessThanEqual(
                        LeaseStatus.PENDING_ACTIVATION,
                        LocalDate.now()
                );

        log.info("LeaseActionScheduler: found {} lease(s) pending activation", leases.size());

        for (Lease lease : leases) {
            try {
                // Call through the proxied self-reference, not `this` —
                // see the self-invocation fix note on the `self` field.
                self.activateOne(lease);
            } catch (Exception e) {
                log.error("Failed to activate lease id={}", lease.getId(), e);
                // continue processing remaining leases rather than aborting the batch
            }
        }
    }

    /**
     * Each lease gets its own transaction so one failure doesn't roll back others.
     */
    @Transactional
    public void activateOne(Lease lease) {

        // ---------------- IDEMPOTENCY GUARD ----------------
        if (lease.getStatus() != LeaseStatus.PENDING_ACTIVATION) {
            return;
        }

        UUID tenantId = lease.getTenantId();

        // ---------------- STATE TRANSITION ----------------
        lease.activatePending();

        // ---------------- MARK UNIT OCCUPIED ----------------
        // On move-in date the scheduler activates the lease; the unit must
        // also transition to OCCUPIED so property occupancy rollup works.
        UUID unitId = lease.getUnitId();
        if (unitId != null && unitRepository != null) {
            Unit unit = unitRepository.findByIdForUpdate(unitId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unit not found for lease activation. unitId=" + unitId));
            unit.markOccupied("lease-activation-" + lease.getId());
            var unitEvents = unit.pullDomainEvents();
            unitRepository.save(unit);
            eventPublisher.publishAll(unitEvents);
        }

        leaseRepository.save(lease);

        // ---------------- RENT LEDGER: POST OPENING CHARGE ----------------
        leaseActivationOrchestrator.onLeaseActivated(tenantId, lease);

        // ---------------- DOMAIN EVENTS ----------------
        eventPublisher.publishAll(lease.pullDomainEvents());

        log.info("Lease activated. leaseId={}", lease.getId());
    }

    /**
     * NEW: runs daily — expires leases whose contractual end date has
     * passed. Staggered 30 minutes after the activation sweep so both
     * schedulers never contend for the same lease rows in a single run.
     *
     * SCOPE (deliberate, decided this session): only FIXED_TERM and
     * STANDARD leases are eligible. MONTH_TO_MONTH leases are excluded —
     * their endDate is a rolling/nominal field, not a real contractual
     * expiry (a month-to-month tenancy of unknown duration should only
     * end via TERMINATE/notice, never a silent date-based sweep).
     *
     * Eligible source statuses are ACTIVE and RENEWED, per this session's
     * decision to treat RENEWED as equivalent to ACTIVE going forward.
     *
     * ============================================================
     * TEMPORARY TEST OVERRIDE (revert before merging/deploying):
     * Original: @Scheduled(cron = "0 30 1 * * *") // 1:30 AM daily
     * Swapped to fixedRate so expiry fires every 10 minutes instead
     * of waiting for the daily 1:30 AM window, purely to make
     * manual/local testing faster.
     * ============================================================
     */
    @Scheduled(fixedRate = 10 * 60 * 1000, initialDelay = 10 * 60 * 1000) // TEMP-TEST: every 10 min
    public void runDailyExpiry() {

        List<Lease> leases =
                leaseRepository.findAllByStatusInAndLeaseTypeInAndEndDateLessThanEqual(
                        List.of(LeaseStatus.ACTIVE, LeaseStatus.RENEWED),
                        List.of(LeaseType.FIXED_TERM, LeaseType.STANDARD),
                        LocalDate.now()
                );

        log.info("LeaseActionScheduler: found {} lease(s) eligible for expiry", leases.size());

        for (Lease lease : leases) {
            try {
                // Call through the proxied self-reference, not `this` —
                // see the self-invocation fix note on the `self` field.
                self.expireOne(lease);
            } catch (Exception e) {
                log.error("Failed to expire lease id={}", lease.getId(), e);
            }
        }
    }

    /**
     * Each lease gets its own transaction, same isolation pattern as
     * activateOne(). Delegates to LeaseWorkflowEngine.expire() rather than
     * calling Lease.expire() directly, reusing the engine's validation.
     *
     * FIX (this session): added the pullDomainEvents()/publishAll() pair
     * below. Previously this method relied entirely on
     * LeaseWorkflowEngine.expire()'s own direct eventPublisher.publish()
     * call to fire LeaseExpiredEvent — that direct call has now been
     * removed from the engine as part of fixing a double-publish bug on
     * the manual-action path (see LeaseWorkflowEngine.java /
     * LeaseApplicationService.executeAction()). Without this addition,
     * removing the engine's direct publish would have silently stopped
     * LeaseExpiredEvent from firing on the scheduled sweep entirely. This
     * now matches the same publish pattern already used by activateOne()
     * above.
     */
    @Transactional
    public void expireOne(Lease lease) {

        // ---------------- IDEMPOTENCY GUARD ----------------
        if (lease.getStatus() != LeaseStatus.ACTIVE && lease.getStatus() != LeaseStatus.RENEWED) {
            return;
        }

        // ---------------- INDEPENDENT LEASE-TYPE GUARD ----------------
        if (lease.getLeaseType() == LeaseType.MONTH_TO_MONTH) {
            throw new LeaseStateException(
                    "Month-to-month leases cannot be expired via the scheduled sweep; "
                            + "use termination instead. leaseId=" + lease.getId(),
                    ErrorCode.LEASE_EXPIRATION_NOT_APPLICABLE_TO_MONTH_TO_MONTH
            );
        }

        leaseWorkflowEngine.expire(lease);
        leaseRepository.save(lease);

        // ---------------- DOMAIN EVENTS ----------------
        eventPublisher.publishAll(lease.pullDomainEvents());

        log.info("Lease expired. leaseId={}", lease.getId());
    }
}