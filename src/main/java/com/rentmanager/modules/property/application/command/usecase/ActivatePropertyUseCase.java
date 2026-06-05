package com.rentmanager.modules.property.application.command.usecase;

import java.util.UUID;

public interface ActivatePropertyUseCase {

    void execute(UUID tenantId,
                 UUID propertyId);

}