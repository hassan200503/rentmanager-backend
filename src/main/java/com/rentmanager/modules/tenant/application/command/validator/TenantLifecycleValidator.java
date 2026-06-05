package com.rentmanager.modules.tenant.application.command.validator;

import java.util.UUID;

public interface TenantLifecycleValidator {

    void validateActivation(UUID tenantId);

    void validateDeactivation(UUID tenantId);

    void validateSuspension(UUID tenantId);

    void validateRestoration(UUID tenantId);

}