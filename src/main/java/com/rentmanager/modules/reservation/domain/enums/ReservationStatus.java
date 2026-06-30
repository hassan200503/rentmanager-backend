package com.rentmanager.modules.reservation.domain.enums;

public enum ReservationStatus {

    PENDING_PAYMENT,      // form submitted, STK push sent, waiting for payment
    DEPOSIT_PAID,         // M-Pesa callback confirmed, tenant account being created
    FULFILLING,           // processing
    COMPLETED,            // tenant account created, lease created, unit reserved
    FULFILLMENT_FAILED,   // payment succeeded but automation broke partway — needs human review, money was received
    CANCELLED             // expired before payment, or explicitly cancelled before payment
}