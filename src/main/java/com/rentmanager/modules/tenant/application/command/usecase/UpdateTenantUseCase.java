package com.rentmanager.modules.tenant.application.command.usecase;
import
com.rentmanager.modules.tenant.application.dto.request.UpdateTenantRequest;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import java.util.UUID;
public interface UpdateTenantUseCase {
TenantResponse execute(UUID tenantId, UpdateTenantRequest request);
}