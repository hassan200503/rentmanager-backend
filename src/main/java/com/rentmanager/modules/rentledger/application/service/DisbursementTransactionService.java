package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.audit.application.service.FinancialAuditService;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.observability.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Owns the short-lived, lock-holding transactional steps of the B2C
 * disbursement flow. Deliberately a separate bean from
 * {@link B2CDisbursementService} rather than private {@code @Transactional}
 * methods there: Spring's {@code @Transactional} is proxy-based, so a
 * self-invoked call from within the same bean bypasses the proxy silently —
 * no exception, no warning, just a transaction (and a lock) that quietly
 * never opens. Splitting into a separate bean is the only way to guarantee
 * these boundaries are honored. This mirrors
 * {@code UnitReservationTransactionService} in the reservation module, which
 * exists for exactly the same reason.
 *
 * <h2>Why the lock is needed</h2>
 * {@link DisbursementEntitlementService#settleableAmount} answers "how much
 * may still be paid out against this charge" as
 * {@code netProceeds - committedPayouts}. Read and write used to sit in one
 * transaction with no lock, so under READ COMMITTED two concurrent payout
 * attempts on the same charge could each read the same
 * {@code committedPayouts} — neither seeing the other's uncommitted row —
 * and both pass the cap. Nothing in the schema caught it either:
 * {@code uk_disbursements_originator_conversation_id} is the only unique
 * index, and that value is assigned by Daraja *after* the call goes out.
 * The result was a double payout of real money.
 *
 * <p>Locking the ledger entry row serializes those attempts. The second
 * transaction blocks until the first commits, then — READ COMMITTED taking
 * a fresh snapshot per statement — reads the first disbursement and
 * correctly computes a settleable amount of zero.
 *
 * <h2>Why the Daraja call is outside it</h2>
 * The lock is released at commit, before the caller talks to Safaricom.
 * Holding a row lock across a slow external call would serialize every
 * payout attempt on that charge for the duration of that call, turning a
 * Safaricom slowdown into a stuck disbursement queue. The INITIATED row is
 * committed first precisely so it counts against the cap the moment the
 * lock drops — {@code INITIATED} is in the entitlement service's
 * {@code COMMITTED} set — which is what makes it safe to let go.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisbursementTransactionService {

    private final DisbursementRepository disbursementRepository;
    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final TenantRepository tenantRepository;
    private final DisbursementEntitlementService entitlementService;
    private final FinancialAuditService financialAuditService;
    private final BusinessMetrics metrics;

    /**
     * Takes the row lock, re-checks entitlement under it, and persists the
     * disbursement in INITIATED state. Returns the saved record; the caller
     * then performs the Daraja call with no lock held.
     *
     * <p>The recipient is resolved here from {@code tenants.payout_phone_number}
     * rather than taken from the caller, so no request body can redirect a
     * payout.
     */
    @Transactional
    public Disbursement reserveEntitlementAndCreate(
            UUID tenantId,
            UUID leaseId,
            UUID ledgerEntryId,
            BigDecimal amount,
            String commandId
    ) {
        // Lock first. Every read below is state a concurrent disbursement
        // against the same charge would otherwise race us on.
        rentLedgerEntryRepository.findByIdAndTenantIdForUpdate(ledgerEntryId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "Rent charge not found", ErrorCode.RESOURCE_NOT_FOUND));

        Tenant landlord = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "Landlord not found", ErrorCode.RESOURCE_NOT_FOUND));

        String recipientPhone = landlord.getPayoutPhoneNumber();
        if (recipientPhone == null || recipientPhone.isBlank()) {
            metrics.disbursementRefused();
            financialAuditService.disbursementRefused(
                    tenantId, ledgerEntryId, String.valueOf(amount),
                    "No payout number configured");
            throw new RentLedgerStateException(
                    "No payout number is configured for this account. "
                            + "Add one in settings before disbursing.",
                    ErrorCode.VALIDATION_ERROR);
        }

        BigDecimal settleable = entitlementService.settleableAmount(tenantId, ledgerEntryId);
        if (amount.compareTo(settleable) > 0) {
            log.warn("Disbursement refused: requested {} exceeds settleable {}. tenantId={} ledgerEntryId={}",
                    amount, settleable, tenantId, ledgerEntryId);
            metrics.disbursementRefused();
            financialAuditService.disbursementRefused(
                    tenantId, ledgerEntryId, String.valueOf(amount),
                    "Exceeds settleable amount of " + settleable);
            throw new RentLedgerStateException(
                    "Requested amount exceeds what remains to be disbursed for this charge.",
                    ErrorCode.VALIDATION_ERROR);
        }

        // Persisted BEFORE the Daraja call, so a crash after the API call but
        // before the result is recorded still leaves a traceable row — and so
        // the amount counts against the cap as soon as the lock drops.
        return disbursementRepository.save(Disbursement.create(
                tenantId, leaseId, ledgerEntryId, amount,
                recipientPhone, landlord.getName(), commandId
        ));
    }

    /** Records the Daraja conversation id once the call has been accepted. */
    @Transactional
    public Disbursement markPending(Disbursement disbursement, String originatorConversationId) {
        disbursement.markPending(originatorConversationId);
        return disbursementRepository.save(disbursement);
    }

    /**
     * Records a failed initiation. Kept in its own transaction so it still
     * commits when the surrounding attempt is being abandoned.
     */
    @Transactional
    public Disbursement markFailed(Disbursement disbursement, String reason) {
        disbursement.markFailed(reason, null);
        return disbursementRepository.save(disbursement);
    }
}
