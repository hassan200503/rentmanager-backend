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
    private final RentLedgerApplicationService rentLedgerApplicationService;
    private final RentTransactionRepository rentTransactionRepository;
    private final CommissionPolicyService commissionPolicyService;
    private final DisbursementRepository disbursementRepository;
    private final EntityManager entityManager;

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

        BigDecimal ratePercent = commissionPolicyService.getActiveRate(request.getTenantId());
        BigDecimal commissionAmount = null;
        BigDecimal netAmount = null;

        if (ratePercent != null) {
            commissionAmount = CommissionPolicyService.computeCommission(request.getAmount(), ratePercent);
            netAmount = CommissionPolicyService.computeNetAmount(request.getAmount(), commissionAmount);
            transaction.applyCommission(ratePercent, commissionAmount, netAmount);
            rentTransactionRepository.save(transaction);
        }

        log.info("Rent payment applied. requestId={} receipt={} rate={}% commission={} net={}",
                request.getId(), mpesaReceiptNumber, ratePercent, commissionAmount, netAmount);

        return new SuccessfulPaymentResult(
                request, transaction, ratePercent, commissionAmount, netAmount
        );
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
}
