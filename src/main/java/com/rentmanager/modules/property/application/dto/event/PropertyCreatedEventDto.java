package com.rentmanager.modules.property.application.dto.event;

import lombok.Getter;

@Getter
public class PropertyCreatedEventDto extends BasePropertyEventDto {

    private final String name;
    private final String propertyType;

    public PropertyCreatedEventDto(
            java.util.UUID propertyId,
            java.util.UUID tenantId,
            String correlationId,
            String name,
            String propertyType
    ) {
        super(propertyId, tenantId, correlationId);
        this.name = name;
        this.propertyType = propertyType;
    }
}