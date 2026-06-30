package com.rentmanager.modules.reservation.domain.repository;

import com.rentmanager.modules.reservation.domain.model.PaymentIntent;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository {

    PaymentIntent save(PaymentIntent paymentIntent);

    Optional<PaymentIntent> findById(UUID id);

    // Used by M-Pesa callback to match incoming payment to the right intent
    Optional<PaymentIntent> findByMpesaCheckoutRequestId(String mpesaCheckoutRequestId);
}