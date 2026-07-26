package com.rentmanager.modules.reservation.infrastructure.persistence.adapter;

import com.rentmanager.modules.reservation.domain.enums.PaymentIntentStatus;
import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.reservation.domain.repository.PaymentIntentRepository;
import com.rentmanager.modules.reservation.infrastructure.persistence.entity.PaymentIntentJpaEntity;
import com.rentmanager.modules.reservation.infrastructure.persistence.repository.PaymentIntentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
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

    @Override
    public List<PaymentIntent> findByStatusAndCreatedAtBefore(PaymentIntentStatus status, Instant cutoff) {
        return jpaRepository.findByStatusAndCreatedAtBefore(status, cutoff)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PaymentIntent> findByStatusInAndCreatedAtBefore(List<PaymentIntentStatus> statuses, Instant cutoff) {
        return jpaRepository.findByStatusInAndCreatedAtBefore(statuses, cutoff)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void delete(PaymentIntent paymentIntent) {
        PaymentIntentJpaEntity entity = toEntity(paymentIntent);
        jpaRepository.delete(entity);
    }

    // -------------------------------------------------------
    // MAPPING
    // -------------------------------------------------------
    private PaymentIntentJpaEntity toEntity(PaymentIntent i) {
        return PaymentIntentJpaEntity.builder()
                .id(i.getId())
                .tenantId(i.getTenantId())
                .unitId(i.getUnitId())
                .propertyId(i.getPropertyId())
                .formDataJson(i.getFormDataJson())
                .mpesaCheckoutRequestId(i.getMpesaCheckoutRequestId())
                .depositAmount(i.getDepositAmount())
                .status(i.getStatus())
                .mpesaReceiptNumber(i.getMpesaReceiptNumber())
                // Passed through for completeness, but functionally inert on
                // both paths: on INSERT, @CreationTimestamp on the entity
                // generates the authoritative value regardless of what's set
                // here; on UPDATE, the column is marked updatable=false so
                // Hibernate excludes it from the UPDATE statement entirely.
                // The DB-assigned value is always the source of truth —
                // never this domain-side value.
                .createdAt(i.getCreatedAt())
                .version(i.getVersion())
                .build();
    }

    private PaymentIntent toDomain(PaymentIntentJpaEntity e) {
        return PaymentIntent.rehydrate(
                e.getId(),
                e.getTenantId(),
                e.getUnitId(),
                e.getPropertyId(),
                e.getFormDataJson(),
                e.getMpesaCheckoutRequestId(),
                e.getDepositAmount(),
                e.getStatus(),
                e.getMpesaReceiptNumber(),
                e.getCreatedAt(),
                e.getVersion()
        );
    }
}