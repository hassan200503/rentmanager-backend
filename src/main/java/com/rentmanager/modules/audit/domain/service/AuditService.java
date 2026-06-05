package com.rentmanager.modules.audit.domain.service;

import com.rentmanager.modules.audit.domain.model.AuditLog;

public interface AuditService {

    void record(AuditLog auditLog);
}