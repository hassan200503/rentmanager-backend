package com.rentmanager.modules.reservation.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.reservation.domain.enums.PaymentIntentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentIntent extends AggregateRoot {

    private UUID unitId;
    private UUID propertyId;

    // Raw JSON of the reservation form submission
    private String formDataJson;

    // Daraja STK Push checkout request ID — used to match the M-Pesa callback
    private String mpesaCheckoutRequestId;

    private BigDecimal depositAmount;

    private PaymentIntentStatus status;

    // Filled in after callback confirms payment
    private String mpesaReceiptNumber;

    // Set once at creation, never modified. Used by the scheduled
    // stale-intent sweep to detect PaymentIntents whose M-Pesa callback
    // was never delivered (as opposed to delivered-and-failed, which is
    // handled directly and immediately in MpesaCallbackService).
    private Instant createdAt;

    // Optimistic locking version, mirrored from PaymentIntentJpaEntity's
    // @Version column. Null until first persisted; Hibernate assigns 0
    // on initial insert. Carried through rehydrate() on every load so
    // concurrent callback deliveries against a stale version are detected.
    private Long version;

    protected PaymentIntent() {}

    // -------------------------------------------------------
    // FACTORY
    // -------------------------------------------------------
    // tenantId is the owning landlord's tenant, resolved by the caller from
    // the unit being reserved (UnitReservationTransactionService reads it
    // off the already row-locked Unit — the caller of this endpoint is an
    // unauthenticated prospective renter, so there is no TenantContext to
    // derive it from; it must come in from the unit's own tenant, not the
    // request). Assigned via the inherited BaseTenantEntity.assignTenant(),
    // which enforces once-only assignment.
    public static PaymentIntent create(
            UUID tenantId,
            UUID unitId,
            UUID propertyId,
            String formDataJson,
            BigDecimal depositAmount
    ) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (unitId == null) throw new IllegalArgumentException("unitId is required");
        if (propertyId == null) throw new IllegalArgumentException("propertyId is required");
        if (formDataJson == null || formDataJson.isBlank()) throw new IllegalArgumentException("formDataJson is required");
        if (depositAmount == null || depositAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("depositAmount must be > 0");

        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.assignTenant(tenantId);
        intent.unitId = unitId;
        intent.propertyId = propertyId;
        intent.formDataJson = formDataJson;
        intent.depositAmount = depositAmount;
        intent.status = PaymentIntentStatus.PENDING;
        // Set here rather than left to the JPA @CreationTimestamp so the
        // in-memory domain object is consistent immediately after create(),
        // before the first save() round-trip. The JPA layer's
        // @CreationTimestamp is the actual source of truth once persisted;
        // this value is overwritten by whatever rehydrate() loads back on
        // the next read, so any sub-millisecond drift between this and the
        // DB-assigned value is harmless.
        intent.createdAt = Instant.now();
        intent.version = null; // unpersisted; Hibernate assigns on first insert

        return intent;
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
        if (this.status != PaymentIntentStatus.PENDING) {
            throw new IllegalStateException("Only PENDING payment intents can be marked paid");
        }
        this.status = PaymentIntentStatus.PAID;
        this.mpesaReceiptNumber = mpesaReceiptNumber;
    }

    public void markFailed() {
        if (this.status != PaymentIntentStatus.PENDING) {
            throw new IllegalStateException("Only PENDING payment intents can be marked failed");
        }
        this.status = PaymentIntentStatus.FAILED;
    }

    public void expire() {
        if (this.status != PaymentIntentStatus.PENDING) return;
        this.status = PaymentIntentStatus.EXPIRED;
    }

    /**
     * Handles the case where a genuine, successful M-Pesa callback arrives
     * for an intent that the stale-intent sweep already expired (i.e. the
     * sweep's timeout guess was wrong for this particular delivery — the
     * customer did pay, the callback was just slow). Deliberately distinct
     * from markPaid(): no reservation is created and the unit is not
     * touched here, since by this point the unit may have already been
     * legitimately released and re-taken by a different applicant. This
     * only records that money arrived so it surfaces for manual
     * reconciliation/refund review — see MpesaCallbackService's CRITICAL
     * log at the call site for the operational alert.
     */
    public void markPaidAfterExpiry(String mpesaReceiptNumber) {
        if (this.status != PaymentIntentStatus.EXPIRED) {
            throw new IllegalStateException("Only EXPIRED payment intents can be marked paid-after-expiry");
        }
        this.status = PaymentIntentStatus.PAID_AFTER_EXPIRY;
        this.mpesaReceiptNumber = mpesaReceiptNumber;
    }

    // -------------------------------------------------------
    // GETTERS
    // -------------------------------------------------------
    // getTenantId() is inherited from BaseTenantEntity — not redeclared here.
    public UUID getUnitId() { return unitId; }
    public UUID getPropertyId() { return propertyId; }
    public String getFormDataJson() { return formDataJson; }
    public String getMpesaCheckoutRequestId() { return mpesaCheckoutRequestId; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public PaymentIntentStatus getStatus() { return status; }
    public String getMpesaReceiptNumber() { return mpesaReceiptNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }

    // -------------------------------------------------------
    // REHYDRATION
    // -------------------------------------------------------
    public static PaymentIntent rehydrate(
            UUID id,
            UUID tenantId,
            UUID unitId,
            UUID propertyId,
            String formDataJson,
            String mpesaCheckoutRequestId,
            BigDecimal depositAmount,
            PaymentIntentStatus status,
            String mpesaReceiptNumber,
            Instant createdAt,
            Long version
    ) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        intent.restoreTenantId(tenantId);
        intent.unitId = unitId;
        intent.propertyId = propertyId;
        intent.formDataJson = formDataJson;
        intent.mpesaCheckoutRequestId = mpesaCheckoutRequestId;
        intent.depositAmount = depositAmount;
        intent.status = status;
        intent.mpesaReceiptNumber = mpesaReceiptNumber;
        intent.createdAt = createdAt;
        intent.version = version;
        return intent;
    }
}