package com.rentmanager.modules.tenant.domain.service;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.domain.base.DomainEvent;

import java.util.List;

public interface TenantEventDomainService {

    List<DomainEvent> registerTenantCreated(Tenant tenant);

    List<DomainEvent> registerTenantActivated(Tenant tenant);

    List<DomainEvent> registerTenantSuspended(Tenant tenant, String reason);

    List<DomainEvent> registerTenantDeactivated(Tenant tenant, String reason);

    List<DomainEvent> collectAndClearEvents(Tenant tenant);
}