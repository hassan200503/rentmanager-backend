package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Safety net for B2C disbursements that failed or got stuck in PENDING
 * (callback never arrived). Retries failed disbursements up to
 * {@code MAX_RETRIES} times, then flags them for manual attention.
 *
 * This class itself is NOT {@code @Transactional}. The actual DB + HTTP
 * work happens in {@link DisbursementRetrySweepService}, invoked as calls
 * on a separate proxied bean — never as internal method calls on {@code this}.
 * One bad row can't block the rest of the batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisbursementRetryScheduler {

    private static final int MAX_RETRIES = 3;
    private static final Duration STUCK_AFTER = Duration.ofMinutes(30);

    private final DisbursementRepository disbursementRepository;
    private final DisbursementRetrySweepService sweepService;

    @Scheduled(fixedRate = 5 * 60 * 1000) // every 5 minutes
    public void retryFailedDisbursements() {
        List<Disbursement> failed = disbursementRepository
                .findByStatusInAndRetryCountLessThan(
                        List.of(DisbursementStatus.FAILED),
                        MAX_RETRIES
                );

        if (failed.isEmpty()) {
            return;
        }

        log.info("Disbursement retry sweep found {} FAILED candidate(s) to retry", failed.size());

        for (Disbursement d : failed) {
            try {
                sweepService.retryOne(d.getId());
            } catch (Exception e) {
                log.error("Failed to retry disbursement {} — will retry on next sweep", d.getId(), e);
            }
        }
    }

    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 30_000) // 30s after startup, then every 5 min
    public void retryStuckPendingDisbursements() {
        Instant cutoff = Instant.now().minus(STUCK_AFTER);

        List<Disbursement> stuck = disbursementRepository
                .findByStatusInAndCreatedAtBefore(
                        List.of(DisbursementStatus.PENDING),
                        cutoff
                );

        if (stuck.isEmpty()) {
            return;
        }

        log.info("Disbursement retry sweep found {} PENDING (stuck) candidate(s) older than {}", stuck.size(), cutoff);

        for (Disbursement d : stuck) {
            try {
                sweepService.retryOne(d.getId());
            } catch (Exception e) {
                log.error("Failed to retry stuck disbursement {} — will retry on next sweep", d.getId(), e);
            }
        }
    }
}
