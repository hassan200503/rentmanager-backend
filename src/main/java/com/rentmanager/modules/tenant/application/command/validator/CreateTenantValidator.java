package com.rentmanager.modules.tenant.application.command.validator;

import com.rentmanager.modules.tenant.application.dto.request.CreateTenantRequest;

public interface CreateTenantValidator {

    void validate(CreateTenantRequest request);

}