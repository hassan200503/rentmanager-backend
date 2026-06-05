package com.rentmanager.modules.property.application.dto.event;

import lombok.Getter;

@Getter
public class PropertyActivatedEventDto extends BasePropertyEventDto {

    public PropertyActivatedEventDto(
            java.util.UUID propertyId,
            java.util.UUID tenantId,
            String correlationId
    ) {
        super(propertyId, tenantId, correlationId);
    }
}