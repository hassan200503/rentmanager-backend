package com.rentmanager.modules.tax.infrastructure.persistence.adapter;

import com.rentmanager.modules.tax.domain.enums.TaxInvoiceStatus;
import com.rentmanager.modules.tax.domain.model.TaxInvoice;
import com.rentmanager.modules.tax.domain.repository.TaxInvoiceRepository;
import com.rentmanager.modules.tax.infrastructure.persistence.mapper.TaxInvoicePersistenceMapper;
import com.rentmanager.modules.tax.infrastructure.persistence.repository.TaxInvoiceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TaxInvoiceRepositoryAdapter implements TaxInvoiceRepository {

    private final TaxInvoiceJpaRepository jpaRepository;
    private final TaxInvoicePersistenceMapper mapper;

    @Override
    public Optional<TaxInvoice> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<TaxInvoice> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByTenantIdAndRentTransactionId(UUID tenantId, UUID rentTransactionId) {
        return jpaRepository.existsByTenantIdAndRentTransactionId(tenantId, rentTransactionId);
    }

    @Override
    public List<TaxInvoice> findDue(LocalDateTime now, int maxAttempts, int limit) {
        return jpaRepository.findDue(
                        List.of(TaxInvoiceStatus.PENDING, TaxInvoiceStatus.FAILED),
                        maxAttempts,
                        now,
                        PageRequest.of(0, limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<TaxInvoice> findAllByTenantId(UUID tenantId, int page, int size) {
        return jpaRepository.findAllByTenantIdOrderByOccurredAtDesc(
                        tenantId,
                        PageRequest.of(page, size))
                .getContent()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countAttentionRequiredByTenantId(UUID tenantId) {
        return jpaRepository.countByTenantIdAndStatusIn(
                tenantId,
                List.of(TaxInvoiceStatus.PENDING, TaxInvoiceStatus.FAILED));
    }

    @Override
    public TaxInvoice save(TaxInvoice invoice) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpaEntity(invoice)));
    }
}