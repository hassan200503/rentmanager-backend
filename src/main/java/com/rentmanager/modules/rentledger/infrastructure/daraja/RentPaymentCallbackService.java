package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload.StkCallback;
import com.rentmanager.modules.rentledger.application.service.CommissionPolicyService;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentPaymentCallbackService {

    private final RentPaymentCallbackTransactionService txService;
    private final TenantRepository tenantRepository;
    private final DarajaB2CService darajaB2CService;

    public void handle(MpesaCallbackPayload payload) {
        StkCallback callback = payload.getBody().getStkCallback();
        String checkoutRequestId = callback.getCheckoutRequestId();

        log.info("Rent payment M-Pesa callback received. CheckoutRequestID={} ResultCode={}",
                checkoutRequestId, callback.getResultCode());

        if (!callback.isSuccessful()) {
            handleFailure(checkoutRequestId, callback.getResultDesc());
            return;
        }

        String mpesaReceiptNumber = callback.getCallbackMetadata() != null
                ? callback.getCallbackMetadata().getMpesaReceiptNumber()
                : null;

        if (mpesaReceiptNumber == null) {
            handleFailure(checkoutRequestId, "No MpesaReceiptNumber in callback");
            return;
        }

        RentPaymentCallbackTransactionService.SuccessfulPaymentResult result =
                txService.processSuccessfulCallback(checkoutRequestId, mpesaReceiptNumber);

        if (result == null) {
            log.info("Duplicate callback — already processed. CheckoutRequestID={}", checkoutRequestId);
            return;
        }

        initiateB2CIfNeeded(result);
    }

    private void initiateB2CIfNeeded(RentPaymentCallbackTransactionService.SuccessfulPaymentResult result) {
        BigDecimal netAmount = result.netAmount();
        if (netAmount == null || netAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("No B2C disbursement needed for requestId={} netAmount={}",
                    result.request().getId(), netAmount);
            return;
        }

        UUID landlordTenantId = result.request().getTenantId();
        Tenant landlord = tenantRepository.findById(landlordTenantId).orElse(null);
        if (landlord == null || landlord.getPayoutPhoneNumber() == null || landlord.getPayoutPhoneNumber().isBlank()) {
            log.warn("Landlord {} has no payout phone number configured — cannot disburse net rent of {}",
                    landlordTenantId, netAmount);
            return;
        }

        String recipientPhone = landlord.getPayoutPhoneNumber();
        String recipientName = landlord.getName();
        UUID leaseId = result.request().getLeaseId();
        UUID ledgerEntryId = result.request().getRentLedgerEntryId();

        try {
            String originatorConversationId = darajaB2CService.initiateB2C(
                    netAmount, recipientPhone, recipientName,
                    "Rent disbursement for " + result.request().getId(),
                    "BusinessPayment"
            );

            txService.createDisbursement(
                    landlordTenantId, leaseId, ledgerEntryId,
                    netAmount, recipientPhone, recipientName,
                    originatorConversationId
            );

            log.info("B2C disbursement initiated. requestId={} netAmount={} recipient={} conversationId={}",
                    result.request().getId(), netAmount, recipientPhone, originatorConversationId);
        } catch (Exception e) {
            log.error("B2C disbursement failed for requestId={} netAmount={} recipient={}",
                    result.request().getId(), netAmount, recipientPhone, e);

            txService.createFailedDisbursement(
                    landlordTenantId, leaseId, ledgerEntryId,
                    netAmount, recipientPhone, recipientName,
                    "B2C initiation failed: " + e.getMessage()
            );
        }
    }

    private void handleFailure(String checkoutRequestId, String reason) {
        log.warn("Rent payment M-Pesa callback reported failure. CheckoutRequestID={} Reason={}",
                checkoutRequestId, reason);
        txService.processFailedCallback(checkoutRequestId, reason);
    }
}
