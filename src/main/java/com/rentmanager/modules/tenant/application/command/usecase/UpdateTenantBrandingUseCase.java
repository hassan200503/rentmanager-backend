package com.rentmanager.modules.tenant.application.command.usecase;

import
com.rentmanager.modules.tenant.application.dto.request.UpdateTenantBrandingRequest;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import java.util.UUID;
public interface UpdateTenantBrandingUseCase {
TenantResponse execute(UUID tenantId, UpdateTenantBrandingRequest request);
}
