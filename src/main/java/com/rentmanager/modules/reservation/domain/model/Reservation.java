package com.rentmanager.modules.reservation.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.reservation.domain.enums.ReservationStatus;
import com.rentmanager.modules.reservation.domain.event.ReservationDepositPaidEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class Reservation extends AggregateRoot {

    private UUID unitId;
    private UUID propertyId;

    private String fullName;
    private String phone;
    private String email;
    private String nationalId;
    private String mpesaPhone;

    private LocalDate moveInDate;
    private BigDecimal depositAmount;

    private ReservationStatus status;

    private String mpesaReceiptNumber;
    private String clerkUserId;
    private UUID paymentIntentId;

    // Set only if fulfillment fails, for support/diagnostic visibility
    private String fulfillmentFailureReason;

    // Optimistic locking version, mirrored from ReservationJpaEntity's
    // @Version column. Null until first persisted; Hibernate assigns 0
    // on initial insert. Carried through rehydrate() on every load so
    // concurrent saves against a stale version are detected.
    private Long version;

    protected Reservation() {}

    public static Reservation create(
            UUID unitId,
            UUID propertyId,
            String fullName,
            String phone,
            String email,
            String nationalId,
            String mpesaPhone,
            LocalDate moveInDate,
            BigDecimal depositAmount,
            UUID paymentIntentId
    ) {
        if (unitId == null) throw new IllegalArgumentException("unitId is required");
        if (propertyId == null) throw new IllegalArgumentException("propertyId is required");
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("fullName is required");
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("phone is required");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email is required");
        if (nationalId == null || nationalId.isBlank()) throw new IllegalArgumentException("nationalId is required");
        if (mpesaPhone == null || mpesaPhone.isBlank()) throw new IllegalArgumentException("mpesaPhone is required");
        if (moveInDate == null) throw new IllegalArgumentException("moveInDate is required");
        if (depositAmount == null || depositAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("depositAmount must be > 0");
        if (paymentIntentId == null) throw new IllegalArgumentException("paymentIntentId is required");

        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());

        reservation.unitId = unitId;
        reservation.propertyId = propertyId;
        reservation.fullName = fullName;
        reservation.phone = phone;
        reservation.email = email;
        reservation.nationalId = nationalId;
        reservation.mpesaPhone = mpesaPhone;
        reservation.moveInDate = moveInDate;
        reservation.depositAmount = depositAmount;
        reservation.paymentIntentId = paymentIntentId;
        reservation.status = ReservationStatus.PENDING_PAYMENT;
        reservation.version = null; // unpersisted; Hibernate assigns on first insert

        return reservation;
    }

    public void markDepositPaid(String mpesaReceiptNumber) {
        if (this.status != ReservationStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only PENDING_PAYMENT reservations can be marked paid");
        }
        if (mpesaReceiptNumber == null || mpesaReceiptNumber.isBlank()) {
            throw new IllegalArgumentException("mpesaReceiptNumber is required");
        }

        this.status = ReservationStatus.DEPOSIT_PAID;
        this.mpesaReceiptNumber = mpesaReceiptNumber;

        registerEvent(new ReservationDepositPaidEvent(
                getId(),
                unitId,
                propertyId,
                fullName,
                phone,
                email,
                nationalId,
                mpesaPhone,
                mpesaReceiptNumber,
                depositAmount,
                moveInDate
        ));
    }

    public void markFulfilling() {
        if (this.status != ReservationStatus.DEPOSIT_PAID) {
            throw new IllegalStateException(
                    "Only DEPOSIT_PAID reservations can begin fulfillment"
            );
        }

        this.status = ReservationStatus.FULFILLING;
    }

    public void complete(String clerkUserId) {
        if (this.status != ReservationStatus.FULFILLING) {
            throw new IllegalStateException("Only FULFILLING reservations can be completed");
        }
        if (clerkUserId == null || clerkUserId.isBlank()) {
            throw new IllegalArgumentException("clerkUserId is required");
        }

        this.status = ReservationStatus.COMPLETED;
        this.clerkUserId = clerkUserId;
    }

    /**
     * Called by the fulfillment saga's compensation handler when an
     * unrecoverable error occurs partway through fulfillment, after
     * compensating actions for whatever DID complete have been run.
     * This does NOT mean no money changed hands — the deposit was already
     * paid. It means automation broke and a human needs to follow up
     * (refund, manual account creation, or retry).
     *
     * CHANGED (project handoff §1, decision 2 shape (a)): this is now a
     * tolerant no-op rather than a guard exception when the reservation
     * isn't in FULFILLING. ReservationFulfillmentStepZeroService now
     * commits the FULFILLING transition independently before the rest of
     * the saga runs, so in normal operation this method should always see
     * FULFILLING when compensation calls it. But compensate() runs in its
     * own transaction and cannot see every possible interleaving of
     * concurrent writes, so this must not throw a surprising exception for
     * a visibility situation it doesn't control — better to no-op and let
     * the caller log accordingly than to produce a misleading "CRITICAL /
     * UNKNOWN state" alarm for what may just be a timing artifact.
     *
     * @return true if the transition to FULFILLMENT_FAILED actually
     *         happened; false if this was a tolerated no-op (already
     *         FULFILLMENT_FAILED, or not currently FULFILLING).
     */
    public boolean markFulfillmentFailed(String reason) {
        if (this.status == ReservationStatus.FULFILLMENT_FAILED) {
            // Already marked — idempotent no-op (e.g. compensation ran twice).
            return false;
        }
        if (this.status != ReservationStatus.FULFILLING) {
            // See method javadoc above — tolerated, not an error.
            return false;
        }

        this.status = ReservationStatus.FULFILLMENT_FAILED;
        this.fulfillmentFailureReason = reason;
        return true;
    }

    public void cancel() {
        if (this.status == ReservationStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed reservation");
        }
        if (this.status == ReservationStatus.FULFILLMENT_FAILED) {
            throw new IllegalStateException(
                    "Cannot cancel a reservation with failed fulfillment — payment was received, this needs manual resolution, not cancellation"
            );
        }
        this.status = ReservationStatus.CANCELLED;
    }

    public UUID getUnitId() { return unitId; }
    public UUID getPropertyId() { return propertyId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getNationalId() { return nationalId; }
    public String getMpesaPhone() { return mpesaPhone; }
    public LocalDate getMoveInDate() { return moveInDate; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public ReservationStatus getStatus() { return status; }
    public String getMpesaReceiptNumber() { return mpesaReceiptNumber; }
    public String getClerkUserId() { return clerkUserId; }
    public UUID getPaymentIntentId() { return paymentIntentId; }
    public String getFulfillmentFailureReason() { return fulfillmentFailureReason; }
    public Long getVersion() { return version; }

    public static Reservation rehydrate(
            UUID id,
            UUID unitId,
            UUID propertyId,
            String fullName,
            String phone,
            String email,
            String nationalId,
            String mpesaPhone,
            LocalDate moveInDate,
            BigDecimal depositAmount,
            ReservationStatus status,
            String mpesaReceiptNumber,
            String clerkUserId,
            UUID paymentIntentId,
            String fulfillmentFailureReason,
            Long version
    ) {
        Reservation r = new Reservation();
        r.setId(id);
        r.unitId = unitId;
        r.propertyId = propertyId;
        r.fullName = fullName;
        r.phone = phone;
        r.email = email;
        r.nationalId = nationalId;
        r.mpesaPhone = mpesaPhone;
        r.moveInDate = moveInDate;
        r.depositAmount = depositAmount;
        r.status = status;
        r.mpesaReceiptNumber = mpesaReceiptNumber;
        r.clerkUserId = clerkUserId;
        r.paymentIntentId = paymentIntentId;
        r.fulfillmentFailureReason = fulfillmentFailureReason;
        r.version = version;
        return r;
    }
}