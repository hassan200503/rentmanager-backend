package com.rentmanager.modules.audit.infrastructure.listener;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.audit.domain.model.AuditLog;
import com.rentmanager.modules.audit.domain.service.AuditService;
import org.springframework.stereotype.Component;

@Component
public class AuditEventListenerImpl implements AuditEventListener {

    private final AuditService auditService;

    public AuditEventListenerImpl(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void onEvent(DomainEvent event) {

        AuditLog log = new AuditLog(
                event.getTenantId(),
                event.eventType(),
                "SYSTEM",
                "DOMAIN_EVENT",
                event.getClass().getSimpleName(),
                event.getEventId().toString(),
                event.getCorrelationId(),
                "SUCCESS",
                null,
                null,
                null
        );

        auditService.record(log);
    }
}