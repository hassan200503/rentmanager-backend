package com.rentmanager.modules.reservation.infrastructure.persistence.repository;

import com.rentmanager.modules.reservation.domain.enums.PaymentIntentStatus;
import com.rentmanager.modules.reservation.infrastructure.persistence.entity.PaymentIntentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentIntentJpaRepository extends JpaRepository<PaymentIntentJpaEntity, UUID> {

    Optional<PaymentIntentJpaEntity> findByMpesaCheckoutRequestId(String mpesaCheckoutRequestId);

    /**
     * Backs the scheduled stale-intent sweep. Derived query is sufficient
     * here — no explicit @Query needed since there's no locking requirement
     * (the sweep re-fetches each unit individually with a pessimistic lock
     * inside its own short transaction, rather than locking rows here).
     */
    List<PaymentIntentJpaEntity> findByStatusAndCreatedAtBefore(PaymentIntentStatus status, Instant cutoff);
}