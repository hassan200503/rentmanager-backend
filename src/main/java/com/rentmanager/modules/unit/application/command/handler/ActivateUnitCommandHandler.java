package com.rentmanager.modules.unit.application.command.handler;

import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.command.usecase.ActivateUnitUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActivateUnitCommandHandler implements ActivateUnitUseCase {

    private final UnitCommandService unitCommandService;

    @Override
    public void execute(UUID tenantId, UUID unitId, String correlationId) {

        unitCommandService.activate(tenantId, unitId, correlationId);
    }
}