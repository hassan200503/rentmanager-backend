package com.rentmanager.modules.unit.application.command.usecase;

import com.rentmanager.modules.unit.application.dto.request.UpdateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;

import java.util.UUID;

public interface UpdateUnitUseCase {

    UnitResponse execute(UUID tenantId, UUID unitId, UpdateUnitRequest request);
}