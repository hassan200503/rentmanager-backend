package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Nightly integrity sweep: {@code rent_ledger_entries.amount_paid} is a
 * denormalised total updated incrementally as each RentTransaction is
 * applied (see RentLedgerEntry's class javadoc — "the hybrid model"), with
 * nothing in the schema tying it back to the sum of its own transaction log.
 * A bug in any write path, a bad manual DB fix, or (now that
 * V81 prevents delete/mutation) an application-level bypass of the domain
 * layer could let the two silently drift apart.
 *
 * This never writes anything — it only logs a mismatch loudly enough to
 * page someone. Auto-correcting a financial balance from a background job
 * with no human review is its own kind of dangerous; see
 * RentLedgerEntry#replayAmountPaid for the replay logic itself.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentLedgerReconciliationScheduler {

    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final RentTransactionRepository rentTransactionRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "Africa/Nairobi") // 3:00 AM daily — after the overdue sweep (2:30 AM)
    public void runDaily() {
        Instant since = Instant.now().minus(2, ChronoUnit.DAYS);
        List<RentLedgerEntry> candidates = rentLedgerEntryRepository.findAllByUpdatedAtAfter(since);

        log.info("RentLedgerReconciliationScheduler: checking {} recently-updated entry(ies)", candidates.size());

        int mismatches = 0;
        for (RentLedgerEntry entry : candidates) {
            try {
                if (!reconcileOne(entry)) {
                    mismatches++;
                }
            } catch (Exception e) {
                log.error("RentLedgerReconciliationScheduler: failed to reconcile entry id={}", entry.getId(), e);
                // continue processing remaining entries rather than aborting the sweep
            }
        }

        if (mismatches > 0) {
            log.error("RentLedgerReconciliationScheduler: {} entry(ies) failed reconciliation — see ERROR logs above", mismatches);
        }
    }

    private boolean reconcileOne(RentLedgerEntry entry) {
        List<RentTransaction> transactions =
                rentTransactionRepository.findByLedgerEntry(entry.getTenantId(), entry.getId());
        BigDecimal expected = RentLedgerEntry.replayAmountPaid(transactions);

        if (expected.compareTo(entry.getAmountPaid()) != 0) {
            log.error("RentLedgerReconciliationScheduler: MISMATCH entryId={} tenantId={} leaseId={} " +
                            "recordedAmountPaid={} expectedFromTransactionLog={}",
                    entry.getId(), entry.getTenantId(), entry.getLeaseId(),
                    entry.getAmountPaid(), expected);
            return false;
        }
        return true;
    }
}
