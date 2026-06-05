package com.rentmanager.modules.property.application.dto.event;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import lombok.Getter;

@Getter
public class PropertyOccupancyChangedEventDto extends BasePropertyEventDto {

    private final OccupancyStatus previousStatus;
    private final OccupancyStatus newStatus;

    public PropertyOccupancyChangedEventDto(
            java.util.UUID propertyId,
            java.util.UUID tenantId,
            String correlationId,
            OccupancyStatus previousStatus,
            OccupancyStatus newStatus
    ) {
        super(propertyId, tenantId, correlationId);
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }
}