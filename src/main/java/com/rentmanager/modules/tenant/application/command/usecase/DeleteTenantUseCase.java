package com.rentmanager.modules.tenant.application.command.usecase;
import java.util.UUID;
public interface DeleteTenantUseCase {
void execute(UUID tenantId);
}
