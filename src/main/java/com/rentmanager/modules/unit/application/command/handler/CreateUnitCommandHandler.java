package com.rentmanager.modules.unit.application.command.handler;

import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.command.usecase.CreateUnitUseCase;
import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreateUnitCommandHandler implements CreateUnitUseCase {

    private final UnitCommandService unitCommandService;

    @Override
    public UnitResponse execute(UUID tenantId, CreateUnitRequest request) {

        return unitCommandService.create(tenantId, request);
    }
}