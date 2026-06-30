package com.rentmanager.modules.reservation.infrastructure.persistence.repository;

import com.rentmanager.modules.reservation.infrastructure.persistence.entity.ReservationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, UUID> {

    Optional<ReservationJpaEntity> findByPaymentIntentId(UUID paymentIntentId);
}