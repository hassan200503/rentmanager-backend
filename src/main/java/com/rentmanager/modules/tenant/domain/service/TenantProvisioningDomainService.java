package com.rentmanager.modules.tenant.domain.service;



import com.rentmanager.modules.tenant.domain.model.Organization;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.model.TenantSettings;


public interface TenantProvisioningDomainService {

    Tenant buildNewTenant(String name, String email, String phoneNumber);

    Organization buildDefaultOrganization(Tenant tenant);

    TenantSettings buildDefaultSettings(Tenant tenant);

    void validateProvisioning(Tenant tenant);
}