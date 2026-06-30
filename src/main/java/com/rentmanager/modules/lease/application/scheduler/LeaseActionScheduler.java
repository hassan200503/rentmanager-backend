package com.rentmanager.modules.lease.application.scheduler;

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

        // ---------------- DOMAIN EVENTS ----------------
        eventPublisher.publishAll(lease.pullDomainEvents());

        log.info("Lease activated. leaseId={}", lease.getId());
    }
}