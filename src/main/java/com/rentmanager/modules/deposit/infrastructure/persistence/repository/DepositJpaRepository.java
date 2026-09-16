package com.rentmanager.modules.deposit.infrastructure.persistence.repository;

import com.rentmanager.modules.deposit.domain.enums.DepositStatus;
import com.rentmanager.modules.deposit.infrastructure.persistence.entity.DepositJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepositJpaRepository extends JpaRepository<DepositJpaEntity, UUID> {

    Optional<DepositJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DepositJpaEntity> findByLeaseId(UUID leaseId);

    Optional<DepositJpaEntity> findByLeaseIdAndTenantId(UUID leaseId, UUID tenantId);

    List<DepositJpaEntity> findByTenantProfileId(UUID tenantProfileId);

    Optional<DepositJpaEntity> findByPendingRefundCheckoutRequestId(String pendingRefundCheckoutRequestId);

    List<DepositJpaEntity> findByTenantIdAndStatusOrderByPaidAtDesc(UUID tenantId, DepositStatus status);

    List<DepositJpaEntity> findByTenantIdOrderByPaidAtDesc(UUID tenantId);
}