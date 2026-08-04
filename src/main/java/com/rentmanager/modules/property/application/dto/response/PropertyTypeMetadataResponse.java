package com.rentmanager.modules.property.application.dto.response;

import com.rentmanager.modules.property.domain.enums.PremisesType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Taxonomy metadata for the property form: every selectable property type
 * with its auto-derived premises classification, plus the full set of
 * premises classifications a user may explicitly pick (MIXED_USE included).
 * Rendered verbatim by the frontend so the derivation rule lives in exactly
 * one place (this backend module).
 */
@Getter
@Builder
public class PropertyTypeMetadataResponse {

    private final List<PropertyTypeDescriptor> propertyTypes;

    private final List<PremisesType> premisesTypes;
}
