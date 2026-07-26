package com.rentmanager.modules.rentledger.infrastructure.persistence.adapter;

import com.rentmanager.modules.rentledger.domain.model.UnmatchedPayment;
import com.rentmanager.modules.rentledger.domain.repository.UnmatchedPaymentRepository;
import com.rentmanager.modules.rentledger.infrastructure.persistence.mapper.UnmatchedPaymentPersistenceMapper;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.UnmatchedPaymentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UnmatchedPaymentRepositoryAdapter implements UnmatchedPaymentRepository {

    private final UnmatchedPaymentJpaRepository jpaRepository;
    private final UnmatchedPaymentPersistenceMapper mapper;

    @Override
    public UnmatchedPayment save(UnmatchedPayment payment) {
        var saved = jpaRepository.save(mapper.toJpaEntity(payment));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<UnmatchedPayment> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<UnmatchedPayment> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId).map(mapper::toDomain);
    }

    @Override
    public List<UnmatchedPayment> findByTenantIdAndResolvedFalse(UUID tenantId) {
        return jpaRepository.findByTenantIdAndResolvedFalseOrderByOccurredAtDesc(tenantId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<UnmatchedPayment> findAllByTenant(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByOccurredAtDesc(tenantId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
