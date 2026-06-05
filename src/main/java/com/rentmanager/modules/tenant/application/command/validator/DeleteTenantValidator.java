package com.rentmanager.modules.tenant.application.command.validator;

import java.util.UUID;

public interface DeleteTenantValidator {

    void validate(UUID tenantId);

}