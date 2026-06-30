package com.rentmanager.modules.reservation.infrastructure.persistence.adapter;

import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.reservation.domain.repository.PaymentIntentRepository;
import com.rentmanager.modules.reservation.infrastructure.persistence.entity.PaymentIntentJpaEntity;
import com.rentmanager.modules.reservation.infrastructure.persistence.repository.PaymentIntentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentIntentRepositoryImpl implements PaymentIntentRepository {

    private final PaymentIntentJpaRepository jpaRepository;

    @Override
    public PaymentIntent save(PaymentIntent intent) {
        PaymentIntentJpaEntity entity = toEntity(intent);
        PaymentIntentJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<PaymentIntent> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<PaymentIntent> findByMpesaCheckoutRequestId(String mpesaCheckoutRequestId) {
        return jpaRepository.findByMpesaCheckoutRequestId(mpesaCheckoutRequestId).map(this::toDomain);
    }

    // -------------------------------------------------------
    // MAPPING
    // -------------------------------------------------------

    private PaymentIntentJpaEntity toEntity(PaymentIntent i) {
        return PaymentIntentJpaEntity.builder()
                .id(i.getId())
                .unitId(i.getUnitId())
                .propertyId(i.getPropertyId())   // <-- add this
                .formDataJson(i.getFormDataJson())
                .mpesaCheckoutRequestId(i.getMpesaCheckoutRequestId())
                .depositAmount(i.getDepositAmount())
                .status(i.getStatus())
                .mpesaReceiptNumber(i.getMpesaReceiptNumber())
                .build();
    }

    private PaymentIntent toDomain(PaymentIntentJpaEntity e) {
        return PaymentIntent.rehydrate(
                e.getId(),
                e.getUnitId(),
                e.getPropertyId(),
                e.getFormDataJson(),
                e.getMpesaCheckoutRequestId(),
                e.getDepositAmount(),
                e.getStatus(),
                e.getMpesaReceiptNumber()
        );
    }
}