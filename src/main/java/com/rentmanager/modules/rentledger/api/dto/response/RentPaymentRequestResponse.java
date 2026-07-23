package com.rentmanager.modules.rentledger.api.dto.response;

import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Returned right after initiation (id + PENDING status, for the frontend to
 * poll) and from the status-polling endpoint (PaymentStatusResponse's
 * rent-payment equivalent).
 */
public record RentPaymentRequestResponse(
        UUID id,
        UUID leaseId,
        UUID rentLedgerEntryId,
        BigDecimal amount,
        RentPaymentRequestStatus status,
        String mpesaReceiptNumber
) {
    public static RentPaymentRequestResponse from(RentPaymentRequest request) {
        return new RentPaymentRequestResponse(
                request.getId(),
                request.getLeaseId(),
                request.getRentLedgerEntryId(),
                request.getAmount(),
                request.getStatus(),
                request.getMpesaReceiptNumber()
        );
    }
}
