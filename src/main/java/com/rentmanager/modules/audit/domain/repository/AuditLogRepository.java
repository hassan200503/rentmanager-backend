package com.rentmanager.modules.audit.domain.repository;

import com.rentmanager.modules.audit.domain.model.AuditLog;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository {

    AuditLog save(AuditLog auditLog);

    List<AuditLog> findByTenantId(UUID tenantId);

    List<AuditLog> findByCorrelationId(String correlationId);
}