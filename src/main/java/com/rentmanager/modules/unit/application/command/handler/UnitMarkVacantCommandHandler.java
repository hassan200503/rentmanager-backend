package com.rentmanager.modules.unit.application.command.handler;

import com.rentmanager.modules.unit.application.command.usecase.MarkVacantUseCase;
import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UnitMarkVacantCommandHandler implements MarkVacantUseCase {

    private final UnitCommandService unitCommandService;

    @Override
    public void execute(UUID tenantId, UUID unitId, String correlationId) {
        unitCommandService.markVacant(tenantId, unitId, correlationId);
    }
}