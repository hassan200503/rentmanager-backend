package com.rentmanager.modules.reservation.domain.enums;

public enum PaymentIntentStatus {

    PENDING,   // STK push sent, waiting for customer PIN
    PAID,      // M-Pesa callback confirmed payment
    FAILED,    // M-Pesa callback reported failure
    EXPIRED    // customer did not pay within timeout window
}