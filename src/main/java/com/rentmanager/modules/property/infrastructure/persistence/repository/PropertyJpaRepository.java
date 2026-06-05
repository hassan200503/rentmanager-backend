package com.rentmanager.modules.property.infrastructure.persistence.repository;

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

    List<PropertyJpaEntity> findByStatus(String status);

    Page<PropertyJpaEntity> searchByNameContainingIgnoreCase(
            String name,
            Pageable pageable
    );





}