package com.rentmanager.modules.deposit.domain.repository;

import com.rentmanager.modules.deposit.domain.model.Deposit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepositRepository {

    Deposit save(Deposit deposit);

    Optional<Deposit> findById(UUID id);

    Optional<Deposit> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Deposit> findByLeaseId(UUID leaseId);

    Optional<Deposit> findByLeaseIdAndTenantId(UUID leaseId, UUID tenantId);

    List<Deposit> findByTenantProfileId(UUID tenantProfileId);

    void delete(Deposit deposit);
}