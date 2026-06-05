package com.rentmanager.modules.tenant.application.command.validator;

import com.rentmanager.modules.tenant.application.dto.request.UpdateTenantSubscriptionRequest;

import java.util.UUID;

public interface TenantSubscriptionValidator {

    void validate(UUID tenantId, UpdateTenantSubscriptionRequest request);

}