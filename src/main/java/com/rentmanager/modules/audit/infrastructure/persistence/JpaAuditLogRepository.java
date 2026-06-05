package com.rentmanager.modules.audit.infrastructure.persistence;

import com.rentmanager.modules.audit.domain.model.AuditLog;
import com.rentmanager.modules.audit.domain.repository.AuditLogRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JpaAuditLogRepository implements AuditLogRepository {

    @Override
    public AuditLog save(AuditLog auditLog) {
        // JPA implementation later
        return auditLog;
    }

    @Override
    public List<AuditLog> findByTenantId(UUID tenantId) {
        return List.of();
    }

    @Override
    public List<AuditLog> findByCorrelationId(String correlationId) {
        return List.of();
    }
}