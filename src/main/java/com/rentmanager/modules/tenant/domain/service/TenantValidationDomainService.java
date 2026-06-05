package com.rentmanager.modules.tenant.domain.service;

import com.rentmanager.modules.tenant.domain.model.Tenant;

public interface TenantValidationDomainService {

    void validateUniqueTenantCode(String tenantCode);

    void validateUniqueSlug(String slug);

    void validateTenantEmail(String email);

    void validateTenantState(Tenant tenant);

    void validateOnboardingCompletion(Tenant tenant);
}