package com.rentmanager.modules.property.application.command.usecase;

import java.util.UUID;

public interface MarkFullyOccupiedUseCase {

    void execute(UUID tenantId,
                 UUID propertyId);
}