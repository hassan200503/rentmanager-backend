package com.rentmanager.modules.reservation.infrastructure.persistence.repository;

import com.rentmanager.modules.reservation.infrastructure.persistence.entity.PaymentIntentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentIntentJpaRepository extends JpaRepository<PaymentIntentJpaEntity, UUID> {

    Optional<PaymentIntentJpaEntity> findByMpesaCheckoutRequestId(String mpesaCheckoutRequestId);
}