package com.rentmanager.modules.tenant.application.command.usecase;
import
com.rentmanager.modules.tenant.application.dto.request.UpdateTenantSubscriptionRequest;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import java.util.UUID;
public interface UpdateTenantSubscriptionUseCase {
TenantResponse execute(UUID tenantId, UpdateTenantSubscriptionRequest
request);
}
