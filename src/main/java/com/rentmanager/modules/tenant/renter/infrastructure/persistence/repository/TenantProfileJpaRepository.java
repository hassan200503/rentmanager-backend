package com.rentmanager.modules.tenant.renter.infrastructure.persistence.repository;

import com.rentmanager.modules.tenant.renter.infrastructure.persistence.entity.TenantProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantProfileJpaRepository extends JpaRepository<TenantProfileEntity, UUID> {

    Optional<TenantProfileEntity> findByTenantIdAndClerkUserId(UUID tenantId, String clerkUserId);

    Optional<TenantProfileEntity> findByClerkUserId(String clerkUserId);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
    boolean existsByClerkUserId(String clerkUserId);


}