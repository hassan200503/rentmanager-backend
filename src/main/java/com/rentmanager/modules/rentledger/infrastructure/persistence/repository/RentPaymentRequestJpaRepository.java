package com.rentmanager.modules.rentledger.infrastructure.persistence.repository;

import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentPaymentRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RentPaymentRequestJpaRepository extends JpaRepository<RentPaymentRequestJpaEntity, UUID> {

    Optional<RentPaymentRequestJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    // Matches an inbound Daraja callback back to the request that
    // initiated it — the rent-payment equivalent of
    // PaymentIntentJpaRepository.findByMpesaCheckoutRequestId.
    Optional<RentPaymentRequestJpaEntity> findByMpesaCheckoutRequestId(String mpesaCheckoutRequestId);

    List<RentPaymentRequestJpaEntity> findByStatusAndCreatedAtBefore(RentPaymentRequestStatus status, Instant cutoff);
}