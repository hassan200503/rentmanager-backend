package com.rentmanager.modules.unit.application.command.usecase;

import java.util.UUID;

public interface ArchiveUnitUseCase {

    void execute(UUID tenantId, UUID unitId);
}