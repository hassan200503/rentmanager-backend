package com.rentmanager.modules.tenant.application.command.usecase;
import
com.rentmanager.modules.tenant.application.dto.request.RejectTenantRequest;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import java.util.UUID;
public interface RejectTenantUseCase {
TenantResponse execute(UUID tenantId, RejectTenantRequest request);
}