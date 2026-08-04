package com.rentmanager.modules.property.application.dto.request;

import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.domain.valueobject.GeoLocation;
import com.rentmanager.modules.property.domain.valueobject.PropertyDimensions;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePropertyRequest {

    private String name;

    private PropertyType propertyType;

    /**
     * Optional RESIDENTIAL/COMMERCIAL/MIXED_USE override. When omitted, the
     * property is classified from {@link PropertyType} (COMMERCIAL/OFFICE/
     * WAREHOUSE -> COMMERCIAL, everything else -> RESIDENTIAL). MIXED_USE is
     * only reachable through this override.
     */
    private PremisesType premisesType;

    /**
     * Mandatory (non-blank, max 500 chars) whenever {@code premisesType} is
     * provided. Recorded in the audit trail along with the authenticated user
     * id, because the classification drives the MRI/VAT pipeline.
     */
    private String premisesTypeOverrideReason;

    private String description;

    private Address address;

    private GeoLocation geoLocation;

    private PropertyDimensions dimensions;
}