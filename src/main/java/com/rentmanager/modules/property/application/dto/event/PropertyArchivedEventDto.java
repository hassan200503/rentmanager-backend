package com.rentmanager.modules.property.application.dto.event;

import lombok.Getter;

@Getter
public class PropertyArchivedEventDto extends BasePropertyEventDto {

    private final String reason;

    public PropertyArchivedEventDto(
            java.util.UUID propertyId,
            java.util.UUID tenantId,
            String correlationId,
            String reason
    ) {
        super(propertyId, tenantId, correlationId);
        this.reason = reason;
    }
}