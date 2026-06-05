package com.rentmanager.modules.property.application.dto.response;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.domain.valueobject.GeoLocation;
import com.rentmanager.modules.property.domain.valueobject.PropertyDimensions;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PropertyResponse {

    private UUID propertyId;

    private UUID tenantId;

    private String name;

    private PropertyType propertyType;

    private PropertyStatus status;

    private OccupancyStatus occupancyStatus;

    private Address address;

    private GeoLocation geoLocation;

    private PropertyDimensions dimensions;

    private String description;
}