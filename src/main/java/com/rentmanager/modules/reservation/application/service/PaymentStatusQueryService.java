package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.reservation.application.dto.PaymentStatusResponse;

import java.util.UUID;

public interface PaymentStatusQueryService {
    PaymentStatusResponse getStatus(UUID paymentIntentId);
}