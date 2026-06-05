package com.rentmanager.modules.unit.application.command.handler;

import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.command.usecase.ArchiveUnitUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ArchiveUnitCommandHandler implements ArchiveUnitUseCase {

    private final UnitCommandService unitCommandService;

    @Override
    public void execute(UUID tenantId, UUID unitId) {

        unitCommandService.archive(tenantId, unitId);
    }
}