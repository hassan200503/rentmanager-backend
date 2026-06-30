package com.rentmanager.modules.tenant.domain.service;


import com.rentmanager.modules.tenant.domain.model.Tenant;

public interface TenantDomainService {

    String generateTenantCode(String organizationName);

    String generateSlug(String organizationName);

    void validateTenantActivation(Tenant tenant);

    void validateTenantDeactivation(Tenant tenant);
}