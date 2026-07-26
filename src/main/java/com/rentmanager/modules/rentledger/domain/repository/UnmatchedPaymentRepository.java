package com.rentmanager.modules.rentledger.domain.repository;

import com.rentmanager.modules.rentledger.domain.model.UnmatchedPayment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnmatchedPaymentRepository {
    UnmatchedPayment save(UnmatchedPayment payment);
    Optional<UnmatchedPayment> findById(UUID id);
    Optional<UnmatchedPayment> findByIdAndTenantId(UUID id, UUID tenantId);
    List<UnmatchedPayment> findByTenantIdAndResolvedFalse(UUID tenantId);
    List<UnmatchedPayment> findAllByTenant(UUID tenantId);
}
