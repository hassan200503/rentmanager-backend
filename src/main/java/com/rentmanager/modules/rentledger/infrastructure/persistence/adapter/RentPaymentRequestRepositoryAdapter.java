package com.rentmanager.modules.rentledger.infrastructure.persistence.adapter;

import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentPaymentRequestJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.mapper.RentPaymentRequestPersistenceMapper;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.RentPaymentRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RentPaymentRequestRepositoryAdapter implements RentPaymentRequestRepository {

    private final RentPaymentRequestJpaRepository jpaRepository;
    private final RentPaymentRequestPersistenceMapper mapper;

    @Override
    public RentPaymentRequest save(RentPaymentRequest request) {
        RentPaymentRequestJpaEntity saved = jpaRepository.save(mapper.toJpaEntity(request));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<RentPaymentRequest> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<RentPaymentRequest> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId).map(mapper::toDomain);
    }

    @Override
    public Optional<RentPaymentRequest> findByMpesaCheckoutRequestId(String checkoutRequestId) {
        return jpaRepository.findByMpesaCheckoutRequestId(checkoutRequestId).map(mapper::toDomain);
    }

    @Override
    public List<RentPaymentRequest> findByStatusAndCreatedAtBefore(RentPaymentRequestStatus status, Instant cutoff) {
        return jpaRepository.findByStatusAndCreatedAtBefore(status, cutoff)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<RentPaymentRequest> findLatestPendingForEntry(UUID tenantId, UUID rentLedgerEntryId) {
        return jpaRepository
                .findFirstByTenantIdAndRentLedgerEntryIdAndStatusOrderByCreatedAtDesc(
                        tenantId, rentLedgerEntryId, RentPaymentRequestStatus.PENDING)
                .map(mapper::toDomain);
    }
}