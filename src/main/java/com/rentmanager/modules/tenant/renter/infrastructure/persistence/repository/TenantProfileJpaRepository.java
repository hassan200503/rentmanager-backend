package com.rentmanager.modules.tenant.renter.infrastructure.persistence.repository;

import com.rentmanager.modules.tenant.renter.infrastructure.persistence.entity.TenantProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantProfileJpaRepository extends JpaRepository<TenantProfileEntity, UUID> {

    Optional<TenantProfileEntity> findByTenantIdAndClerkUserId(UUID tenantId, String clerkUserId);

    Optional<TenantProfileEntity> findByClerkUserId(String clerkUserId);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
    boolean existsByClerkUserId(String clerkUserId);

    /**
     * Every Clerk user that holds a renter identity (distinct). Used by the
     * platform-admin identity snapshot to classify renters. Renter profiles
     * are the ONLY renter-side identity rows in the system, so this set is
     * the authoritative renter membership signal.
     */
    @Query("select distinct t.clerkUserId from TenantProfileEntity t")
    List<String> findAllClerkUserIds();

}