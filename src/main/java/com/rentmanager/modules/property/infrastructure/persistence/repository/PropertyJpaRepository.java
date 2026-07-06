package com.rentmanager.modules.property.infrastructure.persistence.repository;

import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyJpaRepository
        extends JpaRepository<PropertyJpaEntity, UUID>,
        JpaSpecificationExecutor<PropertyJpaEntity> {

    Optional<PropertyJpaEntity> findByIdAndTenantId(
            UUID id,
            UUID tenantId
    );

    Optional<PropertyJpaEntity> findByReferenceCodeAndTenantId(
            String referenceCode,
            UUID tenantId
    );

    boolean existsByNameAndTenantId(
            String name,
            UUID tenantId
    );

    // ---------------------------------------
    // FIX 1: required by domain repository
    // ---------------------------------------
    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    // ---------------------------------------
    // FIX 2: case-insensitive SaaS uniqueness
    // ---------------------------------------
    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    // ---------------------------------------
    // FIX 3: pageable version (replace List use)
    // ---------------------------------------
    Page<PropertyJpaEntity> findAllByTenantId(UUID tenantId, Pageable pageable);

    // (optional legacy - keep if still used somewhere)
    List<PropertyJpaEntity> findAllByTenantId(UUID tenantId);



    // ✅ ADD THIS (CRITICAL FIX)
    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<Property> searchByTenantIdAndNameContainingIgnoreCase(
            String tenantId,
            String name,
            Pageable pageable
    );



    Page<PropertyJpaEntity> findByTenantIdAndNameContainingIgnoreCase(
            UUID tenantId,
            String name,
            Pageable pageable
    );


    List<PropertyJpaEntity> findByOwnerId(UUID ownerId);

    // FIX: was String — must match PropertyJpaEntity.status (@Enumerated(EnumType.STRING) PropertyStatus)
    // Hibernate 6.4's stricter parameter binding rejects a String argument against an enum-typed field.
    List<PropertyJpaEntity> findByStatus(PropertyStatus status);

    // ---------------------------------------
    // FIX: tenant-scoped status query
    // Previously missing — adapter was calling findByStatus(status) alone and
    // silently ignoring tenantId, leaking every tenant's properties across the app.
    // ---------------------------------------
    List<PropertyJpaEntity> findByStatusAndTenantId(PropertyStatus status, UUID tenantId);

    // ---------------------------------------
    // FIX: tenant-scoped owner query
    // Previously missing — adapter was calling findByOwnerId(ownerId) alone and
    // silently ignoring tenantId, same cross-tenant leak pattern as above.
    // ---------------------------------------
    List<PropertyJpaEntity> findByOwnerIdAndTenantId(UUID ownerId, UUID tenantId);

    Page<PropertyJpaEntity> searchByNameContainingIgnoreCase(
            String name,
            Pageable pageable
    );

}