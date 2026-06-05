package com.rentmanager.modules.property.application.query.projection;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * CQRS Read Model (Projection)
 * Optimized for query performance and API responses
 */
@Getter
@Builder
public class PropertyProjection {

    private UUID propertyId;

    private UUID tenantId;

    private String name;

    private PropertyType propertyType;

    private PropertyStatus status;

    private OccupancyStatus occupancyStatus;

    private String city;

    private String country;

    private String description;
}