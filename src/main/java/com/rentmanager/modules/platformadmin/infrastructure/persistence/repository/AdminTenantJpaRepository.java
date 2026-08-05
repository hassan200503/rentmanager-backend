package com.rentmanager.modules.platformadmin.infrastructure.persistence.repository;

import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Unscoped (no tenant filter) Spring Data repository over {@code tenants},
 * used exclusively by the platform-admin read model. Landlord-facing code
 * must NOT use this repository — it deliberately bypasses the tenant
 * isolation that the {@code com.rentmanager.modules.tenant} adapter layer
 * enforces for tenant-authenticated callers.
 */
public interface AdminTenantJpaRepository extends JpaRepository<TenantEntity, UUID> {

    /**
     * Searches landlords by name, slug, or email (case-insensitive substring).
     * A blank term matches all rows; null leaves are simply not matched by the
     * literal clause, so callers searching with a blank value should use
     * {@link #findAll(Pageable)} instead.
     */
    Page<TenantEntity> findByNameContainingIgnoreCaseOrSlugContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String slug, String email, Pageable pageable);
}