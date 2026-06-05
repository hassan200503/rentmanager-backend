package com.rentmanager.modules.tenant.application.command.validator;

import com.rentmanager.modules.tenant.domain.enums.TenantStatus;

import java.util.UUID;

public interface TenantStatusValidator {

    void validateStatusTransition(UUID tenantId, TenantStatus newStatus);

}