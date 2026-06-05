package com.rentmanager.modules.tenant.application.command.validator;

import com.rentmanager.modules.tenant.application.dto.request.UpdateTenantBrandingRequest;

import java.util.UUID;

public interface TenantBrandingValidator {

    void validate(UUID tenantId, UpdateTenantBrandingRequest request);

}