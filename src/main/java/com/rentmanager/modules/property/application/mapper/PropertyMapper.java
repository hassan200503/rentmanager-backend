package com.rentmanager.modules.property.application.mapper;

import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PropertyMapper {

    private final PropertyMediaRepository propertyMediaRepository;

    public PropertyResponse toResponse(Property property) {

        String thumbnailUrl = propertyMediaRepository
                .findByTenantIdAndPropertyIdAndPrimaryMediaTrue(
                        property.getTenantId(),
                        property.getId()
                )
                .map(media -> media.getFileUrl())
                .orElse(null);

        return PropertyResponse.builder()
                .propertyId(property.getId())
                .tenantId(property.getTenantId())
                .name(property.getName())
                .propertyType(property.getPropertyType())
                .premisesType(property.getPremisesType())
                .status(property.getStatus())
                .occupancyStatus(property.getOccupancyStatus())
                .address(property.getAddress())
                .geoLocation(property.getGeoLocation())
                .dimensions(property.getDimensions())
                .description(property.getDescription())
                .thumbnailUrl(thumbnailUrl)
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