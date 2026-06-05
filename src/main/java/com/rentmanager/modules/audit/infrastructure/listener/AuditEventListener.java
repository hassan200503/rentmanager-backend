package com.rentmanager.modules.audit.infrastructure.listener;

import com.rentmanager.domain.base.DomainEvent;

public interface AuditEventListener {

    void onEvent(DomainEvent event);
}