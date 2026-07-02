package com.rentmanager.modules.reservation.infrastructure.persistence.adapter;

import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import com.rentmanager.modules.reservation.infrastructure.persistence.entity.ReservationJpaEntity;
import com.rentmanager.modules.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReservationRepositoryImpl implements ReservationRepository {

    private final ReservationJpaRepository jpaRepository;

    @Override
    public Reservation save(Reservation reservation) {
        ReservationJpaEntity entity = toEntity(reservation);
        ReservationJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Reservation> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Reservation> findByPaymentIntentId(UUID paymentIntentId) {
        return jpaRepository.findByPaymentIntentId(paymentIntentId).map(this::toDomain);
    }

    // -------------------------------------------------------
    // MAPPING
    // -------------------------------------------------------
    private ReservationJpaEntity toEntity(Reservation r) {
        return ReservationJpaEntity.builder()
                .id(r.getId())
                .unitId(r.getUnitId())
                .propertyId(r.getPropertyId())
                .fullName(r.getFullName())
                .phone(r.getPhone())
                .email(r.getEmail())
                .nationalId(r.getNationalId())
                .mpesaPhone(r.getMpesaPhone())
                .moveInDate(r.getMoveInDate())
                .depositAmount(r.getDepositAmount())
                .status(r.getStatus())
                .mpesaReceiptNumber(r.getMpesaReceiptNumber())
                .clerkUserId(r.getClerkUserId())
                .paymentIntentId(r.getPaymentIntentId())
                .fulfillmentFailureReason(r.getFulfillmentFailureReason())
                .version(r.getVersion())
                .build();
    }

    private Reservation toDomain(ReservationJpaEntity e) {
        return Reservation.rehydrate(
                e.getId(),
                e.getUnitId(),
                e.getPropertyId(),
                e.getFullName(),
                e.getPhone(),
                e.getEmail(),
                e.getNationalId(),
                e.getMpesaPhone(),
                e.getMoveInDate(),
                e.getDepositAmount(),
                e.getStatus(),
                e.getMpesaReceiptNumber(),
                e.getClerkUserId(),
                e.getPaymentIntentId(),
                e.getFulfillmentFailureReason(),
                e.getVersion()
        );
    }





    @Override
    public void flush() {
        jpaRepository.flush();
    }
}