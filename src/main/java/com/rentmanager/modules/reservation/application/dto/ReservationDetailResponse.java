package com.rentmanager.modules.reservation.application.dto;

import com.rentmanager.modules.reservation.domain.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationDetailResponse(
        UUID reservationId,
        String fullName,
        String phone,
        String email,
        String nationalId,
        String mpesaPhone,
        LocalDate moveInDate,
        BigDecimal depositAmount,
        ReservationStatus status,
        String mpesaReceiptNumber,
        UUID paymentIntentId,
        String unitNumber,
        String propertyName
) {}
