package com.rentmanager.modules.property.application.dto.response;

import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyType;

/**
 * Single source of truth for the physical-type -> legal-classification
 * derivation, served to clients so the frontend never hard-codes the rule
 * again. {@code derivedPremisesType} is what the backend would classify for
 * the property type with no override; it is never MIXED_USE (that value is
 * only reachable via an explicit, audited override).
 */
public record PropertyTypeDescriptor(
        PropertyType propertyType,
        PremisesType derivedPremisesType
) {
}
