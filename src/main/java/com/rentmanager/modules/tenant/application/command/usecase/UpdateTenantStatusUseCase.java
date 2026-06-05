package com.rentmanager.modules.tenant.application.command.usecase;
import
com.rentmanager.modules.tenant.application.dto.request.UpdateTenantStatusRequest;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import java.util.UUID;
public interface UpdateTenantStatusUseCase {
TenantResponse execute(UUID tenantId, UpdateTenantStatusRequest request);
}
