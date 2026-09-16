package com.rentmanager.modules.property.application.mapper;

import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.model.PropertyMedia;
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
                .map(PropertyMedia::getFileUrl)
                .orElse(null);

        return toResponse(property, thumbnailUrl);
    }

    /**
     * Batched equivalent of {@link #toResponse(Property)} for a page of
     * properties. {@code toResponse} looks up one property's primary media
     * with its own query, which is fine for a single property but is an N+1
     * query when mapping a whole page — this fetches every property's media
     * in one query instead, so the dashboard Properties list issues exactly
     * one extra query no matter the page size.
     */
    public List<PropertyResponse> toResponseList(List<Property> properties) {
        if (properties.isEmpty()) {
            return List.of();
        }

        List<UUID> propertyIds = properties.stream().map(Property::getId).toList();

        Map<UUID, String> thumbnailUrlByPropertyId = propertyMediaRepository
                .findAllByPropertyIdIn(propertyIds)
                .stream()
                .filter(PropertyMedia::isPrimaryMedia)
                .collect(Collectors.toMap(
                        PropertyMedia::getPropertyId,
                        PropertyMedia::getFileUrl,
                        (first, second) -> first
                ));

        return properties.stream()
                .map(property -> toResponse(property, thumbnailUrlByPropertyId.get(property.getId())))
                .toList();
    }

    private PropertyResponse toResponse(Property property, String thumbnailUrl) {
        return PropertyResponse.builder()
                .propertyId(property.getId())
                .tenantId(property.getTenantId())
                .name(property.getName())
                .propertyType(property.getPropertyType())
                .premisesType(property.getPremisesType())
                .premisesTypeOverrideReason(property.getPremisesTypeOverrideReason())
                .premisesTypeChangedBy(property.getPremisesTypeChangedBy())
                .premisesTypeChangedAt(property.getPremisesTypeChangedAt())
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