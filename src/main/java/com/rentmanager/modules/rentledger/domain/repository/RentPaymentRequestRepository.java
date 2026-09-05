package com.rentmanager.modules.rentledger.domain.repository;

import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentPaymentRequestRepository {

    RentPaymentRequest save(RentPaymentRequest request);

    Optional<RentPaymentRequest> findById(UUID id);

    Optional<RentPaymentRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Matches an inbound Daraja callback back to the request that
     * initiated it — the equivalent lookup to
     * {@code PaymentIntentRepository.findByMpesaCheckoutRequestId} in the
     * deposit flow.
     */
    Optional<RentPaymentRequest> findByMpesaCheckoutRequestId(String checkoutRequestId);

    List<RentPaymentRequest> findByStatusAndCreatedAtBefore(RentPaymentRequestStatus status, Instant cutoff);

    /**
     * The most recent still-PENDING request against one ledger entry, used to
     * stop a second STK push being sent while the first is still live.
     *
     * <p>Without this, nothing deduped initiation: auto-pay re-sent a prompt
     * every morning for as long as an entry stayed unpaid, and a renter who
     * reloaded the payment page could start a second push by hand. Neither
     * double-charges by itself — each push needs the renter's PIN — but two
     * completed pushes produce two genuine M-Pesa receipts and therefore a
     * real overpayment, which is then only recoverable through the OVERPAID
     * admin resolution rather than prevented.
     */
    Optional<RentPaymentRequest> findLatestPendingForEntry(UUID tenantId, UUID rentLedgerEntryId);
}