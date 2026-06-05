package com.rentmanager.modules.audit.application.service;

import com.rentmanager.modules.audit.domain.model.AuditLog;
import com.rentmanager.modules.audit.domain.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditApplicationService {

    private final AuditService auditService;

    public void log(AuditLog auditLog) {
        auditService.record(auditLog);
    }
}