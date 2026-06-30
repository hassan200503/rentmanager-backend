package com.rentmanager.modules.reservation.application.dto;

import com.rentmanager.modules.reservation.domain.enums.PaymentIntentStatus;

import java.util.UUID;

public record PaymentStatusResponse(
        UUID paymentIntentId,
        PaymentIntentStatus status,   // PENDING | PAID | FAILED | EXPIRED
        UUID reservationId            // non-null only when status = PAID
) {}