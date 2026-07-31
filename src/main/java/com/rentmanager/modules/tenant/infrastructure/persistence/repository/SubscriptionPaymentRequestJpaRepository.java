package com.rentmanager.modules.tenant.infrastructure.persistence.repository;

import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentRequestStatus;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.SubscriptionPaymentRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPaymentRequestJpaRepository
        extends JpaRepository<SubscriptionPaymentRequestJpaEntity, UUID> {

    Optional<SubscriptionPaymentRequestJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<SubscriptionPaymentRequestJpaEntity> findByMpesaCheckoutRequestId(String mpesaCheckoutRequestId);

    Optional<SubscriptionPaymentRequestJpaEntity> findByMpesaReceiptNumber(String mpesaReceiptNumber);

    Optional<SubscriptionPaymentRequestJpaEntity> findFirstByTenantIdAndPurposeAndStatusOrderByCreatedAtDesc(
            UUID tenantId, SubscriptionPaymentPurpose purpose, SubscriptionPaymentRequestStatus status
    );

    List<SubscriptionPaymentRequestJpaEntity> findByStatusAndCreatedAtBefore(
            SubscriptionPaymentRequestStatus status, Instant cutoff
    );
}
