package com.rentmanager.modules.property.application.command.usecase;

import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;

import java.util.UUID;

public interface UpdatePropertyUseCase {

    PropertyResponse execute(UUID tenantId,
                             UUID propertyId,
                             UpdatePropertyRequest request);
}