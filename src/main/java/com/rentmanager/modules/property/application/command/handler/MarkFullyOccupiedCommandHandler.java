package com.rentmanager.modules.property.application.command.handler;

import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.application.command.usecase.MarkFullyOccupiedUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MarkFullyOccupiedCommandHandler implements MarkFullyOccupiedUseCase {

    private final PropertyCommandService propertyCommandService;

    @Override
    public void execute(UUID tenantId,
                        UUID propertyId) {

        propertyCommandService.markFullyOccupied(
                tenantId,
                propertyId
        );
    }
}