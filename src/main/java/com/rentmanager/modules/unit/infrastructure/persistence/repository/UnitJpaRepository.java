package com.rentmanager.modules.unit.infrastructure.persistence.repository;

import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitJpaRepository extends JpaRepository<UnitJpaEntity, UUID> {

    // =====================================================
    // FIND BY ID + TENANT (STRICT ISOLATION)
    // =====================================================
    Optional<UnitJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    // =====================================================
    // FIND ALL BY TENANT
    // =====================================================
    Page<UnitJpaEntity> findAllByTenantId(UUID tenantId, Pageable pageable);

    // =====================================================
    // EXISTS CHECK
    // =====================================================
    boolean existsByTenantIdAndUnitNumber(UUID tenantId, String unitNumber);

    // =====================================================
    // FIND BY PROPERTY (TENANT SCOPED)
    // =====================================================
    Page<UnitJpaEntity> findByTenantIdAndPropertyId(UUID tenantId, UUID propertyId, Pageable pageable);

    // =====================================================
    // FIND BY STATUS (TENANT SCOPED)
    // =====================================================
    Page<UnitJpaEntity> findByTenantIdAndStatus(UUID tenantId, UnitStatus status, Pageable pageable);

    // =====================================================
    // SEARCH (CUSTOM QUERY)
    // =====================================================
    @Query("""
        SELECT u FROM UnitJpaEntity u
        WHERE u.tenantId = :tenantId
        AND (
            LOWER(u.unitNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(u.label) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(u.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
    """)
    Page<UnitJpaEntity> search(UUID tenantId, String keyword, Pageable pageable);
}