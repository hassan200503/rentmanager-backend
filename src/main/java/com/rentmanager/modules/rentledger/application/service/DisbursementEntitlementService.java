package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Works out how much a landlord is still owed on one ledger entry.
 *
 * <h2>Why this exists</h2>
 * {@code POST /api/v1/disbursements} took the payout amount straight from the
 * request body and validated nothing, so any OWNER or MANAGER could send an
 * arbitrary sum. Under platform billing that sum is drawn from pooled float
 * belonging to other landlords and their tenants.
 *
 * <p>The fix is not a bigger validation method — it is a category change. An
 * amount stops being "any number the caller supplies" and becomes "at most
 * what this entry has actually produced and not yet paid out". A caller may
 * ask for less. Never for more.
 *
 * <h2>The arithmetic</h2>
 * <pre>
 *   settleable = Σ net proceeds of payments on the entry
 *              − Σ payouts already raised against it
 * </pre>
 *
 * <p>{@code netAmount} is the post-commission figure stamped on the
 * transaction by the M-Pesa callback. Where it is absent — a
 * {@code PREMIUM_MONTHLY} landlord pays no commission, and rows written
 * before the commission snapshot existed have nothing recorded — the gross
 * amount is the net, which is the same conclusion the callback reaches.
 *
 * <p>{@code FAILED} disbursements are excluded because no money left. Every
 * other status counts, including {@code INITIATED} and {@code PENDING}: a
 * payout in flight has not been confirmed, but treating it as free money is
 * exactly how the same proceeds get paid out twice while the first attempt is
 * still settling.
 */
@Service
@RequiredArgsConstructor
public class DisbursementEntitlementService {

    /** Statuses that represent money committed or already gone. */
    private static final List<DisbursementStatus> COMMITTED = List.of(
            DisbursementStatus.INITIATED,
            DisbursementStatus.PENDING,
            DisbursementStatus.SUCCESS
    );

    private final RentTransactionRepository rentTransactionRepository;
    private final DisbursementRepository disbursementRepository;

    /**
     * The most that may still be disbursed against {@code ledgerEntryId}.
     * Never negative — an entry that has somehow been over-disbursed reports
     * zero rather than a negative ceiling that a subtraction elsewhere could
     * turn back into headroom.
     */
    @Transactional(readOnly = true)
    public BigDecimal settleableAmount(UUID tenantId, UUID ledgerEntryId) {
        BigDecimal proceeds = netProceeds(tenantId, ledgerEntryId);
        BigDecimal alreadyCommitted = committedPayouts(tenantId, ledgerEntryId);

        BigDecimal remaining = proceeds.subtract(alreadyCommitted);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }

    private BigDecimal netProceeds(UUID tenantId, UUID ledgerEntryId) {
        List<RentTransaction> transactions =
                rentTransactionRepository.findByLedgerEntry(tenantId, ledgerEntryId);

        // Payments are never deleted — a REVERSAL posts a compensating row
        // pointing back at the original (V71). Both sides have to drop out:
        // skipping only the reversal row would still count money that was
        // given back, and skipping only the original would be arithmetically
        // right by accident while reading as if reversals were free.
        Set<UUID> reversed = transactions.stream()
                .map(RentTransaction::getReversesTransactionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        BigDecimal total = BigDecimal.ZERO;

        for (RentTransaction tx : transactions) {
            if (tx.getType() != RentTransactionType.PAYMENT) {
                continue;
            }
            if (reversed.contains(tx.getId())) {
                continue;
            }
            // Belt and braces: a reversal row carries the REVERSAL type and so
            // is already excluded above, but a row that points at another
            // transaction is a correction under any type and must never be
            // counted as fresh proceeds.
            if (tx.getReversesTransactionId() != null) {
                continue;
            }
            BigDecimal net = tx.getNetAmount() != null ? tx.getNetAmount() : tx.getAmount();
            if (net != null) {
                total = total.add(net);
            }
        }
        return total;
    }

    private BigDecimal committedPayouts(UUID tenantId, UUID ledgerEntryId) {
        BigDecimal total = BigDecimal.ZERO;

        for (Disbursement d : disbursementRepository.findByLedgerEntryId(tenantId, ledgerEntryId)) {
            if (COMMITTED.contains(d.getStatus()) && d.getAmount() != null) {
                total = total.add(d.getAmount());
            }
        }
        return total;
    }
}
