package com.rentmanager.modules.tenant.application.command.usecase;
import
com.rentmanager.modules.tenant.application.dto.request.SuspendTenantRequest;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import java.util.UUID;
public interface SuspendTenantUseCase {
TenantResponse execute(UUID tenantId, SuspendTenantRequest request);
}