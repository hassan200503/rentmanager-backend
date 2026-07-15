package com.rentmanager.modules.lease.application.scheduler;

import com.rentmanager.modules.lease.application.orchestration.LeaseActivationOrchestrator;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowEngine;
import com.rentmanager.shared.events.DomainEventPublisher;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.LeaseStateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeaseActionScheduler {

    private final LeaseRepository leaseRepository;
    private final DomainEventPublisher eventPublisher;
    private final LeaseActivationOrchestrator leaseActivationOrchestrator;
    private final LeaseWorkflowEngine leaseWorkflowEngine;

    /**
     * Runs daily — activates leases whose move-in date has arrived.
     */
    @Scheduled(cron = "0 0 1 * * *") // 1:00 AM daily — adjust to your timezone/needs
    public void runDaily() {

        List<Lease> leases =
                leaseRepository.findAllByStatusAndStartDateLessThanEqual(
                        LeaseStatus.PENDING_ACTIVATION,
                        LocalDate.now()
                );

        log.info("LeaseActionScheduler: found {} lease(s) pending activation", leases.size());

        for (Lease lease : leases) {
            try {
                activateOne(lease);
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

        // ---------------- STATE TRANSITION ----------------
        lease.activatePending();
        leaseRepository.save(lease);

        // ---------------- RENT LEDGER: POST OPENING CHARGE ----------------
        leaseActivationOrchestrator.onLeaseActivated(lease.getTenantId(), lease);

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
     */
    @Scheduled(cron = "0 30 1 * * *") // 1:30 AM daily
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
                expireOne(lease);
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