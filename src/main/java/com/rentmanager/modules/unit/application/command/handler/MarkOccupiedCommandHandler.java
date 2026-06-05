package com.rentmanager.modules.unit.application.command.handler;

import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.command.usecase.MarkOccupiedUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MarkOccupiedCommandHandler implements MarkOccupiedUseCase {

    private final UnitCommandService unitCommandService;

    @Override
    public void execute(UUID tenantId, UUID unitId, String correlationId) {
        unitCommandService.markOccupied(tenantId, unitId, correlationId);
    }
}