package com.rentmanager.modules.reservation.domain.repository;

import com.rentmanager.modules.reservation.domain.model.Reservation;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {

    Reservation save(Reservation reservation);

    Optional<Reservation> findById(UUID id);

    Optional<Reservation> findByPaymentIntentId(UUID paymentIntentId);
}