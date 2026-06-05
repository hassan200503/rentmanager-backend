package com.rentmanager.modules.tenant.application.command.usecase;

import java.util.UUID;
public interface ArchiveTenantUseCase {
void execute(UUID tenantId);
}