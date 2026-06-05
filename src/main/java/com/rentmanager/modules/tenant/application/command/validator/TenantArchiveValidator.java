package com.rentmanager.modules.tenant.application.command.validator;

import java.util.UUID;

public interface TenantArchiveValidator {

    void validate(UUID tenantId);

}