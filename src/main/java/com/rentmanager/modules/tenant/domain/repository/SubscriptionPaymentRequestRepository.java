package com.rentmanager.modules.tenant.domain.repository;

import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentRequestStatus;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPaymentRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPaymentRequestRepository {

    SubscriptionPaymentRequest save(SubscriptionPaymentRequest request);

    Optional<SubscriptionPaymentRequest> findById(UUID id);

    /** Ownership-scoped lookup for the landlord polling endpoint. */
    Optional<SubscriptionPaymentRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<SubscriptionPaymentRequest> findByMpesaCheckoutRequestId(String checkoutRequestId);

    /** C2B idempotency: Daraja may redeliver a confirmation for the same TransID. */
    Optional<SubscriptionPaymentRequest> findByMpesaReceiptNumber(String mpesaReceiptNumber);

    /** Pending guard: a request already in flight for this tenant/purpose. */
    Optional<SubscriptionPaymentRequest> findPendingByTenantIdAndPurpose(
            UUID tenantId, SubscriptionPaymentPurpose purpose
    );

    /** Stale-request sweep candidates (no callback within the window). */
    List<SubscriptionPaymentRequest> findByStatusAndCreatedAtBefore(
            SubscriptionPaymentRequestStatus status, Instant cutoff
    );
}
