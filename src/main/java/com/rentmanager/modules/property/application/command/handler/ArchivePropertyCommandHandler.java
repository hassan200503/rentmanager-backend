package com.rentmanager.modules.property.application.command.handler;

import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.application.command.usecase.ArchivePropertyUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ArchivePropertyCommandHandler
        implements ArchivePropertyUseCase {

    private final PropertyCommandService propertyCommandService;

    @Override
    public void execute(UUID tenantId,
                        UUID propertyId) {

        propertyCommandService.archiveProperty(
                tenantId,
                propertyId
        );
    }
}