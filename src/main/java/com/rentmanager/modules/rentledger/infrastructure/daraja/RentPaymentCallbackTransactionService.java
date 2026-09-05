package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.rentmanager.modules.rentledger.application.service.CommissionPolicyService;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.rentledger.domain.model.UnmatchedPayment;
import com.rentmanager.modules.rentledger.domain.repository.UnmatchedPaymentRepository;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentPaymentCallbackTransactionService {

    private final RentPaymentRequestRepository rentPaymentRequestRepository;
    private final UnmatchedPaymentRepository unmatchedPaymentRepository;
    private final RentLedgerApplicationService rentLedgerApplicationService;
    private final RentTransactionRepository rentTransactionRepository;
    private final CommissionPolicyService commissionPolicyService;
    private final DisbursementRepository disbursementRepository;
    private final EntityManager entityManager;
    private final TenantRepository tenantRepository;

    @Transactional
    public SuccessfulPaymentResult processSuccessfulCallback(
            String checkoutRequestId, String mpesaReceiptNumber
    ) {
        RentPaymentRequest request = rentPaymentRequestRepository
                .findByMpesaCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "No RentPaymentRequest found for CheckoutRequestID: " + checkoutRequestId,
                        com.rentmanager.shared.exception.ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (request.getStatus() != RentPaymentRequestStatus.PENDING) {
            log.info("Duplicate rent payment callback — skipping. CheckoutRequestID={} status={}",
                    checkoutRequestId, request.getStatus());
            return null;
        }

        request.markPaid(mpesaReceiptNumber);
        try {
            rentPaymentRequestRepository.save(request);
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic locking failure marking rent payment PAID. " +
                    "CheckoutRequestID={} requestId={} — likely concurrent callback delivery, " +
                    "will self-heal on Safaricom retry.",
                    checkoutRequestId, request.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error persisting paid rent payment. " +
                    "CheckoutRequestID={} requestId={}",
                    checkoutRequestId, request.getId(), e);
            throw e;
        }

        String correlationId = "rent-payment-" + request.getId();

        rentLedgerApplicationService.applyTransaction(
                request.getTenantId(),
                correlationId,
                request.getRentLedgerEntryId(),
                RentTransactionType.PAYMENT,
                request.getAmount(),
                mpesaReceiptNumber,
                RentTransactionSource.MPESA,
                "SYSTEM",
                LocalDateTime.now()
        );

        RentTransaction transaction = rentTransactionRepository
                .findByExternalReference(request.getTenantId(), mpesaReceiptNumber)
                .orElseThrow(() -> new RentLedgerStateException(
                        "Transaction not found for receipt: " + mpesaReceiptNumber,
                        com.rentmanager.shared.exception.ErrorCode.RESOURCE_NOT_FOUND
                ));

        // No commission when the platform never received the money. Under
        // DIRECT collection the rent settled straight into the landlord's own
        // paybill, so there is nothing here to take a cut of — deducting one
        // would record a commission that was never actually collected, and
        // hand the disbursement path a net amount to pay out of a float that
        // never received it. See CollectionMode and V89.
        boolean collectsDirectly = collectsDirectly(request.getTenantId());
        boolean premiumBilling = isPremiumMonthly(request.getTenantId());

        BigDecimal ratePercent = null;
        BigDecimal commissionAmount = null;
        // netAmount means "what the platform holds and must pay out". It
        // drives the B2C disbursement downstream, so it stays NULL whenever
        // the platform is holding nothing.
        BigDecimal netAmount = null;

        if (collectsDirectly) {
            // DIRECT: the money settled into the landlord's own paybill at
            // the moment the renter paid. No commission to deduct and — the
            // load-bearing part — nothing to disburse. Leaving netAmount null
            // is what stops initiateB2CIfNeeded from trying to pay out money
            // the platform never received. Setting it to gross here, as the
            // premium branch does, would attempt a B2C against a float that
            // has no such funds in it.
            netAmount = null;
        } else if (premiumBilling) {
            // PLATFORM_CUSTODY + PREMIUM_MONTHLY: the platform does hold the
            // money, takes no commission, and disburses 100% in exchange for
            // the flat monthly fee. Applies during the grace window too
            // (features keep working until the revert).
            netAmount = request.getAmount();
        } else if ((ratePercent = commissionPolicyService.getActiveRate(request.getTenantId())) != null) {
            commissionAmount = CommissionPolicyService.computeCommission(request.getAmount(), ratePercent);
            netAmount = CommissionPolicyService.computeNetAmount(request.getAmount(), commissionAmount);
            transaction.applyCommission(ratePercent, commissionAmount, netAmount);
            rentTransactionRepository.save(transaction);
        }

        log.info("Rent payment applied. requestId={} receipt={} direct={} premium={} rate={}% commission={} net={}",
                request.getId(), mpesaReceiptNumber, collectsDirectly, premiumBilling,
                ratePercent, commissionAmount, netAmount);

        return new SuccessfulPaymentResult(
                request, transaction, ratePercent, commissionAmount, netAmount
        );
    }

    /**
     * Phase 1 dual revenue model: PREMIUM_MONTHLY landlords (including
     * during the grace window after a failed renewal) get zero commission
     * and 100% net disbursement. Fail-closed: any tenant we cannot resolve
     * is treated as COMMISSION (existing behavior). For COMMISSION
     * landlords the rate is whatever the active commission_policies row
     * holds (landlord override -&gt; platform default -&gt; null = no
     * commission) - never hardcoded here.
     */
    /**
     * True when this landlord's rent settles directly into their own M-Pesa
     * and never passes through the platform.
     *
     * <p>Fails closed toward DIRECT — a landlord we cannot resolve is assumed
     * NOT to be a custody arrangement, so no commission is taken from money
     * we may never have held.
     */
    private boolean collectsDirectly(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(com.rentmanager.modules.tenant.domain.model.Tenant::collectsDirectly)
                .orElse(true);
    }

    private boolean isPremiumMonthly(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(tenant -> tenant.getBillingMode() == BillingMode.PREMIUM_MONTHLY)
                .orElse(false);
    }

    @Transactional
    public void processFailedCallback(String checkoutRequestId, String reason) {
        RentPaymentRequest request = rentPaymentRequestRepository
                .findByMpesaCheckoutRequestId(checkoutRequestId)
                .orElse(null);

        if (request == null) {
            log.error("No RentPaymentRequest found for failed callback. CheckoutRequestID={}", checkoutRequestId);
            return;
        }

        if (request.getStatus() != RentPaymentRequestStatus.PENDING) {
            log.info("Duplicate failure callback — already processed. CheckoutRequestID={} status={}",
                    checkoutRequestId, request.getStatus());
            return;
        }

        request.markFailed();
        try {
            rentPaymentRequestRepository.save(request);
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic locking failure marking rent payment FAILED. " +
                    "CheckoutRequestID={} requestId={} — likely concurrent callback delivery, " +
                    "will self-heal on Safaricom retry.",
                    checkoutRequestId, request.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error persisting failed rent payment. " +
                    "CheckoutRequestID={} requestId={}",
                    checkoutRequestId, request.getId(), e);
            throw e;
        }

        log.warn("Rent payment failed. CheckoutRequestID={} Reason={}", checkoutRequestId, reason);
    }

    @Transactional
    public Disbursement createDisbursement(
            UUID tenantId, UUID leaseId, UUID ledgerEntryId,
            BigDecimal amount, String recipientPhone, String recipientName,
            String originatorConversationId
    ) {
        Disbursement disbursement = Disbursement.create(
                tenantId, leaseId, ledgerEntryId, amount, recipientPhone, recipientName, "BusinessPayment"
        );
        disbursement = disbursementRepository.save(disbursement);
        disbursement.markPending(originatorConversationId);
        disbursement = disbursementRepository.save(disbursement);
        return disbursement;
    }

    @Transactional
    public Disbursement createFailedDisbursement(
            UUID tenantId, UUID leaseId, UUID ledgerEntryId,
            BigDecimal amount, String recipientPhone, String recipientName,
            String failureReason
    ) {
        Disbursement disbursement = Disbursement.create(
                tenantId, leaseId, ledgerEntryId, amount, recipientPhone, recipientName, "BusinessPayment"
        );
        disbursement.markFailed(failureReason, null);
        disbursement = disbursementRepository.save(disbursement);
        return disbursement;
    }

    public record SuccessfulPaymentResult(
            RentPaymentRequest request,
            RentTransaction transaction,
            BigDecimal commissionRatePercent,
            BigDecimal commissionAmount,
            BigDecimal netAmount
    ) {}

    /**
     * Records money Safaricom confirmed but the ledger could not accept.
     *
     * <p>Runs in its OWN transaction: the caller reaches this only after
     * {@link #processSuccessfulCallback} threw, which rolled its transaction
     * back. Writing the parked row inside that rolled-back transaction would
     * discard it too, which is the failure this method exists to prevent.
     *
     * <p><b>Why parking rather than throwing.</b> The renter has already paid.
     * Letting the exception escape the callback meant three bad things at
     * once: the payment was recorded nowhere, Safaricom received an error and
     * retried a callback that could never succeed, and the only trace was a
     * stack trace in the application log. An unmatched payment is money the
     * system admits it holds and cannot yet attribute — visible under
     * GET /rent-ledger/unmatched-payments and resolvable by an owner, with
     * the resolution audited.
     *
     * <p>Deliberately swallows its own failures. If parking itself breaks,
     * the callback must still return 200: a retry would replay a payment the
     * ledger already rejected once, and the log line below is then the only
     * record — which is worse than this method working, and better than a
     * retry storm.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void parkUnappliedPayment(
            String checkoutRequestId,
            String mpesaReceiptNumber,
            java.math.BigDecimal amount,
            String phoneNumber,
            String reason
    ) {
        try {
            RentPaymentRequest request = rentPaymentRequestRepository
                    .findByMpesaCheckoutRequestId(checkoutRequestId)
                    .orElse(null);

            UUID tenantId = request == null ? null : request.getTenantId();

            UnmatchedPayment unmatched = UnmatchedPayment.create(
                    tenantId,
                    mpesaReceiptNumber,
                    amount,
                    phoneNumber,
                    request == null ? null : String.valueOf(request.getRentLedgerEntryId()),
                    java.time.LocalDateTime.now(),
                    checkoutRequestId,
                    mpesaReceiptNumber,
                    null,
                    "rent-callback: " + reason
            );
            unmatchedPaymentRepository.save(unmatched);

            log.warn("Rent payment could not be applied and was parked for manual resolution. "
                            + "checkoutRequestId={} receipt={} amount={} tenantId={} reason={}",
                    checkoutRequestId, mpesaReceiptNumber, amount, tenantId, reason);
        } catch (Exception e) {
            log.error("FAILED TO PARK an unapplied rent payment. The renter has paid and the "
                            + "system has no record of it. checkoutRequestId={} receipt={} amount={}",
                    checkoutRequestId, mpesaReceiptNumber, amount, e);
        }
    }
}
