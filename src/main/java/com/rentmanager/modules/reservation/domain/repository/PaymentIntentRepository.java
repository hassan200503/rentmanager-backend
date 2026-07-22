package com.rentmanager.modules.reservation.domain.repository;

import com.rentmanager.modules.reservation.domain.enums.PaymentIntentStatus;
import com.rentmanager.modules.reservation.domain.model.PaymentIntent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository {

    PaymentIntent save(PaymentIntent paymentIntent);

    Optional<PaymentIntent> findById(UUID id);

    // Used by M-Pesa callback to match incoming payment to the right intent
    Optional<PaymentIntent> findByMpesaCheckoutRequestId(String mpesaCheckoutRequestId);

    /**
     * Used by the scheduled stale-intent sweep to find PaymentIntents that
     * have been PENDING longer than the configured timeout — typically
     * because the M-Pesa callback was never delivered (ngrok down, server
     * restart mid-flight) rather than delivered-and-failed, which
     * MpesaCallbackService already handles directly and immediately.
     */
    List<PaymentIntent> findByStatusAndCreatedAtBefore(PaymentIntentStatus status, Instant cutoff);

    /**
     * Used by the orphaned-unit release sweep to find PaymentIntents in
     * terminal failure states whose units may still be PENDING_PAYMENT
     * (e.g. the M-Pesa callback marked the intent FAILED but failed to
     * release the unit due to a transient DB error that was swallowed).
     */
    List<PaymentIntent> findByStatusInAndCreatedAtBefore(List<PaymentIntentStatus> statuses, Instant cutoff);
}