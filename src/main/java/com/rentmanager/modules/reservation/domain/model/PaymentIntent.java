package com.rentmanager.modules.reservation.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.reservation.domain.enums.PaymentIntentStatus;

import java.math.BigDecimal;
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

    // Optimistic locking version, mirrored from PaymentIntentJpaEntity's
    // @Version column. Null until first persisted; Hibernate assigns 0
    // on initial insert. Carried through rehydrate() on every load so
    // concurrent callback deliveries against a stale version are detected.
    private Long version;

    protected PaymentIntent() {}

    // -------------------------------------------------------
    // FACTORY
    // -------------------------------------------------------
    public static PaymentIntent create(
            UUID unitId,
            UUID propertyId,
            String formDataJson,
            BigDecimal depositAmount
    ) {
        if (unitId == null) throw new IllegalArgumentException("unitId is required");
        if (propertyId == null) throw new IllegalArgumentException("propertyId is required");
        if (formDataJson == null || formDataJson.isBlank()) throw new IllegalArgumentException("formDataJson is required");
        if (depositAmount == null || depositAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("depositAmount must be > 0");

        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.unitId = unitId;
        intent.propertyId = propertyId;
        intent.formDataJson = formDataJson;
        intent.depositAmount = depositAmount;
        intent.status = PaymentIntentStatus.PENDING;
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

    // -------------------------------------------------------
    // GETTERS
    // -------------------------------------------------------
    public UUID getUnitId() { return unitId; }
    public UUID getPropertyId() { return propertyId; }
    public String getFormDataJson() { return formDataJson; }
    public String getMpesaCheckoutRequestId() { return mpesaCheckoutRequestId; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public PaymentIntentStatus getStatus() { return status; }
    public String getMpesaReceiptNumber() { return mpesaReceiptNumber; }
    public Long getVersion() { return version; }

    // -------------------------------------------------------
    // REHYDRATION
    // -------------------------------------------------------
    public static PaymentIntent rehydrate(
            UUID id,
            UUID unitId,
            UUID propertyId,
            String formDataJson,
            String mpesaCheckoutRequestId,
            BigDecimal depositAmount,
            PaymentIntentStatus status,
            String mpesaReceiptNumber,
            Long version
    ) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        intent.unitId = unitId;
        intent.propertyId = propertyId;
        intent.formDataJson = formDataJson;
        intent.mpesaCheckoutRequestId = mpesaCheckoutRequestId;
        intent.depositAmount = depositAmount;
        intent.status = status;
        intent.mpesaReceiptNumber = mpesaReceiptNumber;
        intent.version = version;
        return intent;
    }
}