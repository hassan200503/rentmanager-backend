package com.rentmanager.modules.tenant.application.command.usecase;
import
com.rentmanager.modules.tenant.application.dto.request.ChangeTenantOwnerRequest;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import java.util.UUID;
public interface ChangeTenantOwnerUseCase {

TenantResponse execute(UUID tenantId, ChangeTenantOwnerRequest request);
}