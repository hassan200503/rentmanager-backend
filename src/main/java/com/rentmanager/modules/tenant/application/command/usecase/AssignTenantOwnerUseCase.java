package com.rentmanager.modules.tenant.application.command.usecase;
import
com.rentmanager.modules.tenant.application.dto.request.AssignTenantOwnerRequest;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import java.util.UUID;
public interface AssignTenantOwnerUseCase {
TenantResponse execute(UUID tenantId, AssignTenantOwnerRequest request);
}