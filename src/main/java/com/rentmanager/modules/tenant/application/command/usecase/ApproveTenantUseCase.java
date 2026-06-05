package com.rentmanager.modules.tenant.application.command.usecase;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import java.util.UUID;
public interface ApproveTenantUseCase {
TenantResponse execute(UUID tenantId);
}