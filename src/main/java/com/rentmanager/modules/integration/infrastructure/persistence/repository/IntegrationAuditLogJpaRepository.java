package com.rentmanager.modules.integration.infrastructure.persistence.repository;

import com.rentmanager.modules.integration.infrastructure.persistence.entity.IntegrationAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IntegrationAuditLogJpaRepository extends JpaRepository<IntegrationAuditLogEntity, UUID> {

    List<IntegrationAuditLogEntity> findTop50ByProviderKeyOrderByCreatedAtDesc(String providerKey);
}