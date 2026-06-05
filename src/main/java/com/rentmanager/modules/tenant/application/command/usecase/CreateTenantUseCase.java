package com.rentmanager.modules.tenant.application.command.usecase;
import
com.rentmanager.modules.tenant.application.dto.request.CreateTenantRequest;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
public interface CreateTenantUseCase {
    TenantResponse execute(CreateTenantRequest request);
}