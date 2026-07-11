package com.rentmanager.modules.lease.application.scheduler;

import com.rentmanager.modules.lease.application.orchestration.LeaseActivationOrchestrator;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
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
     *
     * UPDATED (this session): now also calls LeaseActivationOrchestrator.
     * onLeaseActivated(...) to post the lease's opening rent charge — the
     * gap flagged when the manual executeAction()/ACTIVATE path was wired
     * to the same orchestrator. This method's existing @Transactional
     * means the orchestrator's call into RentLedgerApplicationService.
     * postCharge() (REQUIRED propagation) joins this same transaction, so
     * the lease save and the opening charge commit atomically — same
     * guarantee as the manual path, same reasoning: an event-driven
     * alternative was considered and rejected here too, since a
     * transactional listener would fire after commit and reopen the exact
     * atomicity gap this fixes.
     *
     * tenantId is read directly off the loaded Lease rather than
     * TenantContext, since this runs in a background scheduler with no
     * inbound request to derive a tenant context from.
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
}