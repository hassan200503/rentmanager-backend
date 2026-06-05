package com.rentmanager.modules.property.application.command.handler;

import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.application.command.usecase.UpdatePropertyUseCase;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UpdatePropertyCommandHandler
        implements UpdatePropertyUseCase {

    private final PropertyCommandService propertyCommandService;

    @Override
    public PropertyResponse execute(UUID tenantId,
                                    UUID propertyId,
                                    UpdatePropertyRequest request) {

        return propertyCommandService.updateProperty(
                tenantId,
                propertyId,
                request
        );
    }
}