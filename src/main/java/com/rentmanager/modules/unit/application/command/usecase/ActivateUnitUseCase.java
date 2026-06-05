package com.rentmanager.modules.unit.application.command.usecase;

import java.util.UUID;

public interface ActivateUnitUseCase {

    void execute(UUID tenantId, UUID unitId, String correlationId);
}