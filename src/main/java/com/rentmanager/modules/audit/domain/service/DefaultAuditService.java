package com.rentmanager.modules.audit.domain.service;

import com.rentmanager.modules.audit.domain.model.AuditLog;
import com.rentmanager.modules.audit.domain.repository.AuditLogRepository;
import com.rentmanager.modules.audit.domain.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation for audit logging operations.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultAuditService implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public void record(AuditLog auditLog) {

        if (auditLog == null) {
            throw new IllegalArgumentException("Audit log must not be null");
        }

        auditLogRepository.save(auditLog);
    }
}