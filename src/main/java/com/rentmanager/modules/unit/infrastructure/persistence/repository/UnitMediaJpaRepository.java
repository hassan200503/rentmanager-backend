package com.rentmanager.modules.unit.infrastructure.persistence.repository;

import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitMediaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitMediaJpaRepository extends JpaRepository<UnitMediaJpaEntity, UUID> {

    List<UnitMediaJpaEntity> findByTenantId(UUID tenantId);

    List<UnitMediaJpaEntity> findByTenantIdAndUnitId(UUID tenantId, UUID unitId);

    Optional<UnitMediaJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<UnitMediaJpaEntity> findByTenantIdAndUnitIdAndPrimaryMediaTrue(UUID tenantId, UUID unitId);

    boolean existsByTenantIdAndUnitIdAndUrl(UUID tenantId, UUID unitId, String url);

    List<UnitMediaJpaEntity> findAllByUnitIdIn(List<UUID> unitIds);

    List<UnitMediaJpaEntity> findAllByUnitId(UUID unitId);

    @Modifying
    @Query("UPDATE UnitMediaJpaEntity u SET u.primaryMedia = false WHERE u.tenantId = :tenantId AND u.unitId = :unitId")
    void clearPrimaryForUnit(@Param("tenantId") UUID tenantId, @Param("unitId") UUID unitId);

    @Modifying
    @Query("UPDATE UnitMediaJpaEntity u SET u.primaryMedia = true WHERE u.id = :id AND u.tenantId = :tenantId")
    void setPrimaryById(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}