package com.rentmanager.modules.tenant.infrastructure.persistence.adapter;

import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentRequestStatus;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPaymentRequest;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPaymentRequestRepository;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.SubscriptionPaymentRequestJpaEntity;
import com.rentmanager.modules.tenant.infrastructure.persistence.mapper.SubscriptionPaymentRequestPersistenceMapper;
import com.rentmanager.modules.tenant.infrastructure.persistence.repository.SubscriptionPaymentRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionPaymentRequestRepositoryAdapter
        implements SubscriptionPaymentRequestRepository {

    private final SubscriptionPaymentRequestJpaRepository jpaRepository;
    private final SubscriptionPaymentRequestPersistenceMapper mapper;

    @Override
    public SubscriptionPaymentRequest save(SubscriptionPaymentRequest request) {
        SubscriptionPaymentRequestJpaEntity saved =
                jpaRepository.save(mapper.toJpaEntity(request));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<SubscriptionPaymentRequest> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<SubscriptionPaymentRequest> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId).map(mapper::toDomain);
    }

    @Override
    public Optional<SubscriptionPaymentRequest> findByMpesaCheckoutRequestId(String checkoutRequestId) {
        return jpaRepository.findByMpesaCheckoutRequestId(checkoutRequestId).map(mapper::toDomain);
    }

    @Override
    public Optional<SubscriptionPaymentRequest> findByMpesaReceiptNumber(String mpesaReceiptNumber) {
        return jpaRepository.findByMpesaReceiptNumber(mpesaReceiptNumber).map(mapper::toDomain);
    }

    @Override
    public Optional<SubscriptionPaymentRequest> findPendingByTenantIdAndPurpose(
            UUID tenantId, SubscriptionPaymentPurpose purpose
    ) {
        return jpaRepository
                .findFirstByTenantIdAndPurposeAndStatusOrderByCreatedAtDesc(
                        tenantId, purpose, SubscriptionPaymentRequestStatus.PENDING
                )
                .map(mapper::toDomain);
    }

    @Override
    public List<SubscriptionPaymentRequest> findByStatusAndCreatedAtBefore(
            SubscriptionPaymentRequestStatus status, Instant cutoff
    ) {
        return jpaRepository.findByStatusAndCreatedAtBefore(status, cutoff)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
