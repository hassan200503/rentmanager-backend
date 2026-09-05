package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.audit.application.service.FinancialAuditService;
import com.rentmanager.shared.observability.BusinessMetrics;
import com.rentmanager.modules.notification.sms.PhoneMasker;
import com.rentmanager.modules.rentledger.infrastructure.daraja.DarajaB2CService;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class B2CDisbursementService {

    private final DisbursementRepository disbursementRepository;
    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final RentLedgerApplicationService rentLedgerApplicationService;
    private final DarajaB2CService darajaB2CService;
    private final FinancialAuditService financialAuditService;
    private final BusinessMetrics metrics;
    // Resolving the landlord and the settleable cap moved to this bean along
    // with the row lock that has to cover them — see its class javadoc.
    private final DisbursementTransactionService disbursementTransactionService;

    /**
     * Initiates a B2C disbursement to the landlord's registered payout number.
     *
     * <h2>The recipient is derived, never supplied</h2>
     * This method used to take {@code recipientPhone} and {@code recipientName}
     * as arguments, passed through untouched from the request body, so any
     * OWNER or MANAGER could send any amount to any phone in Kenya. Under
     * platform billing that money is drawn from pooled float belonging to
     * other landlords and their tenants.
     *
     * <p>The control was never missing from the system — the automatic payout
     * in {@code RentPaymentCallbackService.initiateB2CIfNeeded} has always
     * read {@code tenants.payout_phone_number} and refused to disburse
     * without one. The manual endpoint simply went around it. Both paths now
     * resolve the destination the same way, from data the server owns.
     *
     * <h2>The amount is capped at what is actually owed</h2>
     * {@link DisbursementEntitlementService} computes the entry's net
     * proceeds less anything already paid out. A caller may request less than
     * that. A request for more is refused rather than clamped: silently
     * paying out a different number from the one an operator typed is how a
     * reconciliation goes unexplained for a month.
     *
     * <p>Persists the Disbursement record BEFORE calling Daraja, so a crash
     * after the API call but before the result is recorded still leaves a
     * traceable row in INITIATED state.
     *
     * <h2>Concurrency</h2>
     * Reserving the entitlement and persisting that row happen inside
     * {@link DisbursementTransactionService#reserveEntitlementAndCreate},
     * which holds a PESSIMISTIC_WRITE lock on the ledger entry for the
     * duration. Without it, two concurrent attempts on the same charge both
     * read the same {@code committedPayouts} under READ COMMITTED, both pass
     * the cap, and the landlord is paid twice. That method is deliberately
     * NOT called on {@code this} — see its class javadoc for why a separate
     * bean is the only way the transaction boundary is honored — and the
     * Daraja call below sits outside it so no row lock is ever held across
     * an external HTTP call.
     *
     * @throws RentLedgerStateException when the landlord has no payout number
     *         configured, or the amount exceeds what remains settleable.
     */
    public Disbursement initiateDisbursement(
            UUID tenantId,
            UUID leaseId,
            UUID ledgerEntryId,
            BigDecimal amount,
            String commandId,
            String remarks
    ) {
        Disbursement disbursement = disbursementTransactionService.reserveEntitlementAndCreate(
                tenantId, leaseId, ledgerEntryId, amount, commandId);

        String recipientPhone = disbursement.getRecipientPhone();
        String recipientName = disbursement.getRecipientName();

        String originatorConversationId;
        try {
            originatorConversationId = darajaB2CService.initiateB2C(
                    amount, recipientPhone, recipientName, remarks, commandId
            );
        } catch (Exception e) {
            disbursementTransactionService.markFailed(
                    disbursement, "Initiation failed: " + e.getMessage());
            throw new RentLedgerStateException("B2C initiation failed", ErrorCode.INTERNAL_ERROR);
        }

        disbursement = disbursementTransactionService.markPending(disbursement, originatorConversationId);

        log.info("B2C disbursement initiated. id={} amount={} recipient={} conversationId={}",
                disbursement.getId(), amount, PhoneMasker.mask(recipientPhone), originatorConversationId);

        // Who authorised this, against which charge, for how much. The ledger
        // already proves the money moved; this is the only record of the
        // decision behind it.
        metrics.disbursementInitiated();
        financialAuditService.disbursementInitiated(
                tenantId, disbursement.getId(), ledgerEntryId,
                String.valueOf(amount), PhoneMasker.mask(recipientPhone),
                originatorConversationId);

        return disbursement;
    }

    /**
     * Handles the B2C ResultURL callback. Updates the disbursement record
     * and posts a REFUND transaction to the rent ledger on success.
     */
    @Transactional
    public Disbursement handleResult(
            UUID disbursementId,
            String resultCode,
            String resultDesc,
            String transactionId,
            String conversationId
    ) {
        Disbursement disbursement = disbursementRepository.findById(disbursementId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "Disbursement not found: " + disbursementId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (disbursement.getStatus() == DisbursementStatus.SUCCESS
                || disbursement.getStatus() == DisbursementStatus.FAILED) {
            log.info("Disbursement {} already has terminal status {} — ignoring duplicate callback",
                    disbursementId, disbursement.getStatus());
            return disbursement;
        }

        if ("0".equals(resultCode)) {
            disbursement.markSuccess(transactionId, conversationId);

            if (disbursement.getLedgerEntryId() != null) {
                rentLedgerApplicationService.applyTransaction(
                        disbursement.getTenantId(),
                        "disbursement-" + disbursement.getId(),
                        disbursement.getLedgerEntryId(),
                        RentTransactionType.REFUND,
                        disbursement.getAmount(),
                        transactionId,
                        RentTransactionSource.MPESA,
                        "SYSTEM",
                        LocalDateTime.now()
                );
            }
        } else {
            disbursement.markFailed(resultDesc, conversationId);
        }

        disbursement = disbursementRepository.save(disbursement);
        log.info("B2C disbursement result processed. id={} resultCode={} status={}",
                disbursementId, resultCode, disbursement.getStatus());
        return disbursement;
    }

    /**
     * Handles the B2C QueueTimeOutURL callback (Safaricom queued but never
     * processed). Marks the disbursement as FAILED with a timeout reason.
     */
    @Transactional
    public Disbursement handleTimeout(UUID disbursementId) {
        Disbursement disbursement = disbursementRepository.findById(disbursementId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "Disbursement not found: " + disbursementId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (disbursement.getStatus() != DisbursementStatus.PENDING) {
            log.info("Disbursement {} in state {} — ignoring timeout callback",
                    disbursementId, disbursement.getStatus());
            return disbursement;
        }

        disbursement.markFailed("Queue timeout — Safaricom did not process the request", null);
        disbursement = disbursementRepository.save(disbursement);
        log.warn("B2C disbursement timed out. id={}", disbursementId);
        return disbursement;
    }
}