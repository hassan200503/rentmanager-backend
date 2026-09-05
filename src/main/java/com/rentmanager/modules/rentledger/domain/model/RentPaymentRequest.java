package com.rentmanager.modules.rentledger.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps a Daraja STK-push checkout request back to the {@code RentLedgerEntry}
 * it's paying toward. Exists because Daraja's callback only carries
 * CheckoutRequestID, ResultCode, amount, and receipt number — nothing that
 * identifies which ledger entry the payment is for. Mirrors
 * {@code PaymentIntent}'s role in the deposit flow (same problem, one
 * module over) as closely as the two use cases allow.
 */
public class RentPaymentRequest extends AggregateRoot {

    private static final String DEFAULT_CURRENCY = "KES";

    private UUID leaseId;
    private UUID rentLedgerEntryId;
    private BigDecimal amount;
    private String mpesaCheckoutRequestId;
    private RentPaymentRequestStatus status;
    private String mpesaReceiptNumber;
    private Instant createdAt;
    private String currency;
    private Long version;

    protected RentPaymentRequest() {
    }

    // -------------------------------------------------------
    // FACTORY
    // -------------------------------------------------------

    /**
     * Shorter overload defaulting {@code currency} to {@link #DEFAULT_CURRENCY}
     * — see the currency-explicit overload below, used by the real STK-push
     * initiation call site with the currency of the RentLedgerEntry being
     * paid toward.
     */
    public static RentPaymentRequest create(
            UUID tenantId,
            UUID leaseId,
            UUID rentLedgerEntryId,
            BigDecimal amount
    ) {
        return create(tenantId, leaseId, rentLedgerEntryId, amount, DEFAULT_CURRENCY);
    }

    public static RentPaymentRequest create(
            UUID tenantId,
            UUID leaseId,
            UUID rentLedgerEntryId,
            BigDecimal amount,
            String currency
    ) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (leaseId == null) throw new IllegalArgumentException("leaseId is required");
        if (rentLedgerEntryId == null) throw new IllegalArgumentException("rentLedgerEntryId is required");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("amount must be > 0");

        RentPaymentRequest request = new RentPaymentRequest();
        request.setId(UUID.randomUUID());
        request.assignTenant(tenantId);
        request.leaseId = leaseId;
        request.rentLedgerEntryId = rentLedgerEntryId;
        request.amount = amount;
        request.status = RentPaymentRequestStatus.PENDING;
        request.createdAt = Instant.now();
        request.currency = currency != null ? currency : DEFAULT_CURRENCY;
        request.version = null; // unpersisted; Hibernate assigns on first insert

        return request;
    }

    // -------------------------------------------------------
    // BEHAVIOUR
    // -------------------------------------------------------

    public void attachCheckoutRequestId(String mpesaCheckoutRequestId) {
        if (mpesaCheckoutRequestId == null || mpesaCheckoutRequestId.isBlank()) {
            throw new IllegalArgumentException("mpesaCheckoutRequestId is required");
        }
        this.mpesaCheckoutRequestId = mpesaCheckoutRequestId;
    }

    public void markPaid(String mpesaReceiptNumber) {
        if (this.status != RentPaymentRequestStatus.PENDING) {
            throw new IllegalStateException("Only PENDING rent payment requests can be marked paid");
        }
        this.status = RentPaymentRequestStatus.PAID;
        this.mpesaReceiptNumber = mpesaReceiptNumber;
    }

    public void markFailed() {
        if (this.status != RentPaymentRequestStatus.PENDING) {
            throw new IllegalStateException("Only PENDING rent payment requests can be marked failed");
        }
        this.status = RentPaymentRequestStatus.FAILED;
    }

    // -------------------------------------------------------
    // GETTERS
    // -------------------------------------------------------
    // getTenantId() is inherited from AggregateRoot — not redeclared here.
    public UUID getLeaseId() { return leaseId; }
    public UUID getRentLedgerEntryId() { return rentLedgerEntryId; }
    public BigDecimal getAmount() { return amount; }
    public String getMpesaCheckoutRequestId() { return mpesaCheckoutRequestId; }
    public RentPaymentRequestStatus getStatus() { return status; }
    public String getMpesaReceiptNumber() { return mpesaReceiptNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCurrency() { return currency; }
    public Long getVersion() { return version; }

    // -------------------------------------------------------
    // REHYDRATION
    // -------------------------------------------------------

    public static RentPaymentRequest rehydrate(
            UUID id,
            UUID tenantId,
            UUID leaseId,
            UUID rentLedgerEntryId,
            BigDecimal amount,
            String mpesaCheckoutRequestId,
            RentPaymentRequestStatus status,
            String mpesaReceiptNumber,
            Instant createdAt,
            String currency,
            Long version
    ) {
        RentPaymentRequest request = new RentPaymentRequest();
        request.setId(id);
        request.restoreTenantId(tenantId);
        request.leaseId = leaseId;
        request.rentLedgerEntryId = rentLedgerEntryId;
        request.amount = amount;
        request.mpesaCheckoutRequestId = mpesaCheckoutRequestId;
        request.status = status;
        request.mpesaReceiptNumber = mpesaReceiptNumber;
        request.createdAt = createdAt;
        request.currency = currency != null ? currency : DEFAULT_CURRENCY;
        request.version = version;
        return request;
    }
}