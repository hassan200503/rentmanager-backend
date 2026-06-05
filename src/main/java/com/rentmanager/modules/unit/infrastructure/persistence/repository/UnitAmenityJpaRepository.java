package com.rentmanager.modules.unit.infrastructure.persistence.repository;

import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitAmenityJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UnitAmenityJpaRepository extends JpaRepository<UnitAmenityJpaEntity, UUID> {

    // =========================
    // TENANT SCOPED QUERIES
    // =========================

    List<UnitAmenityJpaEntity> findByTenantId(UUID tenantId);

    List<UnitAmenityJpaEntity> findByTenantIdAndUnitId(UUID tenantId, UUID unitId);

    boolean existsByTenantIdAndUnitIdAndName(UUID tenantId, UUID unitId, String name);
}