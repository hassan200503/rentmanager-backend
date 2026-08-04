package com.rentmanager.modules.property.application.dto.response;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.domain.valueobject.GeoLocation;
import com.rentmanager.modules.property.domain.valueobject.PropertyDimensions;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class PropertyResponse {

    private UUID propertyId;

    private UUID tenantId;

    private String name;

    private PropertyType propertyType;

    /**
     * RESIDENTIAL/COMMERCIAL/MIXED_USE classification used by the tax module
     * (MRI vs 16% VAT). Always present - derived from {@link PropertyType}
     * when the property was created without an explicit override.
     */
    private PremisesType premisesType;

    /**
     * Free-text justification when the premises classification was explicitly
     * set. NULL for auto-derived rows.
     */
    private String premisesTypeOverrideReason;

    private UUID premisesTypeChangedBy;

    private Instant premisesTypeChangedAt;

    private PropertyStatus status;

    private OccupancyStatus occupancyStatus;

    private Address address;

    private GeoLocation geoLocation;

    private PropertyDimensions dimensions;

    private String description;

    /**
     * Primary property image used for cards/listings.
     */
    private String thumbnailUrl;
}