package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload.StkCallback;
import com.rentmanager.modules.notification.sms.PhoneMasker;
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
    private final com.rentmanager.modules.rentledger.application.service.B2CDisbursementService b2cDisbursementService;

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

        RentPaymentCallbackTransactionService.SuccessfulPaymentResult result;
        try {
            result = txService.processSuccessfulCallback(checkoutRequestId, mpesaReceiptNumber);
        } catch (Exception e) {
            // The renter has already paid. Letting this escape meant the
            // payment was recorded nowhere, Safaricom got an error and
            // retried a callback that could never succeed, and the only
            // trace was a stack trace.
            //
            // Observed in the wild: an STK push prompted against an entry
            // that was already PAID, so applying it threw "cannot modify a
            // PAID entry" with the money already gone from the renter's
            // phone. The initiation guard now prevents that specific cause;
            // this is the net under every other one — a concurrent payment,
            // an admin adjustment landing mid-flight, a lease change.
            log.error("Rent payment callback could not be applied — parking it. "
                            + "checkoutRequestId={} receipt={}",
                    checkoutRequestId, mpesaReceiptNumber, e);
            txService.parkUnappliedPayment(
                    checkoutRequestId,
                    mpesaReceiptNumber,
                    parseAmount(callback),
                    callback.getCallbackMetadata() == null
                            ? null : callback.getCallbackMetadata().getPhoneNumber(),
                    e.getMessage());
            // Returns normally so the controller answers 200. Safaricom must
            // not retry a callback that will fail identically every time.
            return;
        }

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

        // Defence in depth. netAmount is already left null upstream for a
        // DIRECT landlord, so this should be unreachable — but paying out
        // money the platform never received is the single worst thing this
        // system could do, and it is worth two lines to make it impossible
        // rather than merely unlikely. Fails closed: an unreadable landlord
        // is treated as DIRECT and no payout is attempted.
        if (landlord == null || landlord.collectsDirectly()) {
            log.debug("No disbursement for tenantId={} — rent settled directly to the landlord",
                    landlordTenantId);
            return;
        }
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
            // Delegates to the same path the manual payout uses (TD-116).
            // This method used to call darajaB2CService.initiateB2C(...) and
            // only afterwards write the Disbursement row — so a failure
            // between those two statements left money gone from the float
            // with nothing recording that it went, and no id to reconcile
            // against. It also applied no entitlement cap, while the manual
            // path refuses anything above settleableAmount.
            //
            // initiateDisbursement reserves entitlement under a
            // PESSIMISTIC_WRITE lock and persists the row INITIATED *before*
            // calling Daraja (ADR-0018), resolves the recipient server-side,
            // and writes a financial audit entry either way. Two payout paths
            // with different safety properties was the real defect; there is
            // now one.
            b2cDisbursementService.initiateDisbursement(
                    landlordTenantId, leaseId, ledgerEntryId, netAmount,
                    "BusinessPayment",
                    "Rent disbursement for " + result.request().getId()
            );
        } catch (Exception e) {
            // A failed payout must never fail the callback: the rent has
            // already been recorded and Safaricom must still get its 200,
            // or it retries a payment that has been applied.
            log.error("B2C disbursement failed for requestId={} netAmount={} recipient={}",
                    result.request().getId(), netAmount, PhoneMasker.mask(recipientPhone), e);

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

    /**
     * Best-effort amount from the callback metadata, for the parked record.
     * Never throws: this runs on the failure path, and losing the parked row
     * to a parse error would defeat the point of parking it.
     */
    private static java.math.BigDecimal parseAmount(StkCallback callback) {
        try {
            if (callback.getCallbackMetadata() == null) {
                return null;
            }
            String raw = callback.getCallbackMetadata().getAmount();
            return raw == null || raw.isBlank() ? null : new java.math.BigDecimal(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
