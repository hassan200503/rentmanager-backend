package com.rentmanager.modules.reservation.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Fired after M-Pesa callback confirms payment.
// Listeners: create tenant Clerk account, create lease, mark unit reserved.
public class ReservationDepositPaidEvent extends DomainEvent {

    private final UUID unitId;
    private final UUID propertyId;
    private final String fullName;
    private final String phone;
    private final String email;
    private final String nationalId;
    private final String mpesaPhone;
    private final String mpesaReceiptNumber;
    private final BigDecimal depositAmount;
    private final LocalDate moveInDate;

    public ReservationDepositPaidEvent(
            UUID reservationId,
            UUID unitId,
            UUID propertyId,
            String fullName,
            String phone,
            String email,
            String nationalId,
            String mpesaPhone,
            String mpesaReceiptNumber,
            BigDecimal depositAmount,
            LocalDate moveInDate
    ) {
        super(null, reservationId, "SYSTEM"); // no tenantId at reservation time
        this.unitId = unitId;
        this.propertyId = propertyId;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.nationalId = nationalId;
        this.mpesaPhone = mpesaPhone;
        this.mpesaReceiptNumber = mpesaReceiptNumber;
        this.depositAmount = depositAmount;
        this.moveInDate = moveInDate;
    }

    @Override
    public String eventType() {
        return "RESERVATION_DEPOSIT_PAID";
    }

    public UUID getUnitId() { return unitId; }
    public UUID getPropertyId() { return propertyId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getNationalId() { return nationalId; }
    public String getMpesaPhone() { return mpesaPhone; }
    public String getMpesaReceiptNumber() { return mpesaReceiptNumber; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public LocalDate getMoveInDate() { return moveInDate; }
}