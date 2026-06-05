package com.rentmanager.modules.tenant.application.command.validator;

import com.rentmanager.modules.tenant.application.dto.request.UpdateTenantRequest;

import java.util.UUID;

public interface UpdateTenantValidator {

    void validate(UUID tenantId, UpdateTenantRequest request);

}