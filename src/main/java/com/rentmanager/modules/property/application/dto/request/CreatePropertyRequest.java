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
     * Optional RESIDENTIAL/COMMERCIAL override. When omitted, the property is
     * classified from {@link PropertyType} (COMMERCIAL/OFFICE/WAREHOUSE ->
     * COMMERCIAL, everything else -> RESIDENTIAL).
     */
    private PremisesType premisesType;

    private String description;

    private Address address;

    private GeoLocation geoLocation;

    private PropertyDimensions dimensions;
}