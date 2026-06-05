package com.rentmanager.modules.property.domain.model;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.property.domain.enums.AmenityType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Pure Domain Model (NO JPA annotations).
 * Represents a Property Amenity in the domain layer.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PropertyAmenity extends BaseTenantEntity {

    private Property property;

    private AmenityType amenityType;

    /**
     * Factory method for controlled creation (DDD style).
     */
    public static PropertyAmenity create(
            UUID tenantId,
            Property property,
            AmenityType amenityType
    ) {
        PropertyAmenity amenity = PropertyAmenity.builder()
                .property(property)
                .amenityType(amenityType)
                .build();

        amenity.assignTenant(tenantId);
        return amenity;
    }
}