package com.rentmanager.modules.unit.infrastructure.persistence.repository;

import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitMediaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UnitMediaJpaRepository extends JpaRepository<UnitMediaJpaEntity, UUID> {

    List<UnitMediaJpaEntity> findByTenantId(UUID tenantId);

    List<UnitMediaJpaEntity> findByTenantIdAndUnitId(UUID tenantId, UUID unitId);

    boolean existsByTenantIdAndUnitIdAndUrl(UUID tenantId, UUID unitId, String url);
}