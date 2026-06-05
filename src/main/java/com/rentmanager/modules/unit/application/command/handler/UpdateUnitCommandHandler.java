package com.rentmanager.modules.unit.application.command.handler;

import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.command.usecase.UpdateUnitUseCase;
import com.rentmanager.modules.unit.application.dto.request.UpdateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UpdateUnitCommandHandler implements UpdateUnitUseCase {

    private final UnitCommandService unitCommandService;

    @Override
    public UnitResponse execute(UUID tenantId, UUID unitId, UpdateUnitRequest request) {

        return unitCommandService.update(tenantId, unitId, request);
    }
}