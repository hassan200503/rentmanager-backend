package com.rentmanager.modules.audit.infrastructure.persistence.repository;

import com.rentmanager.modules.audit.infrastructure.persistence.entity.AuditLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, UUID> {

    List<AuditLogJpaEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<AuditLogJpaEntity> findByCorrelationIdOrderByCreatedAtAsc(String correlationId);
}
