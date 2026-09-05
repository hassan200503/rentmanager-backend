package com.rentmanager.modules.audit.infrastructure.persistence;

import com.rentmanager.modules.audit.domain.model.AuditLog;
import com.rentmanager.modules.audit.domain.repository.AuditLogRepository;
import com.rentmanager.modules.audit.infrastructure.persistence.entity.AuditLogJpaEntity;
import com.rentmanager.modules.audit.infrastructure.persistence.repository.AuditLogJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Persists audit records.
 *
 * <p>This class previously read, in full:
 *
 * <pre>
 *     public AuditLog save(AuditLog auditLog) {
 *         // JPA implementation later
 *         return auditLog;
 *     }
 * </pre>
 *
 * <p>It returned the object and wrote nothing, and the finders returned empty
 * lists. Every audit call the system has ever made succeeded and recorded
 * nothing — the failure mode being invisible precisely because the method
 * signature promised a save and the caller had no way to know otherwise.
 */
@Repository
@RequiredArgsConstructor
public class JpaAuditLogRepository implements AuditLogRepository {

    private final AuditLogJpaRepository jpaRepository;

    @Override
    public AuditLog save(AuditLog auditLog) {
        return toDomain(jpaRepository.save(toEntity(auditLog)));
    }

    @Override
    public List<AuditLog> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(JpaAuditLogRepository::toDomain)
                .toList();
    }

    @Override
    public List<AuditLog> findByCorrelationId(String correlationId) {
        return jpaRepository.findByCorrelationIdOrderByCreatedAtAsc(correlationId).stream()
                .map(JpaAuditLogRepository::toDomain)
                .toList();
    }

    private static AuditLogJpaEntity toEntity(AuditLog log) {
        AuditLogJpaEntity entity = new AuditLogJpaEntity();
        entity.setId(log.getId());
        entity.setTenantId(log.getTenantId());
        entity.setAction(log.getAction());
        entity.setActorId(log.getActorId());
        entity.setActorType(log.getActorType());
        entity.setEntityType(log.getEntityType());
        entity.setEntityId(log.getEntityId());
        entity.setCorrelationId(log.getCorrelationId());
        entity.setStatus(log.getStatus());
        entity.setMetadata(log.getMetadata());
        entity.setIpAddress(log.getIpAddress());
        entity.setUserAgent(log.getUserAgent());
        entity.setCreatedAt(log.getCreatedAt());
        return entity;
    }

    private static AuditLog toDomain(AuditLogJpaEntity e) {
        return AuditLog.rehydrate(
                e.getId(), e.getTenantId(), e.getAction(), e.getActorId(), e.getActorType(),
                e.getEntityType(), e.getEntityId(), e.getCorrelationId(), e.getStatus(),
                e.getMetadata(), e.getIpAddress(), e.getUserAgent(), e.getCreatedAt());
    }
}
