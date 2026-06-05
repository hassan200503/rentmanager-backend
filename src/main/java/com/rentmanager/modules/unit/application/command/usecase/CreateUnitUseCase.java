package com.rentmanager.modules.unit.application.command.usecase;

import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;

import java.util.UUID;

public interface CreateUnitUseCase {

    UnitResponse execute(UUID tenantId, CreateUnitRequest request);
}