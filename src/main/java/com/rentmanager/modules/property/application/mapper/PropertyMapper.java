package com.rentmanager.modules.property.application.mapper;

import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import com.rentmanager.modules.property.domain.model.Property;
import org.springframework.stereotype.Component;

@Component
public class PropertyMapper {

    public PropertyResponse toResponse(Property property) {

        return PropertyResponse.builder()
                .propertyId(property.getId())
                .tenantId(property.getTenantId())
                .name(property.getName())
                .propertyType(property.getPropertyType())
                .status(property.getStatus())
                .occupancyStatus(property.getOccupancyStatus())
                .address(property.getAddress())
                .geoLocation(property.getGeoLocation())
                .dimensions(property.getDimensions())
                .description(property.getDescription())
                .build();
    }




    public PublicPropertyResponse toPublicResponse(Property property) {
        return PublicPropertyResponse.builder()
                .propertyId(property.getId())
                .name(property.getName())
                .propertyType(property.getPropertyType())
                .address(property.getAddress())
                .geoLocation(property.getGeoLocation())
                .description(property.getDescription())
                .build();
    }
}