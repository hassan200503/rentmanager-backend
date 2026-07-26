package com.rentmanager.modules.rentledger.infrastructure.persistence.repository;

import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.UnmatchedPaymentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnmatchedPaymentJpaRepository extends JpaRepository<UnmatchedPaymentJpaEntity, UUID> {
    Optional<UnmatchedPaymentJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<UnmatchedPaymentJpaEntity> findByTenantIdAndResolvedFalseOrderByOccurredAtDesc(UUID tenantId);
    List<UnmatchedPaymentJpaEntity> findByTenantIdOrderByOccurredAtDesc(UUID tenantId);
}
