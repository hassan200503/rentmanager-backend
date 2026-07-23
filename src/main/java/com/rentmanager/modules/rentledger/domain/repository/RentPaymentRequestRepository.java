package com.rentmanager.modules.rentledger.domain.repository;

import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;

import java.util.Optional;
import java.util.UUID;

public interface RentPaymentRequestRepository {

    RentPaymentRequest save(RentPaymentRequest request);

    Optional<RentPaymentRequest> findById(UUID id);

    Optional<RentPaymentRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Matches an inbound Daraja callback back to the request that
     * initiated it — the equivalent lookup to
     * {@code PaymentIntentRepository.findByMpesaCheckoutRequestId} in the
     * deposit flow.
     */
    Optional<RentPaymentRequest> findByMpesaCheckoutRequestId(String checkoutRequestId);
}