package com.rentmanager.modules.rentledger.infrastructure.persistence.autopay.repository;

import com.rentmanager.modules.rentledger.infrastructure.persistence.autopay.entity.AutoPaySettingsJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutoPaySettingsJpaRepository extends JpaRepository<AutoPaySettingsJpaEntity, UUID> {

    Optional<AutoPaySettingsJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<AutoPaySettingsJpaEntity> findByLeaseIdAndTenantId(UUID leaseId, UUID tenantId);

    List<AutoPaySettingsJpaEntity> findAllByEnabledTrue();

    List<AutoPaySettingsJpaEntity> findAllByTenantId(UUID tenantId);
}