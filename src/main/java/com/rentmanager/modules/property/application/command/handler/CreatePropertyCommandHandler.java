package com.rentmanager.modules.property.application.command.handler;

import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.application.command.usecase.CreatePropertyUseCase;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreatePropertyCommandHandler
        implements CreatePropertyUseCase {

    private final PropertyCommandService propertyCommandService;

    @Override
    public PropertyResponse execute(UUID tenantId,
                                    CreatePropertyRequest request) {

        return propertyCommandService.createProperty(
                tenantId,
                null,
                request
        );
    }
}